package com.fintech.handler;

import com.fintech.utils.UserLookupUtil;
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




        UserLookupUtil.lookupUser(dbClient, toInput).subscribe(
                receiverProfileId -> {
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