package org.etherpad_lite_client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLConnection;

/**
 * A class for easily executing an HTTP POST request.<br />
 * <br />
 * Example:<br />
 * <br />
 * <code>
 * Request req = new POSTRequest(url_object);<br />
 * String resp = req.send();<br />
 * </code>
 */
public class POSTRequest implements Request {
    /**
     * The URL object.
     */
    private URL url;

    /**
     * Instantiates a new POSTRequest.
     * @param url the URL object
     */
    private String args;

    public POSTRequest(URL url, String args) {
        this.url = url;
        this.args = args;
    }

    /**
     * Sends the request and returns the response.
     * @return String
     */
    @Override
    public String send() throws Exception {
        URLConnection con = this.url.openConnection();
        con.setDoOutput(true);

        OutputStreamWriter out = new OutputStreamWriter(con.getOutputStream());
        out.write(this.args);
        out.close();

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String response = "";
        String buffer;
        while ((buffer = in.readLine()) != null) {
            response += buffer;
        }
        in.close();
        return response;
    }
}
