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

package net.atos.entng.collaborativeeditor.events;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.Either.Right;
import org.entcore.common.search.SearchingEvents;
import org.entcore.common.service.SearchService;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CollaborativeEditorSearchingEvents implements SearchingEvents {

	private static final Logger log = LoggerFactory.getLogger(CollaborativeEditorSearchingEvents.class);
	private SearchService searchService;

	public CollaborativeEditorSearchingEvents(Vertx vertx, SearchService searchService) {
		this.searchService = searchService;
	}

	@Override
	public void searchResource(List<String> appFilters, String userId, JsonArray groupIds, JsonArray searchWords, Integer page, Integer limit, final JsonArray columnsHeader,
							   final String locale, final Handler<Either<String, JsonArray>> handler) {
		if (appFilters.contains(CollaborativeEditorSearchingEvents.class.getSimpleName())) {
			final List<String> returnFields = new ArrayList<String>();
			returnFields.add("name");
			returnFields.add("description");
			returnFields.add("modified");
			returnFields.add("owner.userId");
			returnFields.add("owner.displayName");

			searchService.search(userId, groupIds.getList(), returnFields, searchWords.getList(), page, limit, new Handler<Either<String, JsonArray>>() {
				@Override
				public void handle(Either<String, JsonArray> event) {
					if (event.isRight()) {
						formatSearchResult(event.right().getValue(), columnsHeader, handler);
					} else {
						handler.handle(new Either.Left<String, JsonArray>(event.left().getValue()));
					}
					if (log.isDebugEnabled()) {
						log.debug("[CollaborativeEditorSearchingEvents][searchResource] The resources searched by user are finded");
					}
				}
			});
		} else {
			handler.handle(new Right<String, JsonArray>(new JsonArray()));
		}
	}


	private void formatSearchResult(final JsonArray results, final JsonArray columnsHeader, final Handler<Either<String, JsonArray>> handler) {
		final List<String> aHeader = columnsHeader.getList();
		final JsonArray traity = new JsonArray();

		if (results.size() == 0) {
			handler.handle(new Right<String, JsonArray>(traity));
		} else {
			for (int i = 0; i < results.size(); i++) {
				final JsonObject j = results.getJsonObject(i);
				final JsonObject jr = new JsonObject();
				if (j != null) {
					jr.put(aHeader.get(0), j.getString("name"));
					jr.put(aHeader.get(1), j.getString("description", ""));
					jr.put(aHeader.get(2), j.getJsonObject("modified"));
					jr.put(aHeader.get(3), j.getJsonObject("owner").getString("displayName"));
					jr.put(aHeader.get(4), j.getJsonObject("owner").getString("userId"));
					jr.put(aHeader.get(5), "/collaborativeeditor#/view/" + j.getString("_id"));
					traity.add(jr);
				}
			}
			handler.handle(new Right<String, JsonArray>(traity));
		}
	}
}
