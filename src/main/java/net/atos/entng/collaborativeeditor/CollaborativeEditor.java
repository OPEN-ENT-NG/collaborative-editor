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

package net.atos.entng.collaborativeeditor;

import fr.wseduc.cron.CronTrigger;
import net.atos.entng.collaborativeeditor.controllers.CollaborativeEditorController;
import net.atos.entng.collaborativeeditor.cron.NotUsingPAD;
import net.atos.entng.collaborativeeditor.events.CollaborativeEditorSearchingEvents;
import org.entcore.common.http.BaseServer;
import org.entcore.common.http.filter.ShareAndOwner;
import org.entcore.common.mongodb.MongoDbConf;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.service.impl.MongoDbSearchService;
import org.etherpad_lite_client.EPLiteClient;

import java.text.ParseException;

/**
 * Server to manage collaborative editors. This class is the entry point of the Vert.x module.
 * @author AtoS
 */
public class CollaborativeEditor extends BaseServer {

    /**
     * Constant to define the MongoDB collection to use with this module.
     */
    public static final String COLLABORATIVEEDITOR_COLLECTION = "collaborativeeditor";

    /**
     * Entry point of the Vert.x module
     */
    @Override
    public void start() {
        super.start();

        MongoDbConf conf = MongoDbConf.getInstance();
        conf.setCollection(COLLABORATIVEEDITOR_COLLECTION);

        setDefaultResourceFilter(new ShareAndOwner());

        addController(new CollaborativeEditorController(vertx, COLLABORATIVEEDITOR_COLLECTION, container));
        // Subscribe to events published for searching
        final EPLiteClient epClient = new EPLiteClient(vertx, config.getString("etherpad-public-url",
                config.getString("etherpad-url", "")), config.getString("etherpad-api-key",""));
        setSearchingEvents(new CollaborativeEditorSearchingEvents(vertx,
                new MongoDbSearchService(COLLABORATIVEEDITOR_COLLECTION), epClient));
        final String unusedPadCron = container.config().getString("unusedPadCron", "0 0 23 * * ?");
        final TimelineHelper timelineHelper = new TimelineHelper(vertx, vertx.eventBus(), container);

        try {
            new CronTrigger(vertx, unusedPadCron).schedule(
                    new NotUsingPAD(timelineHelper, epClient, container.config())
            );
        } catch (ParseException e) {
            log.fatal("[Collaborative Editor] Invalid cron expression.", e);
            vertx.stop();
        }
    }
}