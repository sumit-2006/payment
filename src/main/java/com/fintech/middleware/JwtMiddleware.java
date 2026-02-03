package com.fintech.middleware;

import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.auth.jwt.JWTAuth;
import io.vertx.rxjava3.ext.web.handler.JWTAuthHandler;

public class JwtMiddleware {

    private static final String SECRET_KEY = "your-secret-key-change-this-in-production";

    public static JWTAuth createAuthProvider(Vertx vertx) {
        return JWTAuth.create(vertx, new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions()
                        .setAlgorithm("HS256")
                        .setBuffer(SECRET_KEY)
                        .setSymmetric(true)));
    }

    public static JWTAuthHandler createHandler(JWTAuth jwtAuth) {
        return JWTAuthHandler.create(jwtAuth);
    }
}