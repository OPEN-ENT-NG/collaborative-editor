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

package net.atos.entng.collaborativeeditor.helpers;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.Utils;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.CookieHelper;
import fr.wseduc.webutils.request.RequestUtils;
import net.atos.entng.collaborativeeditor.CollaborativeEditor;
import org.entcore.common.events.EventHelper;
import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.entcore.common.mongodb.MongoDbControllerHelper;
import org.entcore.common.service.CrudService;
import org.entcore.common.service.VisibilityFilter;
import org.entcore.common.service.impl.MongoDbCrudService;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.entcore.common.utils.StringUtils;
import org.etherpad_lite_client.EPLiteClient;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;

public class EtherpadHelper extends MongoDbControllerHelper {
    static final String RESOURCE_NAME = "pad";
    /**
     * Class logger
     */
    private static final Logger log = LoggerFactory.getLogger(EtherpadHelper.class);

    /**
     * Etherpad client
     */
    private final Map<String,EPLiteClient> clientByDomain = new HashMap();


    /**
     * Mongo CRUD service
     */
    private final CrudService etherpadCrudService;
    private final EventHelper eventHelper;

    /**
     * Constructor
     * @param vertx vertx
     * @param collection Mongo collection to request
     * @param urlByDomain configuration array
     * @param etherpadUrl Etherpad service URL
     * @param etherpadApiKey Etherpad API key
     * @param trustAll trust all
     * @param domain domain
     */
    public EtherpadHelper(Vertx vertx, String collection, JsonArray urlByDomain, String etherpadUrl, String etherpadApiKey, Boolean trustAll, String domain) {
        super(collection);
        final EventStore eventStore = EventStoreFactory.getFactory().getEventStore(CollaborativeEditor.class.getSimpleName());
        this.eventHelper = new EventHelper(eventStore);
        this.etherpadCrudService = new MongoDbCrudService(collection);

        if (StringUtils.isEmpty(etherpadApiKey)) {
            log.error("[Collaborative Editor] Error : Module property 'etherpad-api-key' must be defined");
        }

        if (urlByDomain != null && !urlByDomain.isEmpty()) {
            for (Object o : urlByDomain.getList()) {
                if (o == null || !(o instanceof JsonObject)) continue;
                JsonObject conf = (JsonObject) o;

                final String padDomain = conf.getString("etherpad-domain", null);
                if (StringUtils.isEmpty(padDomain)) {
                    log.error("[Collaborative Editor] Error : Module property 'etherpad-domain' must be defined");
                }

                final String padUrl = conf.getString("etherpad-url", null);
                if (StringUtils.isEmpty(padUrl)) {
                    log.error("[Collaborative Editor] Error : Module property 'etherpad-url' must be defined for " + padDomain);
                }

                clientByDomain.put(padDomain,  new EPLiteClient(vertx, padUrl, etherpadApiKey, trustAll));
            }
        } else {
            if (StringUtils.isEmpty(etherpadUrl)) {
                log.error("[Collaborative Editor] Error : Module property 'etherpad-url' must be defined");
            }

            if (StringUtils.isEmpty(domain)) {
                log.error("[Collaborative Editor] Error : Module property 'etherpad-domain' must be defined");
            }

            clientByDomain.put(domain,  new EPLiteClient(vertx, etherpadUrl, etherpadApiKey, trustAll));
        }
    }

    @Override
    public void create(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    final String text = I18n.getInstance().translate("collaborativeeditor.welcome", getHost(request), I18n.acceptLanguage(request));

                    createPad(getAuthDomain(request), text, new Handler<JsonObject>()
                    {
                        @Override
                        public void handle(JsonObject event)
                        {
                            if ("ok".equals(event.getString("status")) == false)
                                Renders.renderError(request, event);
                            else
                            {
                                RequestUtils.bodyToJson(request, new Handler<JsonObject>()
                                {
                                    @Override
                                    public void handle(JsonObject object)
                                    {
                                        object.put("epName", event.getString("epName"));
                                        object.put("epGroupID", event.getString("epGroupID"));
                                        object.put("locale", I18n.acceptLanguage(request));
                                        final Handler<Either<String,JsonObject>> handler = notEmptyResponseHandler(request);
                                        etherpadCrudService.create(object, user, eventHelper.onCreateResource(request, RESOURCE_NAME, handler));
                                    }
                                });
                            }
                        }
                    });
                } else {
                    log.debug("User not found in session.");
                    Renders.unauthorized(request);
                }
            }
        });
    }

    public void createPad(final String host, final Handler<JsonObject> handler) {
        createPad(host, null, handler);
    }

    public void createPad(final String host, final String text, final Handler<JsonObject> handler) {
        final String randomName = UUID.randomUUID().toString();
        final EPLiteClient client = clientByDomain.get(getAuthDomain(host));
        if(client == null)
        {
            handler.handle(new JsonObject().put("status", "error").put("message", "no.pad.client"));
            return;
        }
        client.createGroup(new Handler<JsonObject>()
        {
            @Override
            public void handle(JsonObject event)
            {
                if ("ok".equals(event.getString("status")))
                {
                    final String groupID = event.getString("groupID");
                    client.createGroupPad(groupID, randomName, text, new Handler<JsonObject>()
                    {
                        @Override
                        public void handle(JsonObject event)
                        {
                            if ("ok".equals(event.getString("status")))
                            {
                                final String padName = event.getString("padID");
                                handler.handle(
                                    new JsonObject()
                                    .put("status", "ok")
                                    .put("epName", padName)
                                    .put("epGroupID", groupID)
                                );
                            } else {
                                handler.handle(event);
                            }
                        }
                    });
                } else {
                    handler.handle(event);
                }
            }
        });
    }

    public void setPadText(final String host, final String padId, final String text, final Handler<JsonObject> handler)
    {
        final EPLiteClient client = clientByDomain.get(getAuthDomain(host));
        client.setText(padId, text, handler);
    }

    public void setPadHTML(final String host, final String padId, final String html, final Handler<JsonObject> handler)
    {
        final EPLiteClient client = clientByDomain.get(getAuthDomain(host));
        client.setHTML(padId, html, handler);
    }

    public void getPadHTML(final String host, final String padId, final Handler<JsonObject> handler)
    {
        final EPLiteClient client = clientByDomain.get(getAuthDomain(host));
        client.getHTML(padId, handler);
    }

    @Override
    public void retrieve(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                String id = request.params().get("id");
                etherpadCrudService.retrieve(id, user, new Handler<Either<String, JsonObject>>() {
                    @Override
                    public void handle(Either<String, JsonObject> event) {
                        if (event.isRight()) {
                            if (event.right().getValue() != null && event.right().getValue().size() > 0) {
                                final JsonObject object = event.right().getValue();
                                final String domain = getAuthDomain(request);
                                final EPLiteClient client = clientByDomain.get(domain);
                                // Create author if he doesn't exists
                                client.createAuthorIfNotExistsFor(user.getLogin(), new Handler<JsonObject>() {
                                    @Override
                                    public void handle(JsonObject event) {
                                        if ("ok".equals(event.getString("status"))) {
                                            final String authorID = event.getString("authorID");
                                            // Create session for the user on the pad group
                                            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Paris"));
                                            Date now = calendar.getTime();
                                            final long validUntil = (now.getTime() + (2 * 60L * 60L * 1000L)) / 1000L;
                                            client.createSession(object.getString("epGroupID"), authorID, validUntil, new Handler<JsonObject>() {
                                                @Override
                                                public void handle(JsonObject event) {
                                                    if ("ok".equals(event.getString("status"))) {
                                                        final String session = event.getString("sessionID");
                                                        request.response().putHeader("Set-Cookie", "sessionID=" + session + ";max-age=" + 2 * 360 * 1000 + ";path=/;domain=" + domain);
                                                        object.put("url", client.getPadUrl() + "/p/" + object.getString("epName"));
                                                        object.remove("epGroupID");
                                                        object.remove("epName");

                                                        Renders.renderJson(request, object, 200);
                                                    } else {
                                                        Renders.renderError(request, event);
                                                    }
                                                }
                                            });
                                        } else {
                                            Renders.renderError(request, event);
                                        }
                                    }
                                });
                            } else {
                                request.response().setStatusCode(404).end();
                            }
                        } else {
                            JsonObject error = new JsonObject().put("error", event.left().getValue());
                            Renders.renderJson(request, error, 400);
                        }
                    }
                });
            }
        });
    }

    @Override
    public void list(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                final String filter = request.params().get("filter");
                VisibilityFilter v = VisibilityFilter.ALL;
                if (filter != null) {
                    try {
                        v = VisibilityFilter.valueOf(filter.toUpperCase());
                    } catch (IllegalArgumentException | NullPointerException e) {
                        v = VisibilityFilter.ALL;
                        if (log.isDebugEnabled()) {
                            log.debug("Invalid filter " + filter);
                        }
                    }
                }

                final String userDisplayName = user.getUsername();

                etherpadCrudService.list(v, user, new Handler<Either<String, JsonArray>>() {
                    @Override
                    public void handle(Either<String, JsonArray> event) {
                        if (event.isRight()) {
                            final JsonArray objects = event.right().getValue();
                            final AtomicInteger callCount = new AtomicInteger(objects.size());

                            final String language = Utils.getOrElse(I18n.acceptLanguage(request), "fr", false);

                            for (int i=0;i<objects.size();i++) {
                                final JsonObject jsonObject = objects.getJsonObject(i);
                                final EPLiteClient client = clientByDomain.get(getAuthDomain(request));
                                client.getReadOnlyID(jsonObject.getString("epName"), new Handler<JsonObject>() {
                                    @Override
                                    public void handle(JsonObject event) {
                                        if ("ok".equals(event.getString("status"))) {
                                            final String readOnlyId = event.getString("readOnlyID");

                                            try {
                                                final String urlReadOnlyStr = client.getPadUrl() + "/p/" + readOnlyId + "?userName=" + userDisplayName + "&lang=" + language;
                                                final URL urlReadOnly = new URL(urlReadOnlyStr);
                                                final URI uriReadOnly = new URI(urlReadOnly.getProtocol(), urlReadOnly.getUserInfo(), urlReadOnly.getHost(), urlReadOnly.getPort(), urlReadOnly.getPath(), urlReadOnly.getQuery(), urlReadOnly.getRef());
                                                jsonObject.put("readOnlyUrl", uriReadOnly.toASCIIString());

                                                final String urlStr = client.getPadUrl() + "/p/" + jsonObject.getString("epName") + "?userName=" + userDisplayName + "&lang=" + language;
                                                final URL url = new URL(urlStr);
                                                final URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());
                                                jsonObject.put("url", uri.toASCIIString());
                                            } catch (MalformedURLException | URISyntaxException e) {
                                                log.error("Can't generate etherpad-lite url", e);
                                            }
                                            jsonObject.remove("epName");
                                            jsonObject.remove("epGroupID");

                                            if (callCount.decrementAndGet() == 0) {
                                                Renders.renderJson(request, objects);
                                                return;
                                            }
                                        } else {
                                            //only log the error if the mongo entry don't link with a real pad
                                            log.error(event.getString("message"));
                                            if (callCount.decrementAndGet() == 0) {
                                                Renders.renderJson(request, objects);
                                                return;
                                            }
                                        }
                                    }
                                });
                            }
                        } else {
                            JsonObject error = new JsonObject().put("error", event.left().getValue());
                            Renders.renderJson(request, error, 400);
                        }
                    }
                });
            }
        });
    }

    /**
     * Create a user session on a collaborative editor
     * @param request HTTP request
     */
    public void createSession(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                String id = request.params().get("id");
                etherpadCrudService.retrieve(id, user, new Handler<Either<String, JsonObject>>() {

                    @Override
                    public void handle(Either<String, JsonObject> event) {
                        if (event.isRight()) {
                            if (event.right().getValue() != null && event.right().getValue().size() > 0) {
                                final JsonObject object = event.right().getValue();
                                final String domain = getAuthDomain(request);
                                final EPLiteClient client = clientByDomain.get(domain);
                                // Create author if he doesn't exists
                                client.createAuthorIfNotExistsFor(user.getLogin(), new Handler<JsonObject>() {
                                    @Override
                                    public void handle(JsonObject event) {
                                        if ("ok".equals(event.getString("status"))) {
                                            final String authorID = event.getString("authorID");
                                            // Create session for the user on the pad group
                                            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Paris"));
                                            calendar.setTime(new Date());
                                            Date now = calendar.getTime();
                                            long validUntil = (now.getTime() + (1 * 60L * 60L * 1000L)) / 1000L;
                                            client.createSession(object.getString("epGroupID"), authorID, validUntil, new Handler<JsonObject>() {
                                                @Override
                                                public void handle(JsonObject event) {
                                                    if ("ok".equals(event.getString("status"))) {
                                                        final String session = event.getString("sessionID");
                                                        request.response().putHeader("Set-Cookie", "sessionID=" + session + ";max-age=" + 360 * 1000 + ";path=/;domain=" + domain).setStatusCode(200).end();
                                                    } else {
                                                        Renders.renderError(request, event);
                                                    }
                                                }
                                            });
                                        } else {
                                            Renders.renderError(request, event);
                                        }
                                    }
                                });
                            } else {
                                request.response().setStatusCode(404).end();
                            }
                        } else {
                            JsonObject error = new JsonObject().put("error", event.left().getValue());
                            Renders.renderJson(request, error, 400);
                        }
                    }
                });
            }
        });
    }

    /**
     * Delete a user session on a collaborative editor
     * @param request HTTP request
     */
    public void deleteSession(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                String sessionID = CookieHelper.get("sessionID", request);
                if (sessionID != null) {
                    final String domain = getAuthDomain(request);
                    final EPLiteClient client = clientByDomain.get(domain);
                    client.deleteSession(sessionID, new Handler<JsonObject>() {
                        @Override
                        public void handle(JsonObject event) {
                            if ("ok".equals(event.getString("status"))) {
                                request.response().putHeader("Set-Cookie", "sessionID=deleted;max-age=-1;path=/;domain=" + domain).setStatusCode(200).end();
                            } else {
                                //TODO check if render error because there is a redmine ticket about error log on session doesn't exist !!!!!
                                Renders.renderError(request, event);
                            }
                        }
                    });
                } else {
                    request.response().setStatusCode(200).end();
                }
            }
        });
    }

    @Override
    public void delete(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    final String id = request.params().get("id");
                    etherpadCrudService.retrieve(id, user, new Handler<Either<String, JsonObject>>() {

                        @Override
                        public void handle(Either<String, JsonObject> event) {
                            if (event.isRight()) {
                                if (event.right().getValue() != null && event.right().getValue().size() > 0) {
                                    final JsonObject object = event.right().getValue();
                                    etherpadCrudService.delete(id, user, new Handler<Either<String, JsonObject>>() {
                                        @Override
                                        public void handle(Either<String, JsonObject> event) {
                                            if (event.isRight()) {
                                                final EPLiteClient client = clientByDomain.get(getAuthDomain(request));
                                                client.deletePad(object.getString("epName"), new Handler<JsonObject>() {
                                                    @Override
                                                    public void handle(JsonObject event) {
                                                        if (!"ok".equals(event.getString("status"))) {
                                                            log.error("Fail to delete a pad on backend " + event.getString("message"));
                                                        }
                                                    }
                                                });
                                                client.deleteGroup(object.getString("epGroupID"), new Handler<JsonObject>() {
                                                    @Override
                                                    public void handle(JsonObject event) {
                                                        if (!"ok".equals(event.getString("status"))) {
                                                            log.error("Fail to delete a group on backend " + event.getString("message"));
                                                        }
                                                    }
                                                });
                                                Renders.renderJson(request, event.right().getValue(), 200);
                                            } else {
                                                log.error("Fail to delete a pad on mongo backend from id : " + id + ", error : " + event.left().getValue());
                                                Renders.renderError(request, new JsonObject().put("error", event.left().getValue()));
                                            }
                                        }
                                    });
                                } else {
                                    Renders.renderError(request, new JsonObject().put("error", "Empty result from id : " + id));
                                }
                            } else {
                                Renders.renderError(request, new JsonObject().put("error", event.left().getValue()));
                            }
                        }
                    });
                } else {
                    log.debug("User not found in session.");
                    Renders.unauthorized(request);
                }
            }
        });
    }

    private static String getAuthDomain(final String host) {
        String domain = "";

        final List<String> levels = StringUtils.split(StringUtils.split(host, ":").get(0), "\\.");
        if (levels.size() > 2) {
            for (int i=levels.size()-2;i<levels.size();i++) {
                domain += levels.get(i) + ".";
            }
            domain = domain.substring(0, domain.length() - 1);
        } else {
            domain = host;
        }

        return domain;
    }

    private static String getAuthDomain(final HttpServerRequest request) {
        final String host = StringUtils.trimToBlank(Renders.getHost(request));
        return getAuthDomain(host);
    }

    public EPLiteClient getFirstClient() {
        return clientByDomain.values().iterator().next();
    }

    public EPLiteClient getClientFromHost(final String host) {
        String[] s = host.split("://");
        String hostname = s.length > 1 ? s[1] : s[0];
        final String domain = getAuthDomain(hostname);
        return clientByDomain.get(domain);
    }
}