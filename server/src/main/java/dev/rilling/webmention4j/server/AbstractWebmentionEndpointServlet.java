package dev.rilling.webmention4j.server;

import dev.rilling.webmention4j.common.Webmention;
import dev.rilling.webmention4j.common.util.HttpUtils;
import dev.rilling.webmention4j.server.impl.VerificationService;
import dev.rilling.webmention4j.server.impl.verifier.HtmlVerifier;
import dev.rilling.webmention4j.server.impl.verifier.JsonVerifier;
import dev.rilling.webmention4j.server.impl.verifier.TextVerifier;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serial;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.function.Supplier;

/**
 * Servlet handling receiving Webmentions.
 * <p>
 * Serialization of this servlet is NOT supported.
 */
// Spec: '3.2 Receiving Webmentions'
@SuppressWarnings("serial") // Inspired by springs DispatcherServlet. Servlet serialization seems extremely uncommon.
public abstract class AbstractWebmentionEndpointServlet extends HttpServlet {

	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractWebmentionEndpointServlet.class);

	private static final ContentType EXPECTED_CONTENT_TYPE = ContentType.APPLICATION_FORM_URLENCODED;

	private final Supplier<CloseableHttpClient> httpClientFactory;
	private final VerificationService verificationService;

	private CloseableHttpClient httpClient;

	protected AbstractWebmentionEndpointServlet() {
		this(AbstractWebmentionEndpointServlet::createDefaultHttpClient,
			new VerificationService(List.of(new HtmlVerifier(), new TextVerifier(), new JsonVerifier())));
	}

	private AbstractWebmentionEndpointServlet(@NotNull Supplier<CloseableHttpClient> httpClientFactory,
											  @NotNull VerificationService verificationService) {
		this.httpClientFactory = httpClientFactory;
		this.verificationService = verificationService;
	}

	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init(config);

		httpClient = httpClientFactory.get();
	}

	@Override
	public void destroy() {
		super.destroy();

		try {
			httpClient.close();
		} catch (IOException e) {
			LOGGER.warn("Could not close HTTP client.", e);
		}
	}

	@Override
	protected final void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		try {
			processRequest(req);
		} catch (BadRequestException e) {
			LOGGER.warn("Bad request.", e);
			resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
			return;
		}

		/*
		 * Spec:
		 * 'If the receiver chooses to process the request and perform the verification step synchronously (not
		 * recommended), it MUST respond with a 200 OK status on success.'
		 * */
		resp.setStatus(HttpServletResponse.SC_OK);
	}

	/**
	 * Allows servlet consumer to react to a successfully accepted Webmention.
	 *
	 * @param webmention The received Webmention.
	 */
	protected abstract void handleWebmention(@NotNull Webmention webmention);

	private void processRequest(@NotNull HttpServletRequest req) throws BadRequestException {
		if (!EXPECTED_CONTENT_TYPE.isSameMimeType(ContentType.parse(req.getContentType()))) {
			throw new BadRequestException("Content type must be '%s'.".formatted(EXPECTED_CONTENT_TYPE.getMimeType()));
		}

		Webmention webmention = extractWebmention(req);

		// TODO: allow configuration of allowed target URI hosts.

		LOGGER.debug("Received Webmention request '{}'.", webmention);

		// TODO: perform check async
		/*
		 * Spec:
		 * 'If the receiver is going to use the Webmention in some way, (displaying it as a comment on a post,
		 * incrementing a "like" counter, notifying the author of a post), then it MUST perform an HTTP GET request
		 * on source [...] to confirm that it actually mentions the target.
		 */
		try {
			if (verificationService.isWebmentionValid(httpClient, webmention)) {
				LOGGER.debug("Webmention request '{}' passed verification.", webmention);
			} else {
				throw new BadRequestException("Source does not contain link to target URL.");
			}
		} catch (IOException e) {
			// In theory I/O failures cold also be issues on our side (e.g. trusted CAs being wrong), but
			// differentiating between those and issues on the source URIs side (e.g. 404s) seems hard.
			throw new BadRequestException("Verification of source URL could not be performed.", e);
		} catch (VerificationService.UnsupportedContentTypeException e) {
			throw new BadRequestException(
				"Verification of source URL failed due to no supported content type being served.",
				e);
		}

		handleWebmention(webmention);
	}

	private @NotNull Webmention extractWebmention(@NotNull HttpServletRequest req) throws BadRequestException {
		URI source = extractParameterAsUri(req, "source");
		URI target = extractParameterAsUri(req, "target");

		// Spec: 'The receiver MUST reject the request if the source URL is the same as the target URL.'
		if (source.equals(target)) {
			throw new BadRequestException("Source and target URL must not be identical.");
		}
		return new Webmention(source, target);
	}

	private @NotNull URI extractParameterAsUri(@NotNull HttpServletRequest req, @NotNull String parameterName)
		throws BadRequestException {
		/*
		 * Spec:
		 * 'The receiver MUST check that source and target are valid URLs
		 * and are of schemes that are supported by the receiver.'
		 */
		String parameter = req.getParameter(parameterName);
		if (parameter == null) {
			throw new BadRequestException("Required parameter '%s' is missing.".formatted(parameterName));
		}

		URI uri;
		try {
			uri = new URI(parameter);
		} catch (URISyntaxException e) {
			throw new BadRequestException("Invalid URL syntax: '%s'.".formatted(parameter), e);
		}
		if (!verificationService.isUriSchemeSupported(uri)) {
			throw new BadRequestException("URL scheme '%s' is not supported.".formatted(uri.getScheme()));
		}
		return uri;
	}

	@NotNull
	private static CloseableHttpClient createDefaultHttpClient() {
		return HttpClients.custom()
			.setUserAgent(HttpUtils.createUserAgentString("webmention4j-server",
				AbstractWebmentionEndpointServlet.class.getPackage()))
			.build();
	}

	private static final class BadRequestException extends Exception {

		@Serial
		private static final long serialVersionUID = -8108083179786850494L;

		/**
		 * @param message User-facing error message.
		 */
		BadRequestException(String message) {
			super(message);
		}

		/**
		 * @param message User-facing error message.
		 * @param cause   Exception cause.
		 */
		BadRequestException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
