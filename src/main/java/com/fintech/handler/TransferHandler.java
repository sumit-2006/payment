package com.fintech.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.RoutingContext;

public class TransferHandler {

    private final Vertx vertx;

    public TransferHandler(Vertx vertx) {
        this.vertx = vertx;
    }

    // Handler for POST /api/v1/transfer
    public void sendMoney(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();

        // Security: Sender MUST be the logged-in user
        String senderId = ctx.user().principal().getString("sub");

        JsonObject message = new JsonObject()
                .put("from", senderId)
                .put("to", body.getString("to"))
                .put("amount", body.getDouble("amount"))
                .put("ref_id", body.getString("ref_id"));

        // Send to Transaction Engine
        vertx.eventBus().<JsonObject>rxRequest("service.transaction.process", message)
                .subscribe(
                        success -> ctx.json(success.body()),
                        error -> ctx.response().setStatusCode(500).end(new JsonObject().put("error", error.getMessage()).encode())
                );
    }
}