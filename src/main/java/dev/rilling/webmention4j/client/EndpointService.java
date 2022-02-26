package dev.rilling.webmention4j.client;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.io.support.ClassicRequestBuilder;
import org.apache.hc.core5.http.message.BasicNameValuePair;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

class EndpointService {
	private final @NotNull Supplier<CloseableHttpClient> httpClientFactory;

	EndpointService(@NotNull Supplier<CloseableHttpClient> httpClientFactory) {
		this.httpClientFactory = httpClientFactory;
	}

	public void notify(@NotNull URI endpoint, @NotNull URI source, @NotNull URI target) throws IOException {
		/*
		 * Spec:
		 * 'The sender MUST post x-www-form-urlencoded source and target parameters to the Webmention endpoint,
		 * where source is the URL of the sender's page containing a link,
		 * and target is the URL of the page being linked to.'
		 *
		 * 'Note that if the Webmention endpoint URL contains query string parameters,
		 * the query string parameters MUST be preserved, and MUST NOT be sent in the POST body.'
		 */
		BasicNameValuePair sourcePair = new BasicNameValuePair("source", source.toString());
		BasicNameValuePair targetPair = new BasicNameValuePair("target", target.toString());
		ClassicHttpRequest request = ClassicRequestBuilder.post(endpoint)
			.addParameters(sourcePair, targetPair)
			.setCharset(StandardCharsets.UTF_8) // Not part of spec, but probably better than ISO
			.build();

		try (CloseableHttpClient httpClient = httpClientFactory.get(); CloseableHttpResponse response = httpClient.execute(
			request)) {
			/*
			 * Spec:
			 * 'The Webmention endpoint will validate and process the request, and return an HTTP status code.
			 * Most often, 202 Accepted or 201 Created will be returned,
			 * indicating that the request is queued and being processed asynchronously to prevent DoS (Denial of Service) attacks.
			 * If the response code is 201,
			 * the Location header will include a URL that can be used to monitor the status of the request.
			 *
			 * 'Any 2xx response code MUST be considered a success.'
			 */
			if (!isSuccessful(response.getCode())) {
				throw new IOException("Received unexpected response: '%d - %s'.".formatted(response.getCode(),
					response.getReasonPhrase()));
			}
		}
	}

	private boolean isSuccessful(int responseCode) {
		return String.valueOf(responseCode).startsWith("2");
	}
}