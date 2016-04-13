package net.atos.entng.collaborativeeditor.controllers;

import java.util.Map;

import net.atos.entng.collaborativeeditor.CollaborativeEditor;
import net.atos.entng.collaborativeeditor.helpers.EtherpadHelper;

import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.mongodb.MongoDbControllerHelper;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Container;

import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Delete;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.request.RequestUtils;

/**
 * Controller to manage URL paths for collaborative editors.
 * @author AtoS
 */
public class CollaborativeEditorController extends MongoDbControllerHelper {

    private final EtherpadHelper etherpadHelper;

    private EventStore eventStore;

    private enum CollaborativeEditorEvent {
        ACCESS
    }

    @Override
    public void init(Vertx vertx, Container container, RouteMatcher rm, Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, container, rm, securedActions);
        eventStore = EventStoreFactory.getFactory().getEventStore(CollaborativeEditor.class.getSimpleName());

    }

    /**
     * Default constructor
     * @param eb VertX event bus
     * @param collection MongoDB collection to request.
     */
    public CollaborativeEditorController(EventBus eb, String collection, Container container) {
        super(collection);
        JsonObject config = container.config();
        this.etherpadHelper = new EtherpadHelper(collection, config.getString("etherpad-url", null), config.getString("etherpad-api-key", null), config.getString("etherpad-public-url", null),config.getString("etherpad-domain", null));
    }

    @Get("")
    @SecuredAction("collaborativeeditor.view")
    public void view(HttpServerRequest request) {
        renderView(request);

        // Create event "access to application Collaborative Editor" and store it, for module "statistics"
        eventStore.createAndStoreEvent(CollaborativeEditorEvent.ACCESS.name(), request);
    }

    @Override
    @Get("/list/all")
    @ApiDoc("Allows to list all the editors")
    @SecuredAction("collaborativeeditor.list")
    public void list(HttpServerRequest request) {
        etherpadHelper.list(request);
    }

    @Override
    @Post("")
    @ApiDoc("Allows to create a new editor")
    @SecuredAction("collaborativeeditor.create")
    public void create(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "collaborativeeditor", new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject event) {
                etherpadHelper.create(request);
            }
        });
    }

    @Override
    @Get("/:id")
    @ApiDoc("Allows to get a collaborative editor associated to the given identifier")
    @SecuredAction(value = "collaborativeeditor.read", type = ActionType.RESOURCE)
    public void retrieve(HttpServerRequest request) {
        etherpadHelper.retrieve(request);
    }

    @Get("/session/:id")
    @ApiDoc("Allows to create a session on a collaborative editor")
    @SecuredAction(value = "collaborativeeditor.read", type = ActionType.RESOURCE)
    public void session(HttpServerRequest request) {
        etherpadHelper.createSession(request);
    }

    @Get("/deleteSession/:id")
    @ApiDoc("Allows to delete a session")
    @SecuredAction(value = "collaborativeeditor.read", type = ActionType.RESOURCE)
    public void deleteSession(HttpServerRequest request) {
        etherpadHelper.deleteSession(request);
    }

    @Override
    @Put("/:id")
    @ApiDoc("Allows to update a collaborative editor associated to the given identifier")
    @SecuredAction(value = "collaborativeeditor.contrib", type = ActionType.RESOURCE)
    public void update(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, pathPrefix + "collaborativeeditor", new Handler<JsonObject>() {
            @Override
            public void handle(JsonObject event) {
                CollaborativeEditorController.super.update(request);
            }
        });
    }

    @Override
    @Delete("/:id")
    @ApiDoc("Allows to delete a collaborative editor associated to the given identifier")
    @SecuredAction(value = "collaborativeeditor.manager", type = ActionType.RESOURCE)
    public void delete(HttpServerRequest request) {
        etherpadHelper.delete(request);
    }

    @Get("/share/json/:id")
    @ApiDoc("Allows to get the current sharing of the collaborative editor given by its identifier")
    @SecuredAction(value = "collaborativeeditor.manager", type = ActionType.RESOURCE)
    public void share(HttpServerRequest request) {
        shareJson(request, false);
    }

    @Put("/share/json/:id")
    @ApiDoc("Allows to update the current sharing of the collaborative editor given by its identifier")
    @SecuredAction(value = "collaborativeeditor.manager", type = ActionType.RESOURCE)
    public void shareCollaborativeEditorSubmit(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    final String id = request.params().get("id");
                    if (id == null || id.trim().isEmpty()) {
                        badRequest(request);
                        return;
                    }
                    JsonObject params = new JsonObject();
                    params.putString("uri", container.config().getString("host", "http://localhost:8090") +
                        "/userbook/annuaire#" + user.getUserId() + "#" + user.getType());
                    params.putString("username", user.getUsername());
                    params.putString("collaborativeeditorUri", container.config().getString("host", "http://localhost:8090") +
                        "/collaborativeeditor#/view/" + id);
                    params.putString("resourceUri", params.getString("collaborativeeditorUri"));
                    
                    shareJsonSubmit(request, "collaborativeeditor.share", false, params, "name");
                }
            }
        });
    }

    @Put("/share/remove/:id")
    @ApiDoc("Allows to remove the current sharing of the collaborative editor given by its identifier")
    @SecuredAction(value = "collaborativeeditor.manager", type = ActionType.RESOURCE)
    public void removeShareCollaborativeEditor(HttpServerRequest request) {
        removeShare(request, false);
    }

}
