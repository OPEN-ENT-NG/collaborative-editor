package net.atos.entng.collaborativeeditor.events;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.Either.Right;
import org.entcore.common.search.SearchingEvents;
import org.entcore.common.service.SearchService;
import org.etherpad_lite_client.EPLiteClient;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CollaborativeEditorSearchingEvents implements SearchingEvents {

	private static final Logger log = LoggerFactory.getLogger(CollaborativeEditorSearchingEvents.class);
	private SearchService searchService;
	private final EPLiteClient epClient;

	public CollaborativeEditorSearchingEvents(Vertx vertx, SearchService searchService, final EPLiteClient epClient) {
		this.searchService = searchService;
		this.epClient = epClient;
	}

	@Override
	public void searchResource(List<String> appFilters, String userId, JsonArray groupIds, JsonArray searchWords, Integer page, Integer limit, final JsonArray columnsHeader,
							   final String locale, final Handler<Either<String, JsonArray>> handler) {
		if (appFilters.contains(CollaborativeEditorSearchingEvents.class.getSimpleName())) {
			final List<String> returnFields = new ArrayList<String>();
			returnFields.add("name");
			returnFields.add("epName");
			returnFields.add("description");
			returnFields.add("modified");
			returnFields.add("owner.userId");
			returnFields.add("owner.displayName");

			final List<String> searchFields = new ArrayList<String>();
			searchFields.add("name");
			searchFields.add("description");
			searchService.search(userId, groupIds.toList(), returnFields, searchWords.toList(), searchFields, page, limit, new Handler<Either<String, JsonArray>>() {
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
		final List<String> aHeader = columnsHeader.toList();
		final JsonArray traity = new JsonArray();

		final Integer[] callBackCounter = new Integer[]{results.size()};

		for (int i=0;i<results.size();i++) {
			final JsonObject j = results.get(i);
			final JsonObject jr = new JsonObject();
			if (j != null) {
				jr.putString(aHeader.get(0), j.getString("name"));
				jr.putString(aHeader.get(1), j.getString("description", ""));
				this.epClient.getLastEdited(j.getString("epName"), new Handler<JsonObject>() {
					@Override
					public void handle(JsonObject event) {
						if ("ok".equals(event.getString("status"))) {
							callBackCounter[0]--;
							final Object timestamp = event.getField("lastEdited");
							if (timestamp != null) {
								jr.putObject(aHeader.get(2), new JsonObject().putValue("$date",
										new Date(Long.parseLong(timestamp.toString())).getTime()));
							} else {
								jr.putObject(aHeader.get(2), j.getObject("modified"));
							}
							jr.putString(aHeader.get(3), j.getObject("owner").getString("displayName"));
							jr.putString(aHeader.get(4), j.getObject("owner").getString("userId"));
							jr.putString(aHeader.get(5), "/collaborativeeditor#/view/" + j.getString("_id"));
							traity.add(jr);
							if (callBackCounter[0] == 0) {
								handler.handle(new Right<String, JsonArray>(traity));
							}
						} else {
							//log error
						}
					}
				});
			}
		}
	}
}
