package com.fintech.service;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.core.eventbus.Message;

public class NotificationVerticle extends AbstractVerticle {

    @Override
    public io.reactivex.rxjava3.core.Completable rxStart() {
        // Listen for successful transactions
        vertx.eventBus().consumer("event.transaction.success", this::handleNotification);
        return io.reactivex.rxjava3.core.Completable.complete();
    }

    private void handleNotification(Message<JsonObject> msg) {
        JsonObject details = msg.body();
        String email = details.getString("email");
        Double amount = details.getDouble("amount");

        // REAL WORLD: Use JavaMail or SendGrid API here.
        // FOR NOW: We just print to console (Mocking the email)
        System.out.println("------------------------------------------------");
        System.out.println("📧 SENDING EMAIL TO: " + email);
        System.out.println("Subject: Payment Received");
        System.out.println("Body: You have received $" + amount);
        System.out.println("------------------------------------------------");

        // Optional: Update DB to set email_sent=true
    }
}