package com.fintech.handler;

import com.fintech.utils.UserLookupUtil;
import io.reactivex.rxjava3.core.Flowable;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.FileUpload;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Tuple;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BulkTransactionHandler {

    private final Vertx vertx;
    private final MySQLPool dbClient;

    public BulkTransactionHandler(Vertx vertx, MySQLPool dbClient) {
        this.vertx = vertx;
        this.dbClient = dbClient;
    }

    public void submitJob(RoutingContext ctx) {
        if (ctx.fileUploads().isEmpty()) {
            ctx.fail(400);
            return;
        }

        String jobId = UUID.randomUUID().toString();
        FileUpload file = ctx.fileUploads().iterator().next();
        String tempFilePath = file.uploadedFileName();

        dbClient.preparedQuery("INSERT INTO bulk_jobs (id, status) VALUES (?, 'PROCESSING')")
                .rxExecute(Tuple.of(jobId))
                .subscribe(
                        res -> {
                            ctx.response().setStatusCode(202); // 202 = Accepted
                            ctx.json(new JsonObject()
                                    .put("message", "File accepted for processing")
                                    .put("job_id", jobId)
                                    .put("status_url", "/api/v1/bulk-status/" + jobId));

                            processInBackground(tempFilePath, jobId);
                        },
                        err -> ctx.fail(500)
                );
    }


    public void getJobStatus(RoutingContext ctx) {
        String jobId = ctx.pathParam("id");

        dbClient.preparedQuery("SELECT id, status, total_records, success_count, failed_count, details FROM bulk_jobs WHERE id = ?")
                .rxExecute(Tuple.of(jobId))
                .subscribe(
                        rows -> {
                            if (rows.size() == 0) {
                                ctx.fail(404);
                                return;
                            }
                            var row = rows.iterator().next();

                            JsonObject response = new JsonObject()
                                    .put("job_id", row.getString("id"))
                                    .put("status", row.getString("status"))
                                    .put("progress", new JsonObject()
                                            .put("total", row.getInteger("total_records"))
                                            .put("success", row.getInteger("success_count"))
                                            .put("failed", row.getInteger("failed_count"))
                                    );

                            if (row.getJsonObject("details") != null) {
                                response.put("report", row.getJsonObject("details"));
                            }

                            ctx.json(response);
                        },
                        err -> ctx.fail(500)
                );
    }

    private void processInBackground(String filePath, String jobId) {
        vertx.rxExecuteBlocking(promise -> {
                    List<JsonObject> transactions = new ArrayList<>();
                    try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            String[] parts = line.split(",");
                            if (parts.length >= 3) {
                                JsonObject tx = new JsonObject()
                                        .put("from", parts[0].trim())
                                        .put("to", parts[1].trim())
                                        .put("amount", Double.parseDouble(parts[2].trim()))
                                        .put("ref_id", "bulk-" + UUID.randomUUID().toString());
                                transactions.add(tx);
                            }
                        }
                    } catch (Exception e) {
                        promise.fail(e);
                        return;
                    }
                    promise.complete(transactions);
                })
                .toSingle()
                .flatMap(data -> {
                    List<JsonObject> txList = (List<JsonObject>) data;


                    return dbClient.preparedQuery("UPDATE bulk_jobs SET total_records = ? WHERE id = ?")
                            .rxExecute(Tuple.of(txList.size(), jobId))
                            .map(ignore -> txList);
                })
                .flatMap(txList -> {
                    return Flowable.fromIterable(txList)
                            .flatMapSingle(tx -> {
                                String toInput = tx.getString("to");
                                return UserLookupUtil.lookupUser(dbClient, toInput)
                                        .flatMap(receiverUUID -> {
                                            tx.put("to", receiverUUID);
                                            return vertx.eventBus().<JsonObject>rxRequest("service.transaction.process", tx)
                                                    .map(msg -> new JsonObject().put("status", "SUCCESS").put("ref", tx.getString("ref_id")));
                                        })
                                        .onErrorReturn(err -> new JsonObject()
                                                .put("status", "FAILED")
                                                .put("ref", tx.getString("ref_id"))
                                                .put("error", err.getMessage()));
                            }, false, 5)
                            .toList();
                })
                .subscribe(
                        results -> {

                            int success = 0;
                            int failed = 0;
                            for (Object res : results) {
                                JsonObject json = (JsonObject) res;
                                if ("SUCCESS".equals(json.getString("status"))) success++;
                                else failed++;
                            }


                            JsonObject finalReport = new JsonObject().put("results", results);

                            dbClient.preparedQuery("UPDATE bulk_jobs SET status = 'COMPLETED', success_count = ?, failed_count = ?, details = ? WHERE id = ?")
                                    .rxExecute(Tuple.of(success, failed, finalReport, jobId))
                                    .subscribe();
                        },
                        err -> {

                            dbClient.preparedQuery("UPDATE bulk_jobs SET status = 'FAILED' WHERE id = ?")
                                    .rxExecute(Tuple.of(jobId))
                                    .subscribe();
                        }
                );
    }
}