package org.etherpad_lite_client;

import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import javax.net.ssl.*;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
    public URI uri;

    /**
     * The API key
     */
    public String apiKey;

    /**
     * The Etherpad Lite API version
     */
    public String apiVersion;

    private final HttpClient httpClient;

    /**
     * Initializes a new org.etherpad_lite_client.EPLiteConnection object.
     * @param vertx vertx
     * @param url an absolute url, including protocol, to the EPL api
     * @param apiKey the API Key
     * @param apiVersion the API version
     */
    public EPLiteConnection(Vertx vertx, String url, String apiKey, String apiVersion) {
        this.uri = URI.create(url);
        this.apiKey = apiKey;
        this.apiVersion = apiVersion;
        final int port = (uri.getPort() > 0) ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80);

        this.httpClient = vertx.createHttpClient()
                .setHost(uri.getHost())
                .setPort(port)
                .setMaxPoolSize(16)
                .setSSL("https".equals(uri.getScheme()))
                .setKeepAlive(false);
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
        this.post(apiMethod, new HashMap(), handler);
    }

    /**
     * POSTs to the HTTP JSON API.
     */
    public void post(String apiMethod, HashMap apiArgs,  final Handler<JsonObject> handler) {
        String path = this.apiPath(apiMethod);
        String query = this.queryString(apiArgs);
        URL url = apiUrl(path, query);

        this.callPost(url, handler);
    }

    /**
     * Calls the HTTP JSON API.
     * FIXME Post call doesn't work due to unauthorized error (I have simulate query with another client and the result is the same)
     * FIXME Perhaps etherpad-lite API don't support POST http verb
     */
    private void callPost(final URL url, final Handler<JsonObject> handler) {
        trustServerAndCertificate();

        HttpClientRequest req = httpClient.get(url.toString(), new Handler<HttpClientResponse>() {
            @Override
            public void handle(final HttpClientResponse response) {
                parseData(response, handler);
            }
        });
        req.end();
    }

    /**
     * Calls the HTTP JSON API.
     */
    private void callGet(final URL url, final Handler<JsonObject> handler) {
        trustServerAndCertificate();

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
            final Buffer buff = new Buffer();
            response.dataHandler(new Handler<Buffer>() {
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
            handler.handle(new JsonObject().putString("status", "error"));
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
                        handler.handle(new JsonObject((datas != null) ? datas : new HashMap<String, Object>()).putString("status", "ok"));
                        break;
                        // Invalid code, throw an exception with the message
                    case CODE_INVALID_PARAMETERS:
                    case CODE_INVALID_API_KEY:
                    case CODE_INVALID_METHOD:
                        handler.handle(new JsonObject().putString("status", "error").putString("message", (String)response.get("message")));
                        break;
                    default:
                        handler.handle(new JsonObject().putString("status", "error").putString("message",
                                "An unknown error has occurred while handling the response: " + jsonString));
                }
                // No response code, something's really wrong
            } else {
                handler.handle(new JsonObject().putString("status", "error").putString("message",
                        "An unknown error has occurred while handling the response: " + jsonString));
            }
        } catch (ParseException e) {
            log.error("Unable to parse JSON response (" + jsonString + ")" + e);
            handler.handle(new JsonObject().putString("status", "error").putString("message",
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

    /**
     * Creates a trust manager to trust all certificates if you open a ssl connection
     */
    private void trustServerAndCertificate() {
        // Create a trust manager that does not validate certificate chains
        TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
            @Override
            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            @Override
            public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
            }

            @Override
            public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
            }
        } };

        // Install the all-trusting trust manager
        try {
            SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
        } catch (Exception e) {
        }

        HostnameVerifier hv = new HostnameVerifier() {
            // @Override
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };
        HttpsURLConnection.setDefaultHostnameVerifier(hv);
    }
}
