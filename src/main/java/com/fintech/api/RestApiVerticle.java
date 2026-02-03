package com.fintech.api;

import com.fintech.handler.*;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.ext.auth.jwt.JWTAuth;
import io.vertx.rxjava3.ext.web.Router;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import io.vertx.rxjava3.ext.web.handler.JWTAuthHandler;
import io.vertx.rxjava3.ext.web.handler.StaticHandler;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import com.fintech.middleware.JwtMiddleware;

public class RestApiVerticle extends AbstractVerticle {

    @Override
    public io.reactivex.rxjava3.core.Completable rxStart() {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());


        MySQLPool dbClient = MySQLPool.pool(vertx,
                new MySQLConnectOptions().setPort(3306).setHost("localhost").setDatabase("payment").setUser("root").setPassword("Sumit@2006"),
                new PoolOptions().setMaxSize(5));

        JWTAuth jwtAuth = JwtMiddleware.createAuthProvider(vertx);


        AuthHandler authHandler = new AuthHandler(jwtAuth,dbClient); // <--- New Handler
        TransferHandler transferHandler = new TransferHandler(vertx,dbClient);
        PayrollHandler payrollHandler = new PayrollHandler(vertx);
        AccountHandler accountHandler = new AccountHandler(dbClient);
        TransactionHistoryHandler historyHandler = new TransactionHistoryHandler(dbClient);
        ProfileHandler profileHandler = new ProfileHandler(dbClient);
        BulkTransactionHandler bulkHandler = new BulkTransactionHandler(vertx,dbClient);

        Router mainRouter = Router.router(vertx);
        mainRouter.route().handler(BodyHandler.create()); // Parse bodies first
        mainRouter.route().handler(LoggingHandler::log);

        Router apiRouter = Router.router(vertx);

        apiRouter.route().handler(ctx -> {
            ctx.response().putHeader("Content-Type", "application/json");
            ctx.response().putHeader("Access-Control-Allow-Origin", "*");
            ctx.response().putHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ctx.next();
        });
        //public routes
        apiRouter.post("/auth/login").handler(authHandler::login);
        apiRouter.post("/auth/signup").handler(authHandler::signup);

        // -- Protected Routes (Login Required) --
        // We create a "Sub-Router" for /v1 to group protected items
        Router protectedRouter = Router.router(vertx);

        // 1. The Gatekeeper: Check JWT Token
        protectedRouter.route().handler(JwtMiddleware.createHandler(jwtAuth));

        // 2. The Routes
        protectedRouter.post("/transfer").handler(transferHandler::sendMoney);
        protectedRouter.post("/payroll").handler(payrollHandler::depositSalary);
        protectedRouter.get("/balance").handler(accountHandler::getBalance);
        protectedRouter.get("/history").handler(historyHandler::getHistory);
        protectedRouter.get("/profile").handler(profileHandler::getProfile);
        protectedRouter.post("/bulk-transfer").handler(bulkHandler::submitJob);
        protectedRouter.get("/bulk-status/:id").handler(bulkHandler::getJobStatus);

        // Mount "Protected" inside "API"
        apiRouter.mountSubRouter("/v1", protectedRouter);

        // Mount "API" inside "Main"
        mainRouter.mountSubRouter("/api", apiRouter);


        // ====================================================
        // ZONE B: THE FRONTEND (HTML)
        // ====================================================
        // This must be last so it doesn't try to catch API calls.
        // Since it's on 'mainRouter', it won't get the JSON header we added to 'apiRouter'.
        mainRouter.route("/*").handler(StaticHandler.create());

        // 4. START SERVER
        int port = 8080;
        if (System.getenv("PORT") != null) {
            port = Integer.parseInt(System.getenv("PORT"));
        }

        return vertx.createHttpServer()
                .requestHandler(mainRouter)
                .rxListen(port, "0.0.0.0")
                .ignoreElement();
    }
}