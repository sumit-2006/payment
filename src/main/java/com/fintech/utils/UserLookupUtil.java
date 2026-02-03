package com.fintech.utils;

import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Tuple;

public class UserLookupUtil {

    public static Single<String> lookupUser(MySQLPool dbClient, String input) {
        if (input == null || input.trim().isEmpty()) {
            return Single.error(new RuntimeException("Input cannot be empty"));
        }

        String cleanInput = input.trim();

        if (cleanInput.contains("@")) {
            return dbClient.preparedQuery("SELECT id FROM profiles WHERE LOWER(email) = LOWER(?)")
                    .rxExecute(Tuple.of(cleanInput))
                    .map(rows -> {
                        if (rows.size() == 0) throw new RuntimeException("User with email " + cleanInput + " not found");
                        return rows.iterator().next().getString("id");
                    });
        }

        if (cleanInput.length() == 36 && cleanInput.contains("-")) {
            return Single.just(cleanInput);
        }

        return dbClient.preparedQuery("SELECT id FROM profiles WHERE employee_id = ?")
                .rxExecute(Tuple.of(cleanInput))
                .map(rows -> {
                    if (rows.size() == 0) throw new RuntimeException("User with Employee ID " + cleanInput + " not found");
                    return rows.iterator().next().getString("id");
                });
    }
}