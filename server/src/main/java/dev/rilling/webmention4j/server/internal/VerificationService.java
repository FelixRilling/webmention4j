package dev.rilling.webmention4j.server.internal;

import dev.rilling.webmention4j.common.Webmention;
import dev.rilling.webmention4j.common.internal.HttpUtils;
import dev.rilling.webmention4j.common.internal.UriUtils;
import dev.rilling.webmention4j.server.internal.verifier.Verifier;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicHeader;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Serial;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class VerificationService {
	private static final Logger LOGGER = LoggerFactory.getLogger(VerificationService.class);

	private final List<Verifier> verifiers;

	public VerificationService(@NotNull List<Verifier> verifiers) {
		this.verifiers = List.copyOf(verifiers);
	}

	/**
	 * Checks if the URLs scheme allows for validation.
	 */
	public boolean isUriSchemeSupported(@NotNull URI uri) {
		return UriUtils.isHttp(uri);
	}

	/**
	 * Verifies if the source URL mentions the target URL.
	 *
	 * @param httpClient HTTP client.
	 *                   Must be configured to follow redirects.
	 *                   Should be configured to use a fitting UA string.
	 * @param webmention Webmention to verify.
	 * @return if the verification of the Webmention passes,
	 * @throws IOException                     if I/O fails.
	 * @throws UnsupportedContentTypeException if verification cannot be performed due to an unsupported content type.
	 */
	//Spec: https://www.w3.org/TR/webmention/#webmention-verification
	public boolean isWebmentionValid(@NotNull CloseableHttpClient httpClient, @NotNull Webmention webmention)
		throws IOException, UnsupportedContentTypeException {
		/*
		 * Spec:
		 * 'MUST perform an HTTP GET request on source [...].
		 * The receiver SHOULD include an HTTP Accept header indicating its preference of content
		 * types that are acceptable.'
		 */
		ClassicHttpRequest request = ClassicRequestBuilder.get(webmention.source())
			.addHeader(createAcceptHeader())
			.build();

		LOGGER.debug("Verifying source '{}'.", webmention.source());
		return httpClient.execute(request, response -> {
			if (response.getCode() == HttpStatus.SC_NOT_ACCEPTABLE) {
				throw new UnsupportedContentTypeException(
					"Remote server does not support any of the content types supported for verification.");
			}
			HttpUtils.validateResponse(response);
			return isResponseValid(response, webmention);
		});
	}

	private boolean isResponseValid(ClassicHttpResponse response, Webmention webmention)
		throws IOException {
		/*
		 * Spec:
		 * 'The receiver SHOULD use per-media-type rules to determine whether the source document mentions the target URL.
		 * [...]
		 *  The source document MUST have an exact match of the target URL provided in order
		 *  for it to be considered a valid Webmention.'
		 */
		Optional<Verifier> verifierOptional = HttpUtils.extractContentType(response)
			.flatMap(this::findMatchingVerifier);
		if (verifierOptional.isPresent()) {
			Verifier verifier = verifierOptional.get();
			LOGGER.debug("Found verifier '{}' for source '{}'.", verifier, webmention.source());
			return verifier.isValid(response, webmention.target());
		} else {
			throw new UnsupportedContentTypeException("Content type of remote server response is not supported.");
		}
	}

	private Header createAcceptHeader() {
		String acceptValue = verifiers.stream().map(Verifier::getSupportedMimeType).collect(Collectors.joining(", "));
		return new BasicHeader(HttpHeaders.ACCEPT, acceptValue);
	}

	private Optional<Verifier> findMatchingVerifier(ContentType contentType) {
		return verifiers.stream()
			.filter(verifier -> verifier.getSupportedMimeType().equals(contentType.getMimeType()))
			.findFirst();
	}

	public static class UnsupportedContentTypeException extends IOException {
		@Serial
		private static final long serialVersionUID = 7007956002984142094L;

		UnsupportedContentTypeException(String message) {
			super(message);
		}
	}
}
