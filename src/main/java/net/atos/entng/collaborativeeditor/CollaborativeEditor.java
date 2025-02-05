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
import io.vertx.core.Promise;
import net.atos.entng.collaborativeeditor.controllers.CollaborativeEditorController;
import net.atos.entng.collaborativeeditor.cron.NotUsingPAD;
import net.atos.entng.collaborativeeditor.events.CollaborativeEditorRepositoryEvents;
import net.atos.entng.collaborativeeditor.events.CollaborativeEditorSearchingEvents;
import net.atos.entng.collaborativeeditor.explorer.CollaborativeEditorExplorerPlugin;
import net.atos.entng.collaborativeeditor.helpers.EtherpadHelper;
import org.entcore.common.explorer.IExplorerPluginClient;
import org.entcore.common.explorer.impl.ExplorerRepositoryEvents;
import org.entcore.common.http.BaseServer;
import org.entcore.common.http.filter.ShareAndOwner;
import org.entcore.common.mongodb.MongoDbConf;
import org.entcore.common.notification.TimelineHelper;
import org.entcore.common.service.impl.MongoDbSearchService;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

/**
 * Server to manage collaborative editors. This class is the entry point of the Vert.x module.
 * @author AtoS
 */
public class CollaborativeEditor extends BaseServer {

    /**
     * Define the Explorer application name.
     */
	public static final String APPLICATION = "collaborativeeditor";
	/**
     * Define the Explorer type.
     */
	public static final String TYPE = "collaborativeeditor";

    /**
     * Constant to define the MongoDB collection to use with this module.
     */
    public static final String COLLABORATIVEEDITOR_COLLECTION = "collaborativeeditor";

    private CollaborativeEditorExplorerPlugin explorerPlugin;

    /**
     * Entry point of the Vert.x module
     */
    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        super.start(startPromise);

        // Create Explorer plugin
		this.explorerPlugin = CollaborativeEditorExplorerPlugin.create(securedActions);

        // Mongo Conf
        MongoDbConf conf = MongoDbConf.getInstance();
        // Set the main collection
        conf.setCollection(COLLABORATIVEEDITOR_COLLECTION);

        setDefaultResourceFilter(new ShareAndOwner());

        final EtherpadHelper etherpadHelper = new EtherpadHelper(
                vertx
                , COLLABORATIVEEDITOR_COLLECTION
                , config.getJsonArray("domains")
                , config.getString("etherpad-url", null)
                , config.getString("etherpad-api-key", null)
                , config.getBoolean("trust-all-certificate", true)
                , config.getString("etherpad-domain", null)
                , config
                , explorerPlugin);
        
        // Subscribe to events published for searching
        if (config.getBoolean("searching-event", true)) {
            setSearchingEvents(new CollaborativeEditorSearchingEvents(vertx,
                    new MongoDbSearchService(COLLABORATIVEEDITOR_COLLECTION)));
        }

        // Create Repository Event with Explorer Proxy
        final IExplorerPluginClient mainClient = IExplorerPluginClient.withBus(vertx, APPLICATION, TYPE);
		final Map<String, IExplorerPluginClient> pluginClientPerCollection = new HashMap<>();
		pluginClientPerCollection.put(COLLABORATIVEEDITOR_COLLECTION, mainClient);
        setRepositoryEvents(new ExplorerRepositoryEvents(new CollaborativeEditorRepositoryEvents(vertx, etherpadHelper), pluginClientPerCollection, mainClient));
        
        // Add Controller
        addController(new CollaborativeEditorController(COLLABORATIVEEDITOR_COLLECTION, etherpadHelper, explorerPlugin));

        // Cron
        final String unusedPadCron = config.getString("unusedPadCron", "0 0 23 * * ?");
        final TimelineHelper timelineHelper = new TimelineHelper(vertx, vertx.eventBus(), config);

        try {
            new CronTrigger(vertx, unusedPadCron).schedule(
                    new NotUsingPAD(timelineHelper, etherpadHelper.getFirstClient(), config)
            );
        } catch (ParseException e) {
            log.fatal("[Collaborative Editor] Invalid cron expression.", e);
            //vertx.stop();
            vertx.close();
        }

        // Start Explorer plugin
        this.explorerPlugin.start();

        startPromise.tryComplete();
    }
}