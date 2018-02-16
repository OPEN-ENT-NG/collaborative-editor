/*
 * Copyright © Région Nord Pas de Calais-Picardie, 2016.
 *
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package net.atos.entng.collaborativeeditor.cron;

import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoUpdateBuilder;
import net.atos.entng.collaborativeeditor.CollaborativeEditor;
import org.entcore.common.http.request.JsonHttpServerRequest;
import org.entcore.common.notification.TimelineHelper;
import org.etherpad_lite_client.EPLiteClient;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

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
        this.numberDaysWithoutActivity =  config.getInteger("numberDaysWithoutActivity", 90);
        this.recurringNotificationDays = config.getInteger("recurringNotificationDays", 15);
        this.host = config.getString("host", "http://localhost:8090");
    }

    @Override
    public void handle(Long event) {
        // Check the last edit file date on all entries
        final JsonObject query = new JsonObject();
        final JsonObject projection = new JsonObject().put("name", 1).put("epName", 1)
                .put("owner", 1).put("locale", 1).put("daysBeforeNotification", 1);
        mongo.find(CollaborativeEditor.COLLABORATIVEEDITOR_COLLECTION, query, null, projection, new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> event) {
                final JsonArray res = event.body().getJsonArray("results");
                if ("ok".equals(event.body().getString("status")) && res != null && res.size() > 0) {
                    for (Object object : res) {
                        if (!(object instanceof JsonObject)) continue;
                        final JsonObject elem = (JsonObject) object;
                        client.getLastEdited(elem.getString("epName"), new Handler<JsonObject>() {
                            @Override
                            public void handle(JsonObject event) {
                                if ("ok".equals(event.getString("status"))) {
                                    final Object lastEditedPad = event.getJsonObject("lastEdited");
                                    if (lastEditedPad != null) {

                                        final Long todayL = new Date().getTime();
                                        final Long lastEditedPadL = new Date(Long.parseLong(lastEditedPad.toString())).getTime();
                                        final Long numberOfDay = (Math.abs(todayL - lastEditedPadL)) / (1000*60*60*24);

                                        final Integer daysBeforeNotification = elem.getInteger("daysBeforeNotification", 0);
                                        final String id = elem.getString("_id");
                                        final JsonObject updateQuery = new JsonObject().put("_id", id);

                                        if (numberOfDay > numberDaysWithoutActivity && daysBeforeNotification.intValue() == 0) {
                                            final JsonObject params = new JsonObject()
                                                    .put("resourceName", elem.getString("name", ""))
                                                    .put("resourceDate",  new SimpleDateFormat("dd/MM/yyyy").format(lastEditedPadL))
                                                    .put("collaborativeeditorUri", host + "/collaborativeeditor#/view/" + id);

                                            final List<String> recipients = new ArrayList<String>();
                                            recipients.add(elem.getJsonObject("owner").getString("userId"));
                                            final String locale = elem.getString("locale", "fr");

                                            timelineHelper.notifyTimeline(new JsonHttpServerRequest(new JsonObject()
                                                            .put("headers", new JsonObject().put("Accept-Language", locale))),
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