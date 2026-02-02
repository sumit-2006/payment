package com.fintech.api;

import com.fintech.handler.AccountHandler;
import com.fintech.handler.AuthHandler; // <--- Import this
import com.fintech.handler.PayrollHandler;
import com.fintech.handler.TransferHandler;
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

public class RestApiVerticle extends AbstractVerticle {

    @Override
    public io.reactivex.rxjava3.core.Completable rxStart() {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());

        // --- 1. Infrastructure Setup ---
        MySQLPool dbClient = MySQLPool.pool(vertx,
                new MySQLConnectOptions().setPort(3306).setHost("localhost").setDatabase("payment").setUser("root").setPassword("Sumit@2006"),
                new PoolOptions().setMaxSize(5));

        // Setup JWT Provider (The "Key" to sign/verify tokens)
        JWTAuth jwtAuth = JWTAuth.create(vertx, new JWTAuthOptions()
                .addPubSecKey(new PubSecKeyOptions().setAlgorithm("HS256").setBuffer("super_secret_key_for_fintech_app")));

        // --- 2. Instantiate Handlers ---
        AuthHandler authHandler = new AuthHandler(jwtAuth,dbClient); // <--- New Handler
        TransferHandler transferHandler = new TransferHandler(vertx,dbClient);
        PayrollHandler payrollHandler = new PayrollHandler(vertx);
        AccountHandler accountHandler = new AccountHandler(dbClient);

        // --- 3. Define Routes ---

        // Public Route: Login (Generates the Token)
        router.post("/api/auth/login").handler(authHandler::login);
        router.post("/api/auth/signup").handler(authHandler::signup);

        // Protected Middleware: All api/v1/* requests MUST have a valid Token
        router.route("/api/v1/*").handler(JWTAuthHandler.create(jwtAuth));

        // Secured Business Routes
        router.post("/api/v1/transfer").handler(transferHandler::sendMoney);
        router.post("/api/v1/payroll").handler(payrollHandler::depositSalary);
        router.get("/api/v1/balance").handler(accountHandler::getBalance);
        router.route("/*").handler(StaticHandler.create());
        return vertx.createHttpServer()
                .requestHandler(router)
                .rxListen(8080)
                .ignoreElement();
    }
}