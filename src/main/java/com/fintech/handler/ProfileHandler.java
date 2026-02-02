package com.fintech.handler;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Tuple;

public class ProfileHandler {

    private final MySQLPool dbClient;

    public ProfileHandler(MySQLPool dbClient) {
        this.dbClient = dbClient;
    }

    public void getProfile(RoutingContext ctx) {
        String profileId = ctx.user().principal().getString("sub");

        dbClient.preparedQuery("SELECT first_name, last_name, email, employee_id, role FROM profiles WHERE id = ?")
                .rxExecute(Tuple.of(profileId))
                .subscribe(
                        rows -> {
                            if (rows.size() == 0) {
                                ctx.fail(404);
                            } else {
                                var row = rows.iterator().next();
                                JsonObject profile = new JsonObject()
                                        .put("first_name", row.getString("first_name"))
                                        .put("last_name", row.getString("last_name"))
                                        .put("email", row.getString("email"))
                                        .put("employee_id", row.getString("employee_id"))
                                        .put("role", row.getString("role"));

                                ctx.json(profile);
                            }
                        },
                        err -> ctx.fail(500)
                );
    }
}