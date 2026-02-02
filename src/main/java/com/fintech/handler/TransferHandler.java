package com.fintech.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.mysqlclient.MySQLPool; // Import DB Client
import io.vertx.rxjava3.sqlclient.Tuple;
import io.reactivex.rxjava3.core.Single;

public class TransferHandler {

    private final Vertx vertx;
    private final MySQLPool dbClient; // Add DB Client

    // Update Constructor to accept DB Client
    public TransferHandler(Vertx vertx, MySQLPool dbClient) {
        this.vertx = vertx;
        this.dbClient = dbClient;
    }

    public void sendMoney(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String toInput = body.getString("to"); // User can now type "bob@test.com"
        Double amount = body.getDouble("amount");
        String refId = body.getString("ref_id");

        String senderId = ctx.user().principal().getString("sub");

        // Logic: Is 'toInput' an Email or a UUID?
        Single<String> resolveReceiverId;

        if (toInput != null && toInput.contains("@")) {
            // It looks like an email! Find the Profile UUID.
            resolveReceiverId = dbClient.preparedQuery("SELECT id FROM profiles WHERE email = ?")
                    .rxExecute(Tuple.of(toInput))
                    .map(rows -> {
                        if (rows.size() == 0) throw new RuntimeException("User with email " + toInput + " not found");
                        return rows.iterator().next().getString("id");
                    });
        } else {
            // Assume it's already a UUID
            resolveReceiverId = Single.just(toInput);
        }

        resolveReceiverId.subscribe(
                receiverProfileId -> {
                    // Now we have the UUID (f014c...), proceed as normal
                    JsonObject message = new JsonObject()
                            .put("from", senderId)
                            .put("to", receiverProfileId)
                            .put("amount", amount)
                            .put("ref_id", refId);

                    vertx.eventBus().<JsonObject>rxRequest("service.transaction.process", message)
                            .subscribe(
                                    success -> ctx.json(success.body()),
                                    error -> ctx.response().setStatusCode(500).end(new JsonObject().put("error", error.getMessage()).encode())
                            );
                },
                err -> ctx.response().setStatusCode(400).end(new JsonObject().put("error", err.getMessage()).encode())
        );
    }
}