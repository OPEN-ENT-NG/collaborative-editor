package net.atos.entng.collaborativeeditor.events;

import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.mongodb.MongoQueryBuilder;
import fr.wseduc.webutils.I18n;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import net.atos.entng.collaborativeeditor.helpers.EtherpadHelper;
import org.bson.conversions.Bson;
import org.entcore.common.folders.impl.DocumentHelper;
import org.entcore.common.mongodb.MongoDbConf;
import org.entcore.common.service.impl.MongoDbRepositoryEvents;
import org.entcore.common.user.ExportResourceResult;
import org.entcore.common.utils.FileUtils;
import org.entcore.common.utils.StringUtils;
import org.etherpad_lite_client.EPLiteClient;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mongodb.client.model.Filters.*;

public class CollaborativeEditorRepositoryEvents extends MongoDbRepositoryEvents {

	private static final Logger log = LoggerFactory.getLogger(CollaborativeEditorRepositoryEvents.class);
	private final MongoDb mongo = MongoDb.getInstance();
	private final Vertx vertx;
	private final EtherpadHelper helper;

	private final Map<String, Map<String, JsonObject>> oldPadsToNewPads = new ConcurrentHashMap<String, Map<String, JsonObject>>();

	public CollaborativeEditorRepositoryEvents(Vertx vertx, EtherpadHelper helper)
	{
		super(vertx, "net-atos-entng-collaborativeeditor-controllers-CollaborativeEditorController|delete", null, null);
		this.vertx = vertx;
		this.helper = helper;
	}

	@Override
	protected void createExportDirectory(String exportPath, String locale, final Handler<String> handler)
	{
		final String path = exportPath + File.separator
				+ I18n.getInstance().translate("collaborativeeditor.title", I18n.DEFAULT_DOMAIN, locale);

		vertx.fileSystem().mkdir(path, new Handler<AsyncResult<Void>>()
		{
			@Override
			public void handle(AsyncResult<Void> event)
			{
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
			final AtomicBoolean exported, final Handler<Boolean> handler)
	{
		if (results.isEmpty())
		{
			exported.set(true);
			log.info("Collaborative Editor exported successfully to : " + exportPath);
			handler.handle(exported.get());
		}
		else
		{
			JsonObject resources = results.getJsonObject(0);
			String fileId = resources.getString("_id");
			String fileName = resources.getString("title");

			if (fileName == null) {
				fileName = resources.getString("name");
			}

			fileName = StringUtils.replaceForbiddenCharacters(fileName);

			if (!usedFileName.add(fileName)) {
				fileName += "_" + fileId;
			}

			final String filePath = exportPath + File.separator + fileName;
			final String padId = resources.getString("epName");

			vertx.fileSystem().writeFile(filePath, resources.toBuffer(), new Handler<AsyncResult<Void>>()
			{
				@Override
				public void handle(AsyncResult<Void> event)
				{
					if (event.succeeded())
					{
						exportPad(padId, client, exportPath, new Handler<Void>()
						{
							@Override
							public void handle(Void v)
							{
								results.remove(0);
								exportFiles(client, results, exportPath, usedFileName, exported, handler);
							}
						});
					}
					else
					{
						log.error("Collaborative Editor : Could not write file " + filePath, event.cause());
						handler.handle(exported.get());
					}
				}
			});
		}
	}

	private void exportPad(String padId, EPLiteClient client, String exportPath, Handler<Void> handler)
	{
		final String padPath = exportPath + File.separator + "Pad_" + padId;

		client.getHTML(padId, new Handler<JsonObject>()
		{
			@Override
			public void handle(JsonObject res)
			{
				if (res != null && "ok".equals(res.getString("status")))
				{
					res.remove("status");

					vertx.fileSystem().writeFile(padPath, res.toBuffer(), new Handler<AsyncResult<Void>>()
					{
						public void handle(AsyncResult<Void> event)
						{
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
	public void exportResources(JsonArray resourcesIds, boolean exportDocuments, boolean exportSharedResources, String exportId, String userId,
			JsonArray g, String exportPath, String locale, String host, Handler<ExportResourceResult> handler)
	{
		Bson findByAuthor = eq("owner.userId", userId);

		Bson findByShared = or(
			eq("shared.userId", userId),
			in("shared.groupId", g)
		);

		Bson findByAuthorOrShared = exportSharedResources == false ? findByAuthor : or(findByAuthor, findByShared);

		JsonObject query;

		if(resourcesIds == null)
			query = MongoQueryBuilder.build(findByAuthorOrShared);
		else
		{
			Bson limitToResources = and(findByAuthorOrShared, in("_id", resourcesIds));
			query = MongoQueryBuilder.build(limitToResources);
		}

		final AtomicBoolean exported = new AtomicBoolean(false);
		final String collection = MongoDbConf.getInstance().getCollection();

		mongo.find(collection, query, new Handler<Message<JsonObject>>()
		{
			@Override
			public void handle(Message<JsonObject> event)
			{
				JsonArray results = event.body().getJsonArray("results");
				if ("ok".equals(event.body().getString("status")) && results != null)
				{
					createExportDirectory(exportPath, locale, new Handler<String>()
					{
						@Override
						public void handle(String path)
						{
							if (path != null)
							{
								exportFiles(helper.getClientFromHost(host), results, path, new HashSet<>(), exported, e -> new ExportResourceResult(e, path));
							}
							else
							{
								handler.handle(ExportResourceResult.KO);
							}
						}
					});
				}
				else
				{
					log.error("Collaborative Editor : Could not proceed query " + query.encode(),
							event.body().getString("message"));
					handler.handle(ExportResourceResult.KO);
				}
			}
		});
	}

	@Override
	protected JsonObject transformDocumentBeforeImport(JsonObject document, String collectionName,
		String importId, String userId, String userLogin, String userName)
	{
		Map<String, JsonObject> padMap = oldPadsToNewPads.get(importId);

		if(padMap != null)
		{
			JsonObject newPad = padMap.get(DocumentHelper.getAppProperty(document, "epName"));

			if(newPad != null)
			{
				DocumentHelper.setAppProperty(document, "epName", newPad.getString("epName"));
				DocumentHelper.setAppProperty(document, "epGroupID", newPad.getString("epGroupID"));
				return document;
			}
		}

		// Don't import the pad if we can't find the new pad
		return null;
	}

	@Override
	protected boolean filterMongoDocumentFile(String filePath, Buffer fileContents)
	{
		return FileUtils.getFilename(filePath).startsWith("Pad_") == false;
	}

	@Override
	public void importResources(String importId, String userId, String userLogin, String userName, String importPath,
		String locale, String host, boolean forceImportAsDuplication, Handler<JsonObject> handler)
	{
		CollaborativeEditorRepositoryEvents self = this;
		this.fs.readDir(importPath, new Handler<AsyncResult<List<String>>>()
		{
			@Override
			public void handle(AsyncResult<List<String>> result)
			{
				if(result.succeeded() == false)
					handler.handle(new JsonObject().put("status", "error").put("message", "Failed to read import folder"));
				else
				{
					List<String> filesInDir = result.result();
					int nbFiles = filesInDir.size();

					AtomicInteger unprocessed = new AtomicInteger(nbFiles);
					AtomicInteger nbErrors = new AtomicInteger(0);

					Map<String, JsonObject> padMap = new ConcurrentHashMap<String, JsonObject>();
					oldPadsToNewPads.put(importId, padMap);

					Handler<Void> finaliseRead = new Handler<Void>()
					{
						@Override
						public void handle(Void res)
						{
							CollaborativeEditorRepositoryEvents.super.importResources(importId, userId, userLogin, userName, importPath, locale, host, forceImportAsDuplication,
								new Handler<JsonObject>()
							{
								@Override
								public void handle(JsonObject rapport)
								{
									rapport.put("errorsNumber", Integer.toString(Integer.parseInt(rapport.getString("errorsNumber")) + nbErrors.get()));

									handler.handle(rapport);
								}
							});
						}
					};

					for(String filePath : filesInDir)
					{
						String fileName = FileUtils.getFilename(filePath);

						// Only read pads
						if(fileName.startsWith("Pad_") == true)
						{
							self.fs.readFile(filePath, new Handler<AsyncResult<Buffer>>()
							{
								@Override
								public void handle(AsyncResult<Buffer> fileResult)
								{
									if(fileResult.succeeded() == false)
									{
										int ix = unprocessed.decrementAndGet();

										nbErrors.addAndGet(1);
										log.error("Failed to read pad file " + filePath);

										if(ix == 0)
											finaliseRead.handle(null);
									}
									else
									{
										String padId = fileName.substring("Pad_".length());
										String padHtml = fileResult.result().toJsonObject().getString("html", "");
										String padText = fileResult.result().toJsonObject().getString("text", null);

										// Create a new pad in EtherPad
										helper.createPad(host, new Handler<JsonObject>()
										{
											@Override
											public void handle(JsonObject padResult)
											{
												if(padResult.getString("status").equals("ok") == false)
												{
													nbErrors.addAndGet(1);

													int ix = unprocessed.decrementAndGet();
													if(ix == 0)
														finaliseRead.handle(null);
												}
												else
												{
													padMap.put(padId, padResult);
													Handler<JsonObject> hnd = new Handler<JsonObject>()
													{
														@Override
														public void handle(JsonObject textRes)
														{
															int ix = unprocessed.decrementAndGet();
															if(ix == 0)
																finaliseRead.handle(null);
														}
													};

													if(padText != null && padHtml.isEmpty() == true)
														helper.setPadText(host, padResult.getString("epName"), padText, hnd);
													else
														helper.setPadHTML(host, padResult.getString("epName"), padHtml, hnd);
												}
											}
										});
									}
								}
							});
						}
						else
						{
							int ix = unprocessed.decrementAndGet();
							if(ix == 0)
								finaliseRead.handle(null);
						}
					}
				}
			}
		});
	}
}
