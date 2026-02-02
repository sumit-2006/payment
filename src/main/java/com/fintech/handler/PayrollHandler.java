package com.fintech.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.RoutingContext;

public class PayrollHandler {

    private final Vertx vertx;

    public PayrollHandler(Vertx vertx) {
        this.vertx = vertx;
    }


    public void depositSalary(RoutingContext ctx) {

        String role = ctx.user().principal().getString("role");
        if (!"FINANCE".equals(role)) {
            ctx.fail(403); // Forbidden
            return;
        }

        JsonObject body = ctx.body().asJsonObject();

        JsonObject message = new JsonObject()
                .put("from", (String) null) // NULL 'from' means System Deposit
                .put("to", body.getString("to"))
                .put("amount", body.getDouble("amount"))
                .put("ref_id", body.getString("ref_id"));

        vertx.eventBus().<JsonObject>rxRequest("service.transaction.process", message)
                .subscribe(
                        success -> ctx.json(success.body()),
                        error -> ctx.response().setStatusCode(500).end(new JsonObject().put("error", error.getMessage()).encode())
                );
    }
}