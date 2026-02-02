package com.fintech.service;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.StartTLSOptions;
import io.vertx.ext.mail.MailMessage;
import io.vertx.rxjava3.core.AbstractVerticle;
import io.vertx.rxjava3.core.eventbus.Message;
import io.vertx.rxjava3.ext.mail.MailClient;
import io.vertx.rxjava3.mysqlclient.MySQLPool;
import io.vertx.rxjava3.sqlclient.Tuple;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.reactivex.rxjava3.core.Single;
import io.vertx.ext.mail.LoginOption; // <--- Add this import

public class NotificationVerticle extends AbstractVerticle {

    private MailClient mailClient;
    private MySQLPool dbClient;

    @Override
    public io.reactivex.rxjava3.core.Completable rxStart() {
        MySQLConnectOptions connectOptions = new MySQLConnectOptions()
                .setPort(3306).setHost("localhost").setDatabase("payment")
                .setUser("root").setPassword("Sumit@2006");
        dbClient = MySQLPool.pool(vertx, connectOptions, new PoolOptions().setMaxSize(3));

        MailConfig config = new MailConfig()
                .setHostname("smtp.gmail.com")
                .setPort(587)
                .setStarttls(StartTLSOptions.REQUIRED)
                .setLogin(LoginOption.REQUIRED)      // <--- FORCE LOGIN
                .setAuthMethods("PLAIN")             // <--- ONLY USE PLAIN AUTH (Stops XOAUTH2 Warnings)
                .setUsername("sumitmahajan2006@gmail.com") // Your Email
                .setPassword("dkvkyhhikfngnmfp");  // <--- REMEMBER TO UPDATE THIS

        mailClient = MailClient.create(vertx, config);

        vertx.eventBus().consumer("event.transaction.success", this::handleNotification);

        return io.reactivex.rxjava3.core.Completable.complete();
    }

    private void handleNotification(Message<JsonObject> msg) {
        JsonObject event = msg.body();
        String fromId = event.getString("from_profile_id");
        String toId = event.getString("to_profile_id");
        Double amount = event.getDouble("amount");
        String refId = event.getString("ref_id");

        Single<String> senderEmail = resolveEmail(fromId);
        Single<String> receiverEmail = resolveEmail(toId);

        Single.zip(senderEmail, receiverEmail, (sEmail, rEmail) -> {
            return new String[]{sEmail, rEmail};
        }).subscribe(
                emails -> {
                    String sEmail = emails[0];
                    String rEmail = emails[1];

                    if (!rEmail.isEmpty()) {
                        sendEmail(rEmail, "💰 You received $" + amount,
                                "<h3>Payment Received</h3><p>You have received <b>$" + amount + "</b>.</p><p>Ref: " + refId + "</p>");
                    }

                    if (!sEmail.isEmpty()) {
                        sendEmail(sEmail, "💸 You sent $" + amount,
                                "<h3>Payment Sent</h3><p>You have successfully sent <b>$" + amount + "</b>.</p><p>Ref: " + refId + "</p>");
                    }
                },
                err -> System.err.println("Failed to process notification: " + err.getMessage())
        );
    }

    private Single<String> resolveEmail(String profileId) {
        if (profileId == null || "SYSTEM".equals(profileId)) return Single.just("");

        return dbClient.preparedQuery("SELECT email FROM profiles WHERE id = ?")
                .rxExecute(Tuple.of(profileId))
                .map(rows -> {
                    if (rows.size() == 0) return ""; // Return empty string if not found
                    String email = rows.iterator().next().getString("email");
                    return email == null ? "" : email;
                })
                .onErrorReturnItem(""); // FIX: Return empty string on error, NEVER null
    }

    private void sendEmail(String to, String subject, String htmlBody) {
        MailMessage message = new MailMessage()
                .setFrom("no-reply@fintech-app.com")
                .setTo(to)
                .setSubject(subject)
                .setHtml(htmlBody);

        mailClient.rxSendMail(message)
                .subscribe(
                        result -> System.out.println("✅ Email sent to " + to),
                        err -> System.err.println("❌ Failed to send email to " + to + ": " + err.getMessage())
                );
    }
}