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
        String fromProfileId = req.getString("from");
        String toProfileId = req.getString("to");
        Double amountVal = req.getDouble("amount");
        String refId = req.getString("ref_id");

        if (amountVal == null || amountVal <= 0) {
            msg.fail(400, "Amount must be positive");
            return;
        }

        double amount = amountVal;

        dbClient.withTransaction(conn -> {

            // 1. RESOLVE: Translate Profile IDs -> Account IDs
            Single<String> resolveSender = fromProfileId == null ? Single.just("SYSTEM") :
                    conn.preparedQuery("SELECT id FROM accounts WHERE profile_id = ?")
                            .rxExecute(Tuple.of(fromProfileId))
                            .map(rows -> rows.size() > 0 ? rows.iterator().next().getString("id") : "NOT_FOUND");

            Single<String> resolveReceiver = toProfileId == null ? Single.just("SYSTEM") :
                    conn.preparedQuery("SELECT id FROM accounts WHERE profile_id = ?")
                            .rxExecute(Tuple.of(toProfileId))
                            .map(rows -> rows.size() > 0 ? rows.iterator().next().getString("id") : "NOT_FOUND");

            // 2. EXECUTE: Use the Resolved Account IDs for the Logic
            return Single.zip(resolveSender, resolveReceiver, (senderAccId, receiverAccId) -> {
                if ("NOT_FOUND".equals(senderAccId)) throw new RuntimeException("Sender Account not found");
                if ("NOT_FOUND".equals(receiverAccId)) throw new RuntimeException("Receiver Account not found");
                return new String[]{senderAccId, receiverAccId};
            }).flatMap(ids -> {
                String senderAccId = "SYSTEM".equals(ids[0]) ? null : ids[0];
                String receiverAccId = "SYSTEM".equals(ids[1]) ? null : ids[1];

                Single<Boolean> debitStep = Single.just(true);

                // Step A: Debit (Using Account ID is faster and safer)
                if (senderAccId != null) {
                    String debitSql = "UPDATE accounts SET balance = balance - ?, version = version + 1 WHERE id = ? AND balance >= ?";
                    debitStep = conn.preparedQuery(debitSql)
                            .rxExecute(Tuple.of(amount, senderAccId, amount))
                            .map(rows -> rows.rowCount() > 0)
                            .flatMap(success -> success ? Single.just(true) : Single.error(new RuntimeException("Insufficient Funds")));
                }

                return debitStep.flatMap(ok -> {
                    // Step B: Credit (Using Account ID)
                    if (receiverAccId != null) {
                        String creditSql = "UPDATE accounts SET balance = balance + ?, version = version + 1 WHERE id = ?";
                        return conn.preparedQuery(creditSql)
                                .rxExecute(Tuple.of(amount, receiverAccId))
                                .map(rows -> true);
                    }
                    return Single.just(true);
                }).flatMap(ok -> {
                    // Step C: Audit Log (NOW CORRECT: Uses Account ID, not Profile ID)
                    String insertSql = "INSERT INTO transactions (id, sender_account_id, receiver_account_id, amount, status, reference_id, email_sent) VALUES (?, ?, ?, ?, 'COMPLETED', ?, false)";
                    return conn.preparedQuery(insertSql)
                            .rxExecute(Tuple.of(UUID.randomUUID().toString(), senderAccId, receiverAccId, amount, refId))
                            .map(res -> true);
                });
            }).toMaybe();

        }).subscribe(
                result -> { // 1. Reply to HTTP Client (Keep this)
                    // 1. Reply to HTTP Client
                    msg.reply(new JsonObject().put("status", "SUCCESS").put("ref_id", refId));

                    // 2. Publish Success Event with IDs
                    JsonObject eventData = new JsonObject()
                            .put("amount", amount)
                            .put("ref_id", refId)
                            .put("from_profile_id", fromProfileId) // Pass the Sender UUID
                            .put("to_profile_id", toProfileId);    // Pass the Receiver UUID

                    vertx.eventBus().publish("event.transaction.success", eventData);
                },
                err -> {
                    System.err.println("Transaction Error: " + err.getMessage());
                    msg.fail(500, err.getMessage());
                }
        );
    }
}