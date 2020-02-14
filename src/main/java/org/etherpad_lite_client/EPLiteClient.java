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
import io.vertx.core.json.JsonObject;

import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.util.Date;
import java.util.HashMap;

/**
 * A non-blocking client for talking to Etherpad Lite's HTTP JSON API.<br />
 */
public class EPLiteClient {
    /**
     * The Etherpad Lite API version this client targets by default
     */
    public static final String DEFAULT_API_VERSION = "1.2.1";

    public static final Boolean DEFAULT_TRUST_ALL_CERTIFICATE = false;

    /**
     * The connection object
     */
    public EPLiteConnection connection;

    /**
     * padUrl
     */
    private String padUrl;

    /**
     * Initializes a new org.etherpad_lite_client.EPLiteClient object. The default Etherpad Lite API version (in
     * DEFAULT_API_VERSION) will be used.
     */
    public EPLiteClient(Vertx vertx, String url, String apiKey) {
        this(vertx, url, apiKey, DEFAULT_API_VERSION, DEFAULT_TRUST_ALL_CERTIFICATE);
    }

    /**
     * Initializes a new org.etherpad_lite_client.EPLiteClient object. The specified Etherpad Lite trust SSL will be
     * used.
     */
    public EPLiteClient(Vertx vertx, String url, String apiKey, Boolean trustAll) {
        this(vertx, url, apiKey, DEFAULT_API_VERSION, trustAll);
    }

    /**
     * Initializes a new org.etherpad_lite_client.EPLiteClient object. The specified Etherpad Lite API version will be
     * used.
     */
    public EPLiteClient(Vertx vertx, String url, String apiKey, String apiVersion, Boolean trustAll) {
        this.connection = new EPLiteConnection(vertx, url, apiKey, apiVersion, trustAll);
        this.padUrl = url;
    }

    // Groups
    // Pads may belong to a group. These pads are not considered "public", and won't be available through the Web UI
    // without a session.

    /**
     * Creates a new Group. The group id is returned in "groupID" in the HashMap.
     */
    public void createGroup(final Handler<JsonObject> handler) {
        this.connection.get("createGroup", handler);
    }

    /**
     * Creates a new Group for groupMapper if one doesn't already exist. Helps you map your application's groups to
     * Etherpad Lite's groups. The group id is returned in "groupID" in the HashMap.
     */
    public void createGroupIfNotExistsFor(String groupMapper, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("groupMapper", groupMapper);
        this.connection.post("createGroupIfNotExistsFor", args, handler);
    }

    /**
     * Delete group.
     */
    public void deleteGroup(String groupID, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("groupID", groupID);
        this.connection.post("deleteGroup", args, handler);
    }

    /**
     * List all the padIDs in a group. They will be in an array inside "padIDs".
     */
    public void listPads(String groupID, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("groupID", groupID);
        this.connection.get("listPads", args, handler);
    }

    /**
     * Create a pad in this group.
     */
    public void createGroupPad(String groupID, String padName, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("groupID", groupID);
        args.put("padName", padName);
        this.connection.get("createGroupPad", args, handler);
    }

    /**
     * Create a pad in this group.
     */
    public void createGroupPad(String groupID, String padName, String text, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("groupID", groupID);
        args.put("padName", padName);
        if (text != null) {
            args.put("text", text);
        }
        this.connection.get("createGroupPad", args, handler);
    }

    /**
     * Lists all existing groups. The group ids are returned in "groupIDs".
     */
    public void listAllGroups(final Handler<JsonObject> handler) {
        this.connection.get("listAllGroups", handler);
    }

    // Authors
    // These authors are bound to the attributes the users choose (color and name). The author id is returned in
    // "authorID".

    /**
     * Create a new author.
     */
    public void createAuthor(final Handler<JsonObject> handler) {
        this.connection.post("createAuthor", handler);
    }

    /**
     * Create a new author with the given name. The author id is returned in "authorID".
     */
    public void createAuthor(String name, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("name", name);
        this.connection.post("createAuthor", args, handler);
    }

    /**
     * Creates a new Author for authorMapper if one doesn't already exist. Helps you map your application's authors to
     * Etherpad Lite's authors. The author id is returned in "authorID".
     */
    public void createAuthorIfNotExistsFor(String authorMapper, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("authorMapper", authorMapper);
        this.connection.post("createAuthorIfNotExistsFor", args, handler);
    }

    /**
     * Creates a new Author for authorMapper if one doesn't already exist. Helps you map your application's authors to
     * Etherpad Lite's authors. The author id is returned in "authorID".
     */
    public void createAuthorIfNotExistsFor(String authorMapper, String name, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("authorMapper", authorMapper);
        args.put("name", name);
        this.connection.post("createAuthorIfNotExistsFor", args, handler);
    }

    /**
     * List the ids of pads the author has edited. They will be in an array inside "padIDs".
     */
    public void listPadsOfAuthor(String authorId, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("authorID", authorId);
        this.connection.get("listPadsOfAuthor", args, handler);
    }

    /**
     * Returns the Author Name of the author.
     */
    public void getAuthorName(String authorId, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("authorID", authorId);
        this.connection.get("getAuthorName", args, handler);
    }

    // Sessions
    // Sessions can be created between a group and an author. This allows an author to access more than one group. The
    // sessionID will be set as a
    // cookie to the client and is valid until a certain date. Only users with a valid session for this group, can
    // access group pads. You can create a
    // session after you authenticated the user at your web application, to give them access to the pads. You should
    // save the sessionID of this session
    // and delete it after the user logged out.

    /**
     * Create a new session for the given author in the given group, valid until the given UNIX time. The session id
     * will be returned in "sessionID".<br />
     */
    public void createSession(String groupID, String authorID, long validUntil, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("groupID", groupID);
        args.put("authorID", authorID);
        args.put("validUntil", String.valueOf(validUntil));
        this.connection.post("createSession", args, handler);
    }

    /**
     * Create a new session for the given author in the given group valid for the given number of hours. The session id
     * will be returned in "sessionID".<br />
     */
    public void createSession(String groupID, String authorID, int length, final Handler<JsonObject> handler) {
        long inNHours = ((new Date()).getTime() + (length * 60L * 60L * 1000L)) / 1000L;
        this.createSession(groupID, authorID, inNHours, handler);
    }

    /**
     * Create a new session for the given author in the given group, valid until the given datetime. The session id will
     * be returned in "sessionID".<br />
     */
    public void createSession(String groupID, String authorID, Date validUntil, final Handler<JsonObject> handler) {
        long seconds = validUntil.getTime() / 1000L;
        this.createSession(groupID, authorID, seconds, handler);
    }

    /**
     * Delete a session.
     */
    public void deleteSession(String sessionID, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("sessionID", sessionID);
        this.connection.post("deleteSession", args, handler);
    }

    /**
     * Returns information about a session: authorID, groupID and validUntil.
     */
    public void getSessionInfo(String sessionID, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("sessionID", sessionID);
        this.connection.get("getSessionInfo", args, handler);
    }

    /**
     * List all the sessions IDs in a group. Returned as a HashMap of sessionIDs keys, with values of HashMaps
     * containing groupID, authorID, and validUntil.
     */
    public void listSessionsOfGroup(String groupID, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("groupID", groupID);
        this.connection.get("listSessionsOfGroup", args, handler);
    }

    /**
     * List all the sessions IDs belonging to an author. Returned as a HashMap of sessionIDs keys, with values of
     * HashMaps containing groupID, authorID, and validUntil.
     */
    public void listSessionsOfAuthor(String authorID, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("authorID", authorID);
        this.connection.get("listSessionsOfAuthor", args, handler);
    }

    // Pad content

    /**
     * Returns a list of all pads.
     */
    public void listAllPads(final Handler<JsonObject> handler) {
        this.connection.get("listAllPads", handler);
    }

    /**
     * Returns a HashMap containing the latest revision of the pad's text. The text is stored under "text".
     */
    public void getText(String padId, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        this.connection.get("getText", args, handler);
    }

    /**
     * Returns a HashMap containing the a specific revision of the pad's text. The text is stored under "text".
     */
    public void getText(String padId, int rev, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        args.put("rev", new Integer(rev));
        this.connection.get("getText", args, handler);
    }

    /**
     * Creates a new revision with the given text (or creates a new pad).
     */
    public void setText(String padId, String text, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        HashMap postArgs = new HashMap();
        postArgs.put("text", text);
        this.connection.post("setText", args, postArgs, handler);
    }

    /**
     * Returns a HashMap containing the current revision of the pad's text as HTML. The html is stored under "html".
     */
    public void getHTML(String padId, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        this.connection.get("getHTML", args, handler);
    }

    /**
     * Returns a HashMap containing the a specific revision of the pad's text as HTML. The html is stored under "html".
     */
    public void getHTML(String padId, int rev, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        args.put("rev", new Integer(rev));
        this.connection.get("getHTML", args, handler);
    }

    /**
     * Creates a new revision with the given html (or creates a new pad).
     */
    public void setHTML(String padId, String html, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        HashMap postArgs = new HashMap();
        postArgs.put("html", html);
        this.connection.post("setHTML", args, postArgs, handler);
    }

    // Pads
    // Group pads are normal pads, but with the name schema GROUPID$PADNAME. A security manager controls access of them
    // and its
    // forbidden for normal pads to include a $ in the name.

    /**
     * Create a new pad.
     */
    public void createPad(String padId, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        this.connection.post("createPad", args, handler);
    }

    /**
     * Create a new pad with the given initial text.
     */
    public void createPad(String padId, String text, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        HashMap postArgs = new HashMap();
        postArgs.put("text", text);
        this.connection.post("createPad", args, postArgs, handler);
    }

    /**
     * Returns the number of revisions of this pad. The number is in "revisions".
     */
    public void getRevisionsCount(String padId, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        this.connection.get("getRevisionsCount", args, handler);
    }

    /**
     * List the ids of authors who have edited a pad. They will be in an array inside "authorIDs".
     */
    public void listAuthorsOfPad(String padId, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        this.connection.get("listAuthorsOfPad", args, handler);
    }

    /**
     * Deletes a pad.
     */
    public void deletePad(String padId, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        this.connection.post("deletePad", args, handler);
    }

    /**
     * Get the pad's read-only id. The id will be in "readOnlyID".
     */
    public void getReadOnlyID(String padId, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        this.connection.get("getReadOnlyID", args, handler);
    }

    /**
     * Get the pad's last edit date as a Unix timestamp. The timestamp will be in "lastEdited".
     */
    public void getLastEdited(String padId, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        this.connection.get("getLastEdited", args, handler);
    }

    /**
     * Get the number of users currently editing a pad.
     */
    public void padUsersCount(String padId, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        this.connection.get("padUsersCount", args,handler);
    }

    /**
     * Returns the list of users that are currently editing this pad. A padUser has the values: "colorId", "name" and
     * "timestamp".
     */
    public void padUsers(String padId, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        this.connection.get("padUsers", args, handler);
    }

    /**
     * Sets the pad's public status. This is only applicable to group pads.
     */
    public void setPublicStatus(String padId, Boolean publicStatus, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        args.put("publicStatus", publicStatus);
        this.connection.post("setPublicStatus", args, handler);
    }

    /**
     * Gets the pad's public status. The boolean is in "publicStatus". This is only applicable to group pads.<br />
     */
    public void getPublicStatus(String padId, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        this.connection.get("getPublicStatus", args, handler);
    }

    /**
     * Sets the pad's password. This is only applicable to group pads.
     */
    public void setPassword(String padId, String password, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        args.put("password", password);
        this.connection.post("setPassword", args, handler);
    }

    /**
     * Checks whether the pad is password-protected or not. The boolean is in "isPasswordProtected". This is only
     * applicable to group pads.<br />
     */
    public void isPasswordProtected(String padId, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        this.connection.get("isPasswordProtected", args, handler);
    }

    /**
     * Sends a custom message of type msg to the pad.
     */
    public void sendClientsMessage(String padId, String msg, final Handler<JsonObject> handler) {
        HashMap args = new HashMap();
        args.put("padID", padId);
        args.put("msg", msg);
        this.connection.post("sendClientsMessage", args, handler);
    }

    /**
     * Returns true if the connection is using SSL/TLS, false if not.
     */
    public boolean isSecure() {
        if (this.connection.uri.getPort() == 443) {
            return true;
        } else {
            return false;
        }
    }

    public String getPadUrl() {
        return this.padUrl;
    }
}
