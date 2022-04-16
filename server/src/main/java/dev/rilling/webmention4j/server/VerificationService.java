package dev.rilling.webmention4j.server;

import dev.rilling.webmention4j.server.verifier.HtmlVerifier;
import dev.rilling.webmention4j.server.verifier.Verifier;
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
import java.util.Set;
import java.util.stream.Collectors;

class VerificationService {
	private static final Logger LOGGER = LoggerFactory.getLogger(VerificationService.class);

	public static final Set<String> SUPPORTED_SCHEMES = Set.of("http", "https");

	private static final List<Verifier> VERIFIERS = List.of(new HtmlVerifier());

	/**
	 * Verifies if the source URI mentions the target URI.
	 *
	 * @param httpClient HTTP client.
	 *                   Must be configured to follow redirects.
	 *                   Should be configured to use a fitting UA string.
	 * @param source     Source URI to check.
	 * @param target     Target URI to look for.
	 * @throws IOException if IO fails.
	 */
	public void verifySubmission(@NotNull CloseableHttpClient httpClient, @NotNull URI source, @NotNull URI target)
		throws IOException, VerificationException {
		/*
		 * Spec:
		 * 'If the receiver is going to use the Webmention in some way, (displaying it as a comment on a post,
		 * incrementing a "like" counter, notifying the author of a post), then it MUST perform an HTTP GET request
		 * on source'
		 *
		 * 'The receiver SHOULD include an HTTP Accept header indicating its preference of content types that are acceptable.'
		 */

		ClassicHttpRequest request = ClassicRequestBuilder.get(source).addHeader(createAcceptHeader()).build();

		LOGGER.debug("Verifying source '{}'.", source);
		try (ClassicHttpResponse response = httpClient.execute(request)) {
			verifySubmissionResponse(response, source, target);
		}
	}

	private void verifySubmissionResponse(ClassicHttpResponse response, @NotNull URI source, @NotNull URI target)
		throws IOException, VerificationException {
		Optional<Verifier> verifierOptional = findMatchingVerifier(response);
		if (verifierOptional.isPresent()) {
			Verifier verifier = verifierOptional.get();
			LOGGER.debug("Found verifier '{}' for source '{}'.", verifier, source);
			if (!verifier.isValid(response, target)) {
				throw new VerificationException("Verification of presence of '%s' in the content of '%s' failed.".formatted(
					target,
					source));
			}
		} else {
			LOGGER.debug("No verifier supports response content type, rejecting. {}", response);
			throw new IOException("Content type of source is not supported.");
		}
	}

	private @NotNull Header createAcceptHeader() {
		String acceptValue = VERIFIERS.stream().map(Verifier::getSupportedMimeType).collect(Collectors.joining(", "));
		return new BasicHeader(HttpHeaders.ACCEPT, acceptValue);
	}

	private @NotNull Optional<Verifier> findMatchingVerifier(@NotNull ClassicHttpResponse httpResponse) {
		Header contentTypeHeader = httpResponse.getFirstHeader(HttpHeaders.CONTENT_TYPE);
		if (contentTypeHeader == null) {
			return Optional.empty();
		}
		ContentType contentType = ContentType.parse(contentTypeHeader.getValue());
		if (contentType == null) {
			return Optional.empty();
		}
		return VERIFIERS.stream()
			.filter(verifier -> verifier.getSupportedMimeType().equals(contentType.getMimeType()))
			.findFirst();
	}

	static class VerificationException extends Exception {
		@Serial
		private static final long serialVersionUID = 7007956002984142094L;

		VerificationException(String message) {
			super(message);
		}
	}
}