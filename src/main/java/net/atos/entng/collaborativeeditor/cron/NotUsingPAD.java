package net.atos.entng.collaborativeeditor.cron;

import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoUpdateBuilder;
import net.atos.entng.collaborativeeditor.CollaborativeEditor;
import org.entcore.common.http.request.JsonHttpServerRequest;
import org.entcore.common.notification.TimelineHelper;
import org.etherpad_lite_client.EPLiteClient;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by dbreyton on 03/05/2016.
 */
public class NotUsingPAD implements Handler<Long> {

    private final MongoDb mongo = MongoDb.getInstance();
    private final EPLiteClient client;
    private final TimelineHelper timelineHelper;
    private final Integer numberDaysWithoutActivity;
    private final Integer recurringNotificationDays;
    private final String host;
    private static final Logger log = LoggerFactory.getLogger(NotUsingPAD.class);

    public NotUsingPAD(final TimelineHelper timelineHelper,  final EPLiteClient client, final JsonObject config) {
        this.timelineHelper = timelineHelper;
        this.client = client;
        this.numberDaysWithoutActivity =  config.getInteger("numberDaysWithoutActivity", 60);
        this.recurringNotificationDays = config.getInteger("recurringNotificationDays", 10);
        this.host = config.getString("host", "http://localhost:8090");
    }

    @Override
    public void handle(Long event) {
        // Check the last edit file date on all entries
        final JsonObject query = new JsonObject();
        final JsonObject projection = new JsonObject().putNumber("name", 1).putNumber("epName", 1)
                .putNumber("owner", 1).putNumber("locale", 1).putNumber("daysBeforeNotification", 1);
        mongo.find(CollaborativeEditor.COLLABORATIVEEDITOR_COLLECTION, query, null, projection, new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> event) {
                final JsonArray res = event.body().getArray("results");
                if ("ok".equals(event.body().getString("status")) && res != null && res.size() > 0) {
                    for (Object object : res) {
                        if (!(object instanceof JsonObject)) continue;
                        final JsonObject elem = (JsonObject) object;
                        client.getLastEdited(elem.getString("epName"), new Handler<JsonObject>() {
                            @Override
                            public void handle(JsonObject event) {
                                if ("ok".equals(event.getField("status"))) {
                                    final Object lastEditedPad = event.getField("lastEdited");
                                    if (lastEditedPad != null) {

                                        final Long todayL = new Date().getTime();
                                        final Long lastEditedPadL = new Date(Long.parseLong(lastEditedPad.toString())).getTime();
                                        final Long numberOfDay = (Math.abs(todayL - lastEditedPadL)) / (1000*60*60*24);

                                        final Integer daysBeforeNotification = elem.getInteger("daysBeforeNotification", 0);
                                        final String id = elem.getString("_id");
                                        final JsonObject updateQuery = new JsonObject().putString("_id", id);

                                        if (numberOfDay > numberDaysWithoutActivity && daysBeforeNotification.intValue() == 0) {
                                            final JsonObject params = new JsonObject()
                                                    .putString("resourceName", elem.getString("name", ""))
                                                    .putString("resourceDate",  new SimpleDateFormat("dd/MM/yyyy").format(lastEditedPadL))
                                                    .putString("collaborativeeditorUri", host + "/collaborativeeditor#/view/" + id);

                                            final List<String> recipients = new ArrayList<String>();
                                            recipients.add(elem.getObject("owner").getString("userId"));
                                            final String locale = elem.getString("locale", "fr");

                                            timelineHelper.notifyTimeline(new JsonHttpServerRequest(new JsonObject()
                                                            .putObject("headers", new JsonObject().putString("Accept-Language", locale))),
                                                    "collaborativeeditor.unused", null, recipients, null, params);

                                            final MongoUpdateBuilder modifier = new MongoUpdateBuilder();
                                            //Adding notification flag (don't SPAM)
                                            modifier.set("daysBeforeNotification", recurringNotificationDays);

                                            mongo.update(CollaborativeEditor.COLLABORATIVEEDITOR_COLLECTION, updateQuery, modifier.build(), new Handler<Message<JsonObject>>() {
                                                @Override
                                                public void handle(Message<JsonObject> event) {
                                                    if (!"ok".equals(event.body().getString("status"))) {
                                                        log.error(event.body().getString("message"));
                                                    }
                                                }
                                            });
                                        } else if (numberOfDay > numberDaysWithoutActivity && daysBeforeNotification.intValue() > 0) {
                                            final MongoUpdateBuilder modifier = new MongoUpdateBuilder();
                                            //Decrementing notification flag (Allow to send a new notif for next cron)
                                            modifier.set("daysBeforeNotification", daysBeforeNotification.intValue()-1);

                                            mongo.update(CollaborativeEditor.COLLABORATIVEEDITOR_COLLECTION, updateQuery, modifier.build(), new Handler<Message<JsonObject>>() {
                                                @Override
                                                public void handle(Message<JsonObject> event) {
                                                    if (!"ok".equals(event.body().getString("status"))) {
                                                        log.error(event.body().getString("message"));
                                                    }
                                                }
                                            });
                                        }
                                    }
                                } else {
                                    log.error("Can't get last edited PAD date : " + event.getString("message", ""));
                                }
                            }
                        });
                    }
                } else {
                    log.error(event.body().getString("message"));
                }
            }
        });
    }
}