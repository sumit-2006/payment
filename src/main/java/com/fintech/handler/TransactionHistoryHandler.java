package com.fintech.handler;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Tuple;

public class TransactionHistoryHandler {

    private final MySQLPool dbClient;

    public TransactionHistoryHandler(MySQLPool dbClient) {
        this.dbClient = dbClient;
    }

    public void getHistory(RoutingContext ctx) {
        String profileId = ctx.user().principal().getString("sub");

        // 1. Get Page Number (Default to 1 if missing)
        String pageParam = ctx.request().getParam("page");
        int page = (pageParam == null) ? 1 : Integer.parseInt(pageParam);
        int pageSize = 5; // Show 5 per page (Change to 10 if you want)
        int offset = (page - 1) * pageSize;

        // 2. Get Account ID
        dbClient.preparedQuery("SELECT id FROM accounts WHERE profile_id = ?")
                .rxExecute(Tuple.of(profileId))
                .flatMap(rows -> {
                    if (rows.size() == 0) throw new RuntimeException("Account not found");
                    String accountId = rows.iterator().next().getString("id");

                    // 3. Fetch Paged Transactions
                    // LIMIT ? OFFSET ? -> This is the magic of pagination
                    String sql = "SELECT amount, created_at, sender_account_id, receiver_account_id, reference_id " +
                            "FROM transactions " +
                            "WHERE sender_account_id = ? OR receiver_account_id = ? " +
                            "ORDER BY created_at DESC LIMIT ? OFFSET ?";

                    return dbClient.preparedQuery(sql)
                            .rxExecute(Tuple.of(accountId, accountId, pageSize, offset))
                            .map(txRows -> {
                                JsonArray history = new JsonArray();
                                txRows.forEach(row -> {
                                    String type = row.getString("sender_account_id").equals(accountId) ? "DEBIT" : "CREDIT";
                                    history.add(new JsonObject()
                                            .put("type", type)
                                            .put("amount", row.getDouble("amount"))
                                            .put("date", row.getLocalDateTime("created_at").toString())
                                            .put("ref_id", row.getString("reference_id"))
                                    );
                                });
                                return history;
                            });
                })
                .subscribe(
                        data -> ctx.json(new JsonObject()
                                .put("page", page)
                                .put("history", data)),
                        err -> ctx.fail(500)
                );
    }
}