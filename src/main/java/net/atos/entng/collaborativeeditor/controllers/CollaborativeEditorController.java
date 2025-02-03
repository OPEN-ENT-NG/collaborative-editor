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

package net.atos.entng.collaborativeeditor.controllers;

import fr.wseduc.rs.*;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.request.RequestUtils;
import net.atos.entng.collaborativeeditor.CollaborativeEditor;
import net.atos.entng.collaborativeeditor.explorer.CollaborativeEditorExplorerPlugin;
import net.atos.entng.collaborativeeditor.helpers.EtherpadHelper;
import org.entcore.common.events.EventHelper;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.mongodb.MongoDbControllerHelper;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import io.vertx.core.json.JsonObject;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Controller to manage URL paths for collaborative editors.
 * @author AtoS
 */
public class CollaborativeEditorController extends MongoDbControllerHelper {

    private final EtherpadHelper etherpadHelper;
    private final EventHelper eventHelper;

    private CollaborativeEditorExplorerPlugin explorerPlugin;

    /**
     * Default constructor
     * @param collection MongoDB collection to request.
     * @param etherpadHelper Etherpad Helper.
     * @param explorerPlugin Plugin Explorer.
     */
    public CollaborativeEditorController(
            String collection
            , EtherpadHelper etherpadHelper
            , final CollaborativeEditorExplorerPlugin explorerPlugin) {
        super(collection);
        this.etherpadHelper = etherpadHelper;
        final EventStore eventStore = EventStoreFactory.getFactory().getEventStore(CollaborativeEditor.class.getSimpleName());
        this.eventHelper = new EventHelper(eventStore);
        this.explorerPlugin = explorerPlugin;
    }

    @Override
    public void init(Vertx vertx, JsonObject config, RouteMatcher rm, Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, config, rm, securedActions);
        final Map<String, List<String>> groupedActions = new HashMap<>();
        this.shareService = this.explorerPlugin.createShareService(groupedActions);
    }

    @Override
    protected Function<JsonObject, Optional<String>> jsonToOwnerId() {
        return json -> this.explorerPlugin.getCreatorForModel(json).map(UserInfos::getUserId);
    }

    @Get("")
    @SecuredAction("collaborativeeditor.view")
    public void view(HttpServerRequest request) {
        // Create event "access to application Collaborative Editor" and store it, for module "statistics"
        eventHelper.onAccess(request);

        // Get the view parameter from the request
        final String view = request.params().get("view");
        // Get the use-explorer-ui configuration from the config
        final boolean useNewUi = this.config.getBoolean("use-explorer-ui", true);
        // If the view is home, use the old ui by default
        if ("home".equals(view)) {
            if (useNewUi) {
                // use new ui by default
                renderView(request, new JsonObject(), "collaborativeeditor-explorer.html", null);
            } else {
                // use old ui by default
                renderView(request);
            }
        } else if ("resource".equals(view)) {
            // force new ui
            renderView(request, new JsonObject(), "collaborativeeditor-explorer.html", null);
        } else {
            // use old ui by default for routing
            renderView(request);
        }
    }

    @Override
    @Get("/list/all")
    @ApiDoc("Allows to list all the editors")
    @SecuredAction("collaborativeeditor.list")
    public void list(HttpServerRequest request) {
        etherpadHelper.list(request);
    }

    @Override
    @Get("/:id")
    @ApiDoc("Allows to get a collaborative editor associated to the given identifier")
    @SecuredAction(value = "collaborativeeditor.read", type = ActionType.RESOURCE)
    public void retrieve(HttpServerRequest request) {
        etherpadHelper.retrieve(request);
    }

    @Override
    @Post("")
    @ApiDoc("Allows to create a new editor")
    @SecuredAction("collaborativeeditor.create")
    public void create(final HttpServerRequest request) {
        RequestUtils.bodyToJson(
                request
                , pathPrefix + "collaborativeeditor"
                , event -> etherpadHelper.create(request));
    }

    @Override
    @Put("/:id")
    @ApiDoc("Allows to update a collaborative editor associated to the given identifier")
    @SecuredAction(value = "collaborativeeditor.contrib", type = ActionType.RESOURCE)
    public void update(final HttpServerRequest request) {
        UserUtils.getAuthenticatedUserInfos(eb, request)
                .onSuccess(user ->
                        RequestUtils.bodyToJson(
                                request
                                , pathPrefix + "collaborativeeditor"
                                , padData -> {
                                    CollaborativeEditorController.super.update(request); // this method renders the response
                                    // Notify Explorer
                                    padData.put("_id", request.params().get("id"));
                                    padData.put("version", System.currentTimeMillis());
                                    explorerPlugin.notifyUpsert(user, padData);
                                }))
                .onFailure(e -> unauthorized(request));
    }

    @Override
    @Delete("/:id")
    @ApiDoc("Allows to delete a collaborative editor associated to the given identifier")
    @SecuredAction(value = "collaborativeeditor.manager", type = ActionType.RESOURCE)
    public void delete(HttpServerRequest request) {
        etherpadHelper.delete(request);
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
                    params.put("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType());
                    params.put("username", user.getUsername());
                    params.put("collaborativeeditorUri", "/collaborativeeditor#/view/" + id);
                    params.put("resourceUri", params.getString("collaborativeeditorUri"));

                    JsonObject pushNotif = new JsonObject()
                            .put("title", "collaborativeeditor.push-notif.share")
                            .put("body", I18n.getInstance()
                                .translate("collaborativeeditor.push-notif.share.body",
                                        getHost(request),
                                        I18n.acceptLanguage(request),
                                        user.getUsername()
                                ));

                    params.put("pushNotif", pushNotif);

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

    @Put("/share/resource/:id")
    @ApiDoc("Allows to get the current sharing of the collaborative editor given by its identifier")
    @SecuredAction(value = "collaborativeeditor.manager", type = ActionType.RESOURCE)
    public void shareResource(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    final String id = request.params().get("id");
                    if(id == null || id.trim().isEmpty()) {
                        badRequest(request, "invalid.id");
                        return;
                    }

                    JsonObject params = new JsonObject();
                    params.put("uri", "/userbook/annuaire#" + user.getUserId() + "#" + user.getType());
                    params.put("username", user.getUsername());
                    params.put("collaborativeeditorUri", "/collaborativeeditor#/view/" + id);
                    params.put("resourceUri", params.getString("collaborativeeditorUri"));

                    JsonObject pushNotif = new JsonObject()
                            .put("title", "collaborativeeditor.push-notif.share")
                            .put("body", I18n.getInstance()
                                    .translate("collaborativeeditor.push-notif.share.body",
                                            getHost(request),
                                            I18n.acceptLanguage(request),
                                            user.getUsername()
                                    ));

                    params.put("pushNotif", pushNotif);

                    shareResource(request, "collaborativeeditor.share", false, params, "name");
                }
            }
        });
    }


}
