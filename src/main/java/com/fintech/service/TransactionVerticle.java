package com.fintech.service;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.core.eventbus.Message;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Tuple;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.reactivex.rxjava3.core.Single;
import java.util.UUID;

public class TransactionVerticle extends AbstractVerticle {

    private MySQLPool dbClient;

    @Override
    public io.reactivex.rxjava3.core.Completable rxStart() {
        MySQLConnectOptions connectOptions = new MySQLConnectOptions()
                .setPort(3306)
                .setHost("localhost")
                .setDatabase("payment")
                .setUser("root")
                .setPassword("Sumit@2006");

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

        dbClient = MySQLPool.pool(vertx, connectOptions, poolOptions);

        vertx.eventBus().consumer("service.transaction.process", this::processTransaction);
        return io.reactivex.rxjava3.core.Completable.complete();
    }

    private void processTransaction(Message<JsonObject> msg) {
        JsonObject req = msg.body();
        String fromId = req.getString("from");
        String toId = req.getString("to");
        Double amountVal = req.getDouble("amount");
        String refId = req.getString("ref_id");

        if (amountVal == null || amountVal <= 0) {
            msg.fail(400, "Amount must be positive");
            return;
        }
        if (refId == null || refId.isEmpty()) {
            msg.fail(400, "Reference ID is required");
            return;
        }

        double amount = amountVal;

        // No need for <Boolean> type witness if you return the correct type (Maybe)
        dbClient.withTransaction(conn -> {
            Single<Boolean> debitStep = Single.just(true);

            // Step A: Debit Sender
            if (fromId != null) {
                String debitSql = "UPDATE accounts SET balance = balance - ?, version = version + 1 WHERE profile_id = ? AND balance >= ?";

                debitStep = conn.preparedQuery(debitSql)
                        .rxExecute(Tuple.of(amount, fromId, amount))
                        .map(rows -> rows.rowCount() > 0)
                        .flatMap(success -> success ? Single.just(true) : Single.error(new RuntimeException("Insufficient Funds")));
            }

            return debitStep.flatMap(ok -> {
                // Step B: Credit Receiver
                if (toId != null) {
                    String creditSql = "UPDATE accounts SET balance = balance + ?, version = version + 1 WHERE profile_id = ?";

                    return conn.preparedQuery(creditSql)
                            .rxExecute(Tuple.of(amount, toId))
                            .map(rows -> true);
                }
                return Single.just(true);
            }).flatMap(ok -> {
                // Step C: Insert Audit Record
                String insertSql = "INSERT INTO transactions (id, sender_account_id, receiver_account_id, amount, status, reference_id, email_sent) VALUES (?, ?, ?, ?, 'COMPLETED', ?, false)";
                return conn.preparedQuery(insertSql)
                        .rxExecute(Tuple.of(UUID.randomUUID().toString(), fromId, toId, amount, refId))
                        .map(res -> true);
            }).toMaybe(); // <--- CRITICAL FIX: Convert Single<Boolean> to Maybe<Boolean>

        }).subscribe(
                result -> msg.reply(new JsonObject().put("status", "SUCCESS").put("ref_id", refId)),
                err -> {
                    System.err.println("Transaction Error: " + err.getMessage());
                    msg.fail(500, err.getMessage());
                }
        );
    }
}