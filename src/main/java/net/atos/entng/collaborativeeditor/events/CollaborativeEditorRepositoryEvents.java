package net.atos.entng.collaborativeeditor.events;

import com.mongodb.QueryBuilder;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.webutils.I18n;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import net.atos.entng.collaborativeeditor.helpers.EtherpadHelper;
import org.entcore.common.mongodb.MongoDbConf;
import org.entcore.common.user.RepositoryEvents;
import org.etherpad_lite_client.EPLiteClient;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class CollaborativeEditorRepositoryEvents implements RepositoryEvents {

	private static final Logger log = LoggerFactory.getLogger(CollaborativeEditorRepositoryEvents.class);
	private final MongoDb mongo = MongoDb.getInstance();
	private final Vertx vertx;
	private final EtherpadHelper helper;

	public CollaborativeEditorRepositoryEvents(Vertx vertx, EtherpadHelper helper) {
		this.vertx = vertx;
		this.helper = helper;
	}

	private void createExportDirectory(String exportPath, String locale, final Handler<String> handler) {
		final String path = exportPath + File.separator
				+ I18n.getInstance().translate("collaborativeeditor.title", I18n.DEFAULT_DOMAIN, locale);
		vertx.fileSystem().mkdir(path, new Handler<AsyncResult<Void>>() {
			@Override
			public void handle(AsyncResult<Void> event) {
				if (event.succeeded()) {
					handler.handle(path);
				} else {
					log.error("Collaborative Editor : Could not create folder " + exportPath + " - " + event.cause());
					handler.handle(null);
				}
			}
		});
	}

	private void exportFiles(EPLiteClient client, final JsonArray results, String exportPath, Set<String> usedFileName,
			final AtomicBoolean exported, final Handler<Boolean> handler) {
		if (results.isEmpty()) {
			exported.set(true);
			log.info("Collaborative Editor exported successfully to : " + exportPath);
			handler.handle(exported.get());
		} else {
			JsonObject resources = results.getJsonObject(0);
			String fileId = resources.getString("_id");
			String fileName = resources.getString("title");
			if (fileName == null) {
				fileName = resources.getString("name");
			}
			if (fileName != null && fileName.contains("/")) {
				fileName = fileName.replaceAll("/", "-");
			}
			if (!usedFileName.add(fileName)) {
				fileName += "_" + fileId;
			}
			final String filePath = exportPath + File.separator + fileName;
			final String padId = resources.getString("epName");
			vertx.fileSystem().writeFile(filePath, resources.toBuffer(), new Handler<AsyncResult<Void>>() {
				@Override
				public void handle(AsyncResult<Void> event) {
					if (event.succeeded()) {
						exportPad(padId, client, exportPath, new Handler<Void>() {
							@Override
							public void handle(Void v) {
								results.remove(0);
								exportFiles(client, results, exportPath, usedFileName, exported, handler);
							}
						});
					} else {
						log.error("Collaborative Editor : Could not write file " + filePath, event.cause());
						handler.handle(exported.get());
					}
				}
			});
		}
	}

	private void exportPad(String padId, EPLiteClient client, String exportPath, Handler<Void> handler) {
		final String padPath = exportPath + File.separator + "Pad_" + padId;
		client.getText(padId, new Handler<JsonObject>() {
			@Override
			public void handle(JsonObject res) {
				if (res != null && "ok".equals(res.getString("status"))) {
					res.remove("status");
					vertx.fileSystem().writeFile(padPath, res.toBuffer(), new Handler<AsyncResult<Void>>() {
						public void handle(AsyncResult<Void> event) {
							if (event.failed()) {
								log.error("Collaborative Editor : Could not write pad " + padPath);
							}
							handler.handle(null);
						}
					});
				} else {
					log.error("Collaborative Editor : Could not write pad " + padPath);
					handler.handle(null);
				}
			}
		});
	}

	@Override
	public void exportResources(String exportId, String userId, JsonArray g, String exportPath, String locale,
			String host, Handler<Boolean> handler) {
		QueryBuilder findByAuthor = QueryBuilder.start("owner.userId").is(userId);
		QueryBuilder findByShared = QueryBuilder.start().or(QueryBuilder.start("shared.userId").is(userId).get(),
				QueryBuilder.start("shared.groupId").in(g).get());
		QueryBuilder findByAuthorOrShared = QueryBuilder.start().or(findByAuthor.get(), findByShared.get());
		final JsonObject query = MongoQueryBuilder.build(findByAuthorOrShared);
		final AtomicBoolean exported = new AtomicBoolean(false);
		final String collection = MongoDbConf.getInstance().getCollection();
		mongo.find(collection, query, new Handler<Message<JsonObject>>() {
			@Override
			public void handle(Message<JsonObject> event) {
				JsonArray results = event.body().getJsonArray("results");
				if ("ok".equals(event.body().getString("status")) && results != null) {
					createExportDirectory(exportPath, locale, new Handler<String>() {
						@Override
						public void handle(String path) {
							if (path != null) {
								exportFiles(helper.getClientFromHost(host), results, path, new HashSet<String>(), exported, handler);
							} else {
								handler.handle(exported.get());
							}
						}
					});
				} else {
					log.error("Collaborative Editor : Could not proceed query " + query.encode(),
							event.body().getString("message"));
					handler.handle(exported.get());
				}
			}
		});
	}

	@Override
	public void deleteGroups(JsonArray groups) {
	}

	@Override
	public void deleteUsers(JsonArray uers) {
	}
}
