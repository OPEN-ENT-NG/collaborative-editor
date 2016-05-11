package net.atos.entng.collaborativeeditor.helpers;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.CookieHelper;
import fr.wseduc.webutils.request.RequestUtils;
import org.entcore.common.mongodb.MongoDbControllerHelper;
import org.entcore.common.service.CrudService;
import org.entcore.common.service.VisibilityFilter;
import org.entcore.common.service.impl.MongoDbCrudService;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.etherpad_lite_client.EPLiteClient;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.UUID;

import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;

public class EtherpadHelper extends MongoDbControllerHelper {

    /**
     * Class logger
     */
    private static final Logger log = LoggerFactory.getLogger(EtherpadHelper.class);

    /**
     * Etherpad client
     */
    private final EPLiteClient client;

    /**
     * Etherpad public URL
     */
    private final String etherpadPublicUrl;

    /**
     * Etherpad domain for authentication cookie into collaborative-editor and pad service
     */
    private final String domain;

    /**
     * Mongo CRUD service
     */
    private final CrudService etherpadCrudService;

    /**
     * Constructor
     * @param collection Mongo collection to request
     * @param etherpadUrl Etherpad service internal URL
     * @param etherpadApiKey Etherpad API key
     * @param etherpadPublicUrl Etherpad service public URL
     */
    public EtherpadHelper(Vertx vertx, String collection, String etherpadUrl, String etherpadApiKey, String etherpadPublicUrl, String domain) {
        super(collection);
        this.etherpadCrudService = new MongoDbCrudService(collection);
        if (null == etherpadUrl || etherpadUrl.trim().isEmpty()) {
            log.error("[Collaborative Editor] Error : Module property 'etherpad-url' must be defined");
        }
        if (null == etherpadApiKey || etherpadApiKey.trim().isEmpty()) {
            log.error("[Collaborative Editor] Error : Module property 'etherpad-api-key' must be defined");
        }
        if (null == etherpadPublicUrl || etherpadPublicUrl.trim().isEmpty()) {
            this.etherpadPublicUrl = etherpadUrl;
            log.error("[Collaborative Editor] Warning : Module property 'etherpad-public-url' is not defined. Using 'etherpad-url'...");
        } else {
            this.etherpadPublicUrl = etherpadPublicUrl;
        }

        if (null == domain || domain.trim().isEmpty()) {
            log.error("[Collaborative Editor] Error : Module property 'etherpad-domain' must be defined");
        }

        this.domain = domain;

        this.client = new EPLiteClient(vertx, etherpadUrl, etherpadApiKey);

    }

    @Override
    public void create(final HttpServerRequest request) {
        UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
            @Override
            public void handle(final UserInfos user) {
                if (user != null) {
                    final String randomName = UUID.randomUUID().toString();
                    client.createGroup(new Handler<JsonObject>() {
                        @Override
                        public void handle(JsonObject event) {
                            if ("ok".equals(event.getField("status"))) {
                                final String groupID = event.getString("groupID");
                                client.createGroupPad(groupID, randomName, new Handler<JsonObject>() {
                                    @Override
                                    public void handle(JsonObject event) {
                                        if ("ok".equals(event.getField("status"))) {
                                            final String padName = event.getString("padID");
                                            RequestUtils.bodyToJson(request, new Handler<JsonObject>() {
                                                @Override
                                                public void handle(JsonObject object) {
                                                    object.putString("epName", padName);
                                                    object.putString("epGroupID", groupID);
                                                    object.putString("locale", I18n.acceptLanguage(request));
                                                    etherpadCrudService.create(object, user, notEmptyResponseHandler(request));
                                                }
                                            });
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
                    log.debug("User not found in session.");
                    Renders.unauthorized(request);
                }
            }
        });
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
                                // Create author if he doesn't exists
                                client.createAuthorIfNotExistsFor(user.getLogin(), new Handler<JsonObject>() {
                                    @Override
                                    public void handle(JsonObject event) {
                                        if ("ok".equals(event.getField("status"))) {
                                            final String authorID = event.getString("authorID");
                                            // Create session for the user on the pad group
                                            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Paris"));
                                            Date now = calendar.getTime();
                                            final long validUntil = (now.getTime() + (2 * 60L * 60L * 1000L)) / 1000L;
                                            client.createSession(object.getString("epGroupID"), authorID, validUntil, new Handler<JsonObject>() {
                                                @Override
                                                public void handle(JsonObject event) {
                                                    if ("ok".equals(event.getField("status"))) {
                                                        final String session = event.getString("sessionID");
                                                        request.response().putHeader("Set-Cookie", "sessionID=" + session + ";max-age=" + 2 * 360 * 1000 + ";path=/;domain=" + domain);

                                                        object.putString("url", etherpadPublicUrl + "/p/" + object.getString("epName"));
                                                        object.removeField("epGroupID");
                                                        object.removeField("epName");

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
                            JsonObject error = new JsonObject().putString("error", event.left().getValue());
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
                String filter = request.params().get("filter");
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

                etherpadCrudService.list(v, user, new Handler<Either<String, JsonArray>>() {
                    @Override
                    public void handle(Either<String, JsonArray> event) {
                        if (event.isRight()) {
                            final JsonArray objects = event.right().getValue();
                            final int count = objects.size();

                            for (int i=0;i<count;i++) {
                                final JsonObject jsonObject = (JsonObject) objects.get(i);
                                final int current = i+1;
                                client.getReadOnlyID(jsonObject.getString("epName"), new Handler<JsonObject>() {
                                    @Override
                                    public void handle(JsonObject event) {
                                        if ("ok".equals(event.getString("status"))) {
                                            final String readOnlyId = event.getString("readOnlyID");
                                            String userDisplayName = user.getUsername();
                                            try {
                                                String urlReadOnlyStr = etherpadPublicUrl + "/p/" + readOnlyId + "?userName=" + userDisplayName;
                                                URL urlReadOnly = new URL(urlReadOnlyStr);
                                                URI uriReadOnly = new URI(urlReadOnly.getProtocol(), urlReadOnly.getUserInfo(), urlReadOnly.getHost(), urlReadOnly.getPort(), urlReadOnly.getPath(), urlReadOnly.getQuery(), urlReadOnly.getRef());
                                                jsonObject.putString("readOnlyUrl", uriReadOnly.toASCIIString());

                                                String urlStr = etherpadPublicUrl + "/p/" + jsonObject.getString("epName") + "?userName=" + userDisplayName;
                                                URL url = new URL(urlStr);
                                                URI uri = new URI(url.getProtocol(), url.getUserInfo(), url.getHost(), url.getPort(), url.getPath(), url.getQuery(), url.getRef());

                                                jsonObject.putString("url", uri.toASCIIString());
                                                if (current == objects.size()) {
                                                    Renders.renderJson(request, objects);
                                                    return;
                                                }
                                            } catch (MalformedURLException | URISyntaxException e) {
                                                log.error(e);
                                                request.response().setStatusCode(500).end();
                                            }
                                            jsonObject.removeField("epName");
                                            jsonObject.removeField("epGroupID");
                                        } else {
                                            //only log the error if the mongo entry don't link with a real pad
                                            log.error(event.getString("message"));
                                        }
                                    }
                                });
                            }
                        } else {
                            JsonObject error = new JsonObject().putString("error", event.left().getValue());
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

                                // Create author if he doesn't exists
                                client.createAuthorIfNotExistsFor(user.getLogin(), new Handler<JsonObject>() {
                                    @Override
                                    public void handle(JsonObject event) {
                                        if ("ok".equals(event.getField("status"))) {
                                            final String authorID = event.getString("authorID");
                                            // Create session for the user on the pad group
                                            Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Paris"));
                                            calendar.setTime(new Date());
                                            Date now = calendar.getTime();
                                            long validUntil = (now.getTime() + (1 * 60L * 60L * 1000L)) / 1000L;
                                            client.createSession(object.getString("epGroupID"), authorID, validUntil, new Handler<JsonObject>() {
                                                @Override
                                                public void handle(JsonObject event) {
                                                    if ("ok".equals(event.getField("status"))) {
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
                            JsonObject error = new JsonObject().putString("error", event.left().getValue());
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
                    client.deleteSession(sessionID, new Handler<JsonObject>() {
                        @Override
                        public void handle(JsonObject event) {
                            if ("ok".equals(event.getField("status"))) {
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
                                                Renders.renderError(request, new JsonObject().putString("error", event.left().getValue()));
                                            }
                                        }
                                    });
                                } else {
                                    Renders.renderError(request, new JsonObject().putString("error", "Empty result from id : " + id));
                                }
                            } else {
                                Renders.renderError(request, new JsonObject().putString("error", event.left().getValue()));
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
}