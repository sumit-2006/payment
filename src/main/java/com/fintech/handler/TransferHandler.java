package com.fintech.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Tuple;
import io.reactivex.rxjava3.core.Single;

public class TransferHandler {

    private final Vertx vertx;
    private final MySQLPool dbClient;

    public TransferHandler(Vertx vertx, MySQLPool dbClient) {
        this.vertx = vertx;
        this.dbClient = dbClient;
    }

    public void sendMoney(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String toInput = body.getString("to"); // Can be Email, UUID, or Employee ID
        Double amount = body.getDouble("amount");
        String refId = body.getString("ref_id");

        String senderId = ctx.user().principal().getString("sub");

        if (toInput == null || toInput.trim().isEmpty()) {
            ctx.fail(400);
            return;
        }

        // --- SMART LOOKUP LOGIC ---
        Single<String> resolveReceiverId;

        if (toInput.contains("@")) {
            // Case 1: It is an Email -> Lookup UUID
            resolveReceiverId = dbClient.preparedQuery("SELECT id FROM profiles WHERE email = ?")
                    .rxExecute(Tuple.of(toInput))
                    .map(rows -> {
                        if (rows.size() == 0) throw new RuntimeException("User with email " + toInput + " not found");
                        return rows.iterator().next().getString("id");
                    });

        } else if (toInput.length() == 36 && toInput.contains("-")) {
            // Case 2: It is already a UUID -> Use directly
            resolveReceiverId = Single.just(toInput);

        } else {
            // Case 3: It must be an Employee ID -> Lookup UUID
            resolveReceiverId = dbClient.preparedQuery("SELECT id FROM profiles WHERE employee_id = ?")
                    .rxExecute(Tuple.of(toInput))
                    .map(rows -> {
                        if (rows.size() == 0) throw new RuntimeException("User with Employee ID " + toInput + " not found");
                        return rows.iterator().next().getString("id");
                    });
        }

        // --- EXECUTE TRANSFER ---
        resolveReceiverId.subscribe(
                receiverProfileId -> {

                    // Don't let users send money to themselves
                    if (receiverProfileId.equals(senderId)) {
                        ctx.response().setStatusCode(400).end(new JsonObject().put("error", "Cannot transfer to self").encode());
                        return;
                    }

                    JsonObject message = new JsonObject()
                            .put("from", senderId)
                            .put("to", receiverProfileId) // Always passes UUID to the backend
                            .put("amount", amount)
                            .put("ref_id", refId);

                    vertx.eventBus().<JsonObject>rxRequest("service.transaction.process", message)
                            .subscribe(
                                    success -> ctx.json(success.body()),
                                    error -> {
                                        // Handle specific "Insufficient Funds" vs generic errors
                                        int statusCode = error.getMessage().contains("Insufficient") ? 400 : 500;
                                        if (!ctx.response().ended()) {
                                            ctx.response().setStatusCode(statusCode).end(new JsonObject().put("error", error.getMessage()).encode());
                                        }
                                    }
                            );
                },
                err -> {
                    // User not found error
                    if (!ctx.response().ended()) {
                        ctx.response().setStatusCode(400).end(new JsonObject().put("error", err.getMessage()).encode());
                    }
                }
        );
    }
}