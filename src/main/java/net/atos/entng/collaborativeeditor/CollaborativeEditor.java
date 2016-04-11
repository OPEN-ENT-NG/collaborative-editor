package net.atos.entng.collaborativeeditor;

import net.atos.entng.collaborativeeditor.controllers.CollaborativeEditorController;

import net.atos.entng.collaborativeeditor.events.CollaborativeEditorSearchingEvents;
import org.entcore.common.http.BaseServer;
import org.entcore.common.http.filter.ShareAndOwner;
import org.entcore.common.mongodb.MongoDbConf;
import org.entcore.common.service.impl.MongoDbSearchService;

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
        setSearchingEvents(new CollaborativeEditorSearchingEvents(vertx, new MongoDbSearchService(COLLABORATIVEEDITOR_COLLECTION),
                config.getString("etherpad-public-url", config.getString("etherpad-url", "")), config.getString("etherpad-api-key","")));
    }
}