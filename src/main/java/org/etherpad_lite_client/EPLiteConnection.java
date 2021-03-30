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

package org.etherpad_lite_client;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.utils.StringUtils;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * Connection object for talking to and parsing responses from the Etherpad Lite Server.
 */
public class EPLiteConnection {
    private static final Logger log = LoggerFactory.getLogger(EPLiteConnection.class);
    public static final int CODE_OK = 0;
    public static final int CODE_INVALID_PARAMETERS = 1;
    public static final int CODE_INTERNAL_ERROR = 2;
    public static final int CODE_INVALID_METHOD = 3;
    public static final int CODE_INVALID_API_KEY = 4;

    /**
     * The url of the API
     */
    public final URI uri;

    /**
     * The API key
     */
    public final String apiKey;

    /**
     * The Etherpad Lite API version
     */
    public final String apiVersion;

    private final HttpClient httpClient;

    /**
     * Initializes a new org.etherpad_lite_client.EPLiteConnection object.
     * @param vertx vertx
     * @param url an absolute url, including protocol, to the EPL api
     * @param apiKey the API Key
     * @param apiVersion the API version
     */
    public EPLiteConnection(Vertx vertx, final String url, String apiKey, String apiVersion, Boolean trustAll, JsonObject config) {
        final Optional<String> internalUrlOpt = Optional.ofNullable(config.getString("internal-uri"));
        final JsonArray domains = config.getJsonArray("domains", new JsonArray());
        final Optional<String> foundDomain = domains.stream().filter(e -> e instanceof JsonObject).map(e -> (JsonObject)e).filter(e -> {
            return url.equals(e.getString("etherpad-url")) && e.containsKey("internal-uri");
        }).map(e -> e.getString("internal-uri")).findFirst();
        final String internalUrl = foundDomain.orElse(internalUrlOpt.orElse(""));
        if(!StringUtils.isEmpty(internalUrl)){
            log.info("Use internal pad uri: "+ internalUrl);
            this.uri = URI.create(internalUrl);
        }else{
            this.uri = URI.create(url);
        }
        log.info("Pad pool zie : "+config.getInteger("max-pool-size", 16));
        this.apiKey = apiKey;
        this.apiVersion = apiVersion;
        final int port = (uri.getPort() > 0) ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80);
        final HttpClientOptions options = new HttpClientOptions()
            .setDefaultHost(uri.getHost())
            .setDefaultPort(port)
            .setMaxPoolSize(config.getInteger("max-pool-size", 16))
            .setConnectTimeout(config.getInteger("connect-timeout", 10000))
            .setVerifyHost(config.getBoolean("verify-host", false))
            .setKeepAlive(config.getBoolean("keep-alive", false))
            .setSsl("https".equals(uri.getScheme()))
            // fixme Warning jvm knows no AC, trusted parameter used, but MITM attacks are feasible
            .setTrustAll(trustAll);
        this.httpClient = vertx.createHttpClient(options);
    }

    /**
     * GETs from the HTTP JSON API.
     */
    public void get(String apiMethod, final Handler<JsonObject> handler) {
        this.get(apiMethod, new HashMap(), handler);
    }

    /**
     * GETs from the HTTP JSON API.
     */
    public void get(String apiMethod, HashMap apiArgs, final Handler<JsonObject> handler) {
        String path = this.apiPath(apiMethod);
        String query = this.queryString(apiArgs);
        URL url = apiUrl(path, query);

        this.callGet(url, handler);
    }

    /**
     * POSTs to the HTTP JSON API.
     */
    public void post(String apiMethod, final Handler<JsonObject> handler) {
        this.post(apiMethod, new HashMap(), new HashMap(), handler);
    }

    /**
     * POSTs to the HTTP JSON API.
     */
    public void post(String apiMethod, HashMap getArgs, final Handler<JsonObject> handler) {
        this.post(apiMethod, getArgs, new HashMap(), handler);
    }

    /**
     * POSTs to the HTTP JSON API.
     */
    public void post(String apiMethod, HashMap getArgs, HashMap postArgs, final Handler<JsonObject> handler) {
        String path = this.apiPath(apiMethod);
        String query = this.queryString(getArgs);
        URL url = apiUrl(path, query);

        this.callPost(url, postArgs, handler);
    }

    /**
     * Calls the HTTP JSON API.
     * FIXME Post call doesn't work due to unauthorized error (I have simulate query with another client and the result is the same)
     * FIXME Perhaps etherpad-lite API don't support POST http verb
     */
    private void callPost(final URL url, HashMap postArgs, final Handler<JsonObject> handler) {
        HttpClientRequest req = httpClient.post(url.toString(), new Handler<HttpClientResponse>() {
            @Override
            public void handle(final HttpClientResponse response) {
                parseData(response, handler);
            }
        });
        JsonObject body = new JsonObject(postArgs);
        req.putHeader("Content-Type", "application/json; charset=utf-8");
        req.end(body.toString());
    }

    /**
     * Calls the HTTP JSON API.
     */
    private void callGet(final URL url, final Handler<JsonObject> handler) {
        HttpClientRequest req = httpClient.get(url.toString(), new Handler<HttpClientResponse>() {
            @Override
            public void handle(final HttpClientResponse response) {
                parseData(response, handler);
            }
        });
        req.end();
    }

    private void parseData(HttpClientResponse response, final Handler<JsonObject> handler) {
        if (response.statusCode() == 200) {
            final Buffer buff = Buffer.buffer();
            response.handler(new Handler<Buffer>() {
                @Override
                public void handle(Buffer event) {
                    buff.appendBuffer(event);
                }
            });
            response.endHandler(new Handler<Void>() {
                @Override
                public void handle(Void end) {
                    handleResponse(buff.toString(), handler);
                }
            });
        } else {
            handler.handle(new JsonObject().put("status", "error").put("message", response.statusMessage()));
        }
    }

    /**
     * Converts the API resonse's JSON string into a HashMap.
     */
    private void handleResponse(String jsonString,  final Handler<JsonObject> handler) {
        try {
            JSONParser parser = new JSONParser();
            Map response = (Map) parser.parse(jsonString);
            // Act on the response code
            if (!response.get("code").equals(null)) {
                int code = ((Long) response.get("code")).intValue();
                switch (code) {
                    // Valid code, parse the response
                    case CODE_OK:
                        final HashMap datas = (HashMap) response.get("data");
                        handler.handle(new JsonObject((datas != null) ? datas : new HashMap<String, Object>()).put("status", "ok"));
                        break;
                        // Invalid code, indicate the error message
                    case CODE_INVALID_PARAMETERS:
                        handler.handle(new JsonObject().put("status", "error").put("message", "CODE_INVALID_PARAMETERS : " + (String)response.get("message")));
                        break;
                    case CODE_INTERNAL_ERROR:
                        handler.handle(new JsonObject().put("status", "error").put("message", "CODE_INTERNAL_ERROR : " + (String)response.get("message")));
                        break;
                    case CODE_INVALID_API_KEY:
                        handler.handle(new JsonObject().put("status", "error").put("message", "CODE_INVALID_API_KEY : " + (String)response.get("message")));
                        break;
                    case CODE_INVALID_METHOD:
                        handler.handle(new JsonObject().put("status", "error").put("message", "CODE_INVALID_METHOD : " + (String)response.get("message")));
                        break;
                    default:
                        handler.handle(new JsonObject().put("status", "error").put("message",
                                "An unknown error has occurred while handling the response: " + jsonString));
                }
                // No response code, something's really wrong
            } else {
                handler.handle(new JsonObject().put("status", "error").put("message",
                        "An unknown error has occurred while handling the response: " + jsonString));
            }
        } catch (ParseException e) {
            log.error("Unable to parse JSON response (" + jsonString + ")" + e);
            handler.handle(new JsonObject().put("status", "error").put("message",
                    "Unable to parse JSON response (" + jsonString + "): " + e.getMessage()));
        }
    }

    /**
     * Returns the URL for the api path and query.
     */
    private URL apiUrl(String path, String query) {
        try {
            URL url = new URL(new URI(this.uri.getScheme(), null, this.uri.getHost(), this.uri.getPort(), path, query, null).toString());
            return url;
        } catch (Exception e) {
            log.error("Unable to connect to Etherpad Lite instance (" + e.getClass() + ")", e);
        }
        return null;
    }

    /**
     * Returns a URI path for the API method
     * @param apiMethod the api method
     * @return String
     */
    private String apiPath(String apiMethod) {
        return this.uri.getPath() + "/api/" + this.apiVersion + "/" + apiMethod;
    }

    /**
     * Returns a query string made from HashMap keys and values
     * @param apiArgs the api arguments in a HashMap
     * @return String
     */
    private String queryString(HashMap apiArgs) {
        String strArgs = "";
        apiArgs.put("apikey", this.apiKey);
        Iterator i = apiArgs.entrySet().iterator();
        while (i.hasNext()) {
            Map.Entry e = (Map.Entry) i.next();
            Object value = e.getValue();
            if (value != null) {
                strArgs += e.getKey() + "=" + value;
                if (i.hasNext()) {
                    strArgs += "&";
                }
            }
        }
        return strArgs;
    }
}
