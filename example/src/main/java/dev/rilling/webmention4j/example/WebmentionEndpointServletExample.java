package dev.rilling.webmention4j.example;

import dev.rilling.webmention4j.server.AbstractWebmentionEndpointServlet;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Slf4jRequestLogWriter;
import org.eclipse.jetty.servlet.ServletHandler;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public final class WebmentionEndpointServletExample {
	private static final Logger LOGGER = LoggerFactory.getLogger(WebmentionEndpointServletExample.class);

	private static final int PORT = 8080;

	private WebmentionEndpointServletExample() {
	}

	public static void main(String[] args) {
		Server server = new Server(PORT);
		server.setRequestLog(new CustomRequestLog(new Slf4jRequestLogWriter(), CustomRequestLog.EXTENDED_NCSA_FORMAT));

		ServletHandler servletHandler = new ServletHandler();
		servletHandler.addServletWithMapping(LoggingWebmentionEndpointServlet.class, "/endpoint");
		server.setHandler(servletHandler);

		try {
			server.start();
			LOGGER.info("Listening on port {}.", PORT);
		} catch (Exception e) {
			LOGGER.error("Unexpected error.", e);
		}
	}

	@SuppressWarnings("serial")
	private static class LoggingWebmentionEndpointServlet extends AbstractWebmentionEndpointServlet {

		@Override
		protected void handleSubmission(@NotNull URI source, @NotNull URI target) {
			LOGGER.info("Received submission from source '{}'  with target '{}'.", source, target);
		}
	}

}