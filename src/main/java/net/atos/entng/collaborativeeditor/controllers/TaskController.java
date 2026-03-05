package net.atos.entng.collaborativeeditor.controllers;

import fr.wseduc.rs.Post;
import fr.wseduc.webutils.http.BaseController;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import net.atos.entng.collaborativeeditor.cron.NotUsingPAD;

public class TaskController extends BaseController {
	protected static final Logger log = LoggerFactory.getLogger(TaskController.class);

	final NotUsingPAD notUsingPADTask;

	public TaskController(NotUsingPAD notUsingPADTask) {
		this.notUsingPADTask = notUsingPADTask;
	}

	@Post("api/internal/check/not-using-pad")
	public void checkNotUsingPAD(final HttpServerRequest request) {
		log.info("Triggered check not using pad task");
		notUsingPADTask.handle(0L);
		render(request, null, 202);
	}
}
