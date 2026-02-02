package com.fintech.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Tuple;

public class AccountHandler {

    private final MySQLPool dbClient;

    public AccountHandler(MySQLPool dbClient) {
        this.dbClient = dbClient;
    }

    public void getBalance(RoutingContext ctx) {

        String profileId = ctx.user().principal().getString("sub");


        dbClient.preparedQuery("SELECT balance FROM accounts WHERE profile_id = ?")
                .rxExecute(Tuple.of(profileId))
                .subscribe(
                        rows -> {
                            if (rows.size() == 0) {

                                ctx.fail(404);
                            } else {
                                Double balance = rows.iterator().next().getDouble("balance");
                                ctx.json(new JsonObject().put("balance", balance));
                            }
                        },
                        err -> {
                            err.printStackTrace();
                            ctx.fail(500);
                        }
                );
    }
}