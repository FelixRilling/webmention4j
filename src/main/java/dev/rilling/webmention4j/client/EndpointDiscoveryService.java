package dev.rilling.webmention4j.client;

import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Evaluator;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Spec: https://www.w3.org/TR/webmention/#h-sender-discovers-receiver-webmention-endpoint
class EndpointDiscoveryService {
	// TODO: check against https://datatracker.ietf.org/doc/html/rfc5988#section-5
	private static final Pattern HEADER_LINK_WEBMENTION = Pattern.compile("<(?<url>.*)>; rel=\"webmention\"");

	private final HttpClient httpClient;

	EndpointDiscoveryService(@NotNull HttpClient httpClient) {
		// Spec: 'Follow redirects'
		if (httpClient.followRedirects() != HttpClient.Redirect.ALWAYS) {
			throw new IllegalArgumentException("HttpClient must follow redirects.");
		}

		this.httpClient = httpClient;
	}

	@NotNull
	public Optional<URI> discover(@NotNull URI uri) throws IOException, InterruptedException {
		// Spec: 'The sender MUST fetch the target URL'
		HttpRequest request = HttpRequest.newBuilder().GET().uri(uri).build();

		HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

		/*
		 * Spec:
		 * 'Check for an HTTP Link header with a rel value of webmention.
		 * If the content type of the document is HTML,
		 * then the sender MUST look for an HTML <link> and <a> element with a rel value of webmention.
		 * If more than one of these is present, the first HTTP Link header takes precedence,
		 * followed by the first <link> or <a> element in document order.
		 * Senders MUST support all three options and fall back in this order.'
		 */

		// TODO: check headers before receiving body.
		Optional<URI> fromHeader = extractEndpointFromHeader(response.headers());
		if (fromHeader.isPresent()) {
			return fromHeader;
		}

		// TODO: extract and validate that e.g 'text/htmlxyz' does not match.
		if (response.headers().firstValue("Content-Type")
			// Charset can be ignored, HttpClient already handled it
			.map(contentType -> contentType.startsWith("text/html")).orElse(false)) {
			return extractEndpointFromHtml(uri, response.body());
		}

		return Optional.empty();
	}

	private Optional<URI> extractEndpointFromHeader(HttpHeaders httpHeaders) {
		// https://datatracker.ietf.org/doc/html/rfc5988
		for (String link : httpHeaders.allValues("Link")) {
			Matcher matcher = HEADER_LINK_WEBMENTION.matcher(link);
			if (matcher.matches()) {
				URI endpoint = URI.create(matcher.group("url"));
				// Return first match, ignore others.
				return Optional.of(endpoint);
			}
		}
		return Optional.empty();
	}

	private Optional<URI> extractEndpointFromHtml(URI baseUri, String body) {
		Document document = Jsoup.parse(body, baseUri.toString());
		Element firstWebmentionElement = document.selectFirst(new Evaluator() {
			@Override
			public boolean matches(@NotNull Element root, @NotNull Element element) {
				return ("link".equals(element.normalName()) || "a".equals(element.normalName())) &&
					"webmention".equals(element.attr("rel"));
			}
		});
		if (firstWebmentionElement != null) {
			/*
			 * Spec:
			 * 'The endpoint MAY be a relative URL,
			 * in which case the sender MUST resolve it relative to the target URL'.
			 */
			URI endpoint = URI.create(firstWebmentionElement.absUrl("href"));
			return Optional.of(endpoint);
		}
		return Optional.empty();
	}

}
