package com.fintech;

import com.fintech.api.RestApiVerticle;
import com.fintech.service.NotificationVerticle; // <--- Import this
import com.fintech.service.TransactionVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {

        vertx.deployVerticle(new TransactionVerticle())
                .onFailure(startPromise::fail)
                .onSuccess(txId -> {
                    System.out.println("✅ Transaction Service Deployed: " + txId);


                    vertx.deployVerticle(new NotificationVerticle())
                            .onFailure(startPromise::fail)
                            .onSuccess(notifId -> {
                                System.out.println("✅ Notification Service Deployed: " + notifId);

                                // 3. Deploy REST API (The HTTP Gateway) - LAST!
                                vertx.deployVerticle(new RestApiVerticle())
                                        .onSuccess(res -> {
                                            System.out.println("🚀 HTTP Server Deployed. System Ready!");
                                            startPromise.complete();
                                        })
                                        .onFailure(startPromise::fail);
                            });
                });
    }


    public static void main(String[] args) {
        Vertx.vertx().deployVerticle(new MainVerticle());
    }
}