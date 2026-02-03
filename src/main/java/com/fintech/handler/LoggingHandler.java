package com.fintech.handler;

import io.vertx.rxjava3.ext.web.RoutingContext;

public class LoggingHandler {

    public static void log(RoutingContext ctx) {
        String method = ctx.request().method().name();
        String path = ctx.request().path();
        String remote = ctx.request().remoteAddress().host();

        System.out.println("------------------------------------------------");
        System.out.println("📝 INCOMING: " + method + " " + path + " from " + remote);

        // IMPORTANT: Pass control to the next handler!
        ctx.next();
    }
}