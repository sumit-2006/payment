package com.fintech;

import com.fintech.api.RestApiVerticle;
import com.fintech.service.TransactionVerticle;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;

public class MainVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> startPromise) {
        // 1. Deploy the Transaction Service (Business Logic)
        vertx.deployVerticle(new TransactionVerticle())
                .onFailure(err -> startPromise.fail(err))
                .onSuccess(id -> {
                    System.out.println("Transaction Service Deployed: " + id);

                    // 2. Deploy the REST API (Gateway)
                    vertx.deployVerticle(new RestApiVerticle())
                            .onSuccess(res -> startPromise.complete())
                            .onFailure(err -> startPromise.fail(err));
                });
    }
}