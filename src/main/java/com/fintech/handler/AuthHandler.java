package com.fintech.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.auth.jwt.JWTAuth;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Tuple;
import org.mindrot.jbcrypt.BCrypt; // <--- Import BCrypt
import java.util.UUID;

public class AuthHandler {

    private final JWTAuth jwtAuth;
    private final MySQLPool dbClient;

    public AuthHandler(JWTAuth jwtAuth, MySQLPool dbClient) {
        this.jwtAuth = jwtAuth;
        this.dbClient = dbClient;
    }

    // --- SECURE LOGIN ---
    // In AuthHandler.java

    public void login(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();
        String employeeId = body.getString("employee_id");
        String password = body.getString("password");

        if (employeeId == null || password == null) {
            ctx.fail(400);
            return;
        }

        // FIX: Select 'id' (UUID) along with the hash
        dbClient.preparedQuery("SELECT id, password_hash FROM profiles WHERE employee_id = ?")
                .rxExecute(Tuple.of(employeeId))
                .subscribe(
                        rows -> {
                            if (rows.size() == 0) {
                                ctx.response().setStatusCode(401).end("Invalid Credentials");
                                return;
                            }

                            var row = rows.iterator().next();
                            String storedHash = row.getString("password_hash");
                            String profileId = row.getString("id"); // <--- GET UUID

                            if (BCrypt.checkpw(password, storedHash)) {
                                JsonObject claims = new JsonObject()
                                        .put("sub", profileId) // <--- STORE UUID IN TOKEN
                                        .put("role", "EMPLOYEE");

                                String token = jwtAuth.generateToken(claims, new JWTOptions().setExpiresInMinutes(60));
                                ctx.json(new JsonObject().put("token", token));
                            } else {
                                ctx.response().setStatusCode(401).end("Invalid Credentials");
                            }
                        },
                        err -> ctx.fail(500)
                );
    }

    // --- SECURE SIGNUP ---
    public void signup(RoutingContext ctx) {
        JsonObject body = ctx.body().asJsonObject();

        String empId = body.getString("employee_id");
        String password = body.getString("password"); // <--- New Input
        String first = body.getString("first_name");
        String last = body.getString("last_name");
        String email = body.getString("email");

        if (empId == null || email == null || password == null) {
            ctx.fail(400);
            return;
        }

        // 1. Hash the Password
        String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt(10));

        String profileId = UUID.randomUUID().toString();
        String accountId = UUID.randomUUID().toString();

        dbClient.withTransaction(conn -> {
            // Step A: Insert Profile with Hash
            // Note the added column: password_hash
            String sqlProfile = "INSERT INTO profiles (id, employee_id, first_name, last_name, email, role, status, password_hash) VALUES (?, ?, ?, ?, ?, 'EMPLOYEE', 'ACTIVE', ?)";

            return conn.preparedQuery(sqlProfile)
                    .rxExecute(Tuple.of(profileId, empId, first, last, email, hashedPassword))
                    .flatMap(res -> {
                        // Step B: Create Account (Same as before)
                        String sqlAccount = "INSERT INTO accounts (id, profile_id, balance, version) VALUES (?, ?, 0.00, 1)";
                        return conn.preparedQuery(sqlAccount)
                                .rxExecute(Tuple.of(accountId, profileId));
                    })
                    .map(res -> true)
                    .toMaybe();

        }).subscribe(
                success -> {
                    ctx.response().setStatusCode(201);
                    ctx.json(new JsonObject()
                            .put("status", "CREATED")
                            .put("employee_id", empId)
                            .put("profile_id", profileId)
                    );
                },
                err -> {
                    ctx.response().setStatusCode(409).end(new JsonObject().put("error", err.getMessage()).encode());
                }
        );
    }
}