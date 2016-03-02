package com.github.onsdigital.zebedee.util;

import com.github.davidcarboni.httpino.Endpoint;
import com.github.davidcarboni.httpino.Host;
import com.github.davidcarboni.httpino.Http;
import com.github.davidcarboni.httpino.Response;
import com.github.onsdigital.zebedee.content.util.ContentUtil;
import com.github.onsdigital.zebedee.json.publishing.PublishedCollection;
import com.github.onsdigital.zebedee.json.publishing.Result;
import com.github.onsdigital.zebedee.json.publishing.UriInfo;
import com.google.gson.JsonObject;
import org.apache.commons.lang.StringEscapeUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Sends messages to slack.
 */
public class SlackNotification {

    private static final String slackToken = System.getenv("slack_api_token");
    private static final String slackDefaultChannel = System.getenv("slack_default_channel");
    private static final String slackAlarmChannel = System.getenv("slack_alarm_channel");
    private static final String slackPublishChannel = System.getenv("slack_publish_channel");

    private static final String slackBaseUri = "https://slack.com/api/chat.postMessage";
    private static final Host slackHost = new Host(slackBaseUri);
    private static final ExecutorService pool = Executors.newFixedThreadPool(1);


    public static void alarm(String message) {
        String slackUsername = "Alarm";
        String slackEmoji = ":heavy_exclamation_mark:";
        send(message, slackToken, slackAlarmChannel, slackUsername, slackEmoji);
    }

    private static void sendPublishNotification(String message) {
        String slackUsername = "Bot";
        String slackEmoji = ":chart_with_upwards_trend:";
        send(message, slackToken, slackPublishChannel, slackUsername, slackEmoji);
    }

    public static void send(String message) {
        String slackUsername = "Bot";
        String slackEmoji = ":chart_with_upwards_trend:";
        send(message, slackToken, slackDefaultChannel, slackUsername, slackEmoji);
    }

    public static void send(String message, String slackToken, String slackChannel, String slackUsername, String slackEmoji) {

        if (slackToken == null || slackChannel == null) {
            return;
        }

        // send the message
        Future<Exception> exceptionFuture = sendSlackMessage(slackHost, slackToken, slackChannel, slackUsername, slackEmoji, message, pool);
    }

    private static Future<Exception> sendSlackMessage(
            final Host host,
            final String token, final String channel,
            final String userName, final String emoji,
            final String text,
            ExecutorService pool
    ) {
        return pool.submit(() -> {
            Exception result = null;
            try (Http http = new Http()) {
                Endpoint slack = new Endpoint(host, "")
                        .setParameter("token", token)
                        .setParameter("username", userName)
                        .setParameter("channel", channel)
                        .setParameter("icon_emoji", emoji)
                        .setParameter("text", StringEscapeUtils.escapeHtml(text));
                Response<JsonObject> response = http.post(slack, JsonObject.class);
                System.out.println("response.statusLine = " + response.statusLine);
            } catch (Exception e) {
                result = e;
                Log.print(e);
            }
            return result;
        });
    }

    public static void main(String[] args) {
        String timeTaken = String.format("%.3f", (77 / 1000.0));
        System.out.println("timeTaken = " + timeTaken);
        //send("test");
    }


    /**
     * Send a slack message containing collection publication information
     *
     * @param collectionJsonPath
     */
    public static void publishNotification(Path collectionJsonPath) {

        try (InputStream input = Files.newInputStream(collectionJsonPath)) {
            PublishedCollection publishedCollection = ContentUtil.deserialise(input,
                    PublishedCollection.class);

            // get the message for the publication
            String slackMessage = publicationMessage(publishedCollection);
            sendPublishNotification(slackMessage);
        } catch (Exception e) {
            Log.print(e);
        }
    }

    private static String publicationMessage(PublishedCollection publishedCollection) throws ParseException {
        Result result = publishedCollection.publishResults.get(0);

        String timeTaken = String.format("%.2f", (publishedCollection.publishEndDate.getTime() - publishedCollection.publishStartDate.getTime()) / 1000.0);

        String exampleUri = "";
        for (UriInfo info : result.transaction.uriInfos) {
            if (info.uri.endsWith("data.json")) {
                exampleUri = info.uri.substring(0, info.uri.length() - "data.json".length());
                break;
            }
        }

        SimpleDateFormat format = new SimpleDateFormat("HH:mm");
        String message = "Collection " + publishedCollection.name +
                " was published at " + format.format(publishedCollection.publishStartDate) +
                " with " + result.transaction.uriInfos.size() + " files " +
                " in " + timeTaken + " seconds. Example Uri: http://www.ons.gov.uk" + exampleUri;

        return message;
    }
}
