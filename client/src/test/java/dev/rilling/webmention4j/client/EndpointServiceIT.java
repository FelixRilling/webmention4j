package dev.rilling.webmention4j.client;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.net.URI;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static com.github.tomakehurst.wiremock.matching.RequestPatternBuilder.newRequestPattern;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EndpointServiceIT {

	@RegisterExtension
	static final WireMockExtension WIREMOCK = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort())
		.build();

	final EndpointService endpointService = new EndpointService();

	@Test
	@DisplayName("Spec: 'The sender MUST post x-www-form-urlencoded source and target parameters to the Webmention " +
		"endpoint, where source is the URL of the sender's page containing a link, and target is the URL of the page being linked to'")
	void sendsEncodedData() throws IOException {
		WIREMOCK.stubFor(post("/webmention-endpoint").willReturn(ok()));

		URI endpoint = URI.create(WIREMOCK.url("/webmention-endpoint"));
		URI source = URI.create("https://waterpigs.example/post-by-barnaby");
		URI target = URI.create("https://aaronpk.example/post-by-aaron");

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			endpointService.notifyEndpoint(httpClient, endpoint, source, target);
		}

		UrlPattern urlPattern = new UrlPattern(new EqualToPattern("/webmention-endpoint", false), false);
		EqualToPattern contentTypePattern = new EqualToPattern("application/x-www-form-urlencoded; charset=UTF-8");
		EqualToPattern bodyPattern = new EqualToPattern("source=https%3A%2F%2Fwaterpigs.example%2Fpost-by-barnaby" +
			"&target=https%3A%2F%2Faaronpk.example%2Fpost-by-aaron");
		WIREMOCK.verify(newRequestPattern(RequestMethod.POST, urlPattern).withHeader("Content-Type", contentTypePattern)
			.withRequestBody(bodyPattern));
	}

	@Test
	@DisplayName("Spec: 'Any 2xx response code MUST be considered a success' (success)")
	void allows2XXStatus() throws IOException {
		WIREMOCK.stubFor(post("/webmention-endpoint-ok").willReturn(aResponse().withStatus(HttpStatus.SC_OK)));
		WIREMOCK.stubFor(post("/webmention-endpoint-created").willReturn(aResponse().withStatus(HttpStatus.SC_CREATED)));
		WIREMOCK.stubFor(post("/webmention-endpoint-accepted").willReturn(aResponse().withStatus(HttpStatus.SC_ACCEPTED)));

		URI source = URI.create("https://waterpigs.example/post-by-barnaby");
		URI target = URI.create("https://aaronpk.example/post-by-aaron");

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			endpointService.notifyEndpoint(httpClient,
				URI.create(WIREMOCK.url("/webmention-endpoint-ok")),
				source,
				target);
			endpointService.notifyEndpoint(httpClient,
				URI.create(WIREMOCK.url("/webmention-endpoint-created")),
				source,
				target);
			endpointService.notifyEndpoint(httpClient,
				URI.create(WIREMOCK.url("/webmention-endpoint-accepted")),
				source,
				target);
		}
	}

	@Test
	@DisplayName("Spec: 'Any 2xx response code MUST be considered a success' (error)")
	void throwsForNon2XXStatus() throws IOException {
		WIREMOCK.stubFor(post("/webmention-endpoint-client").willReturn(aResponse().withStatus(HttpStatus.SC_CLIENT_ERROR)));
		WIREMOCK.stubFor(post("/webmention-endpoint-unauthorized").willReturn(aResponse().withStatus(HttpStatus.SC_UNAUTHORIZED)));
		WIREMOCK.stubFor(post("/webmention-endpoint-not-found").willReturn(aResponse().withStatus(HttpStatus.SC_NOT_FOUND)));
		WIREMOCK.stubFor(post("/webmention-endpoint-server-error").willReturn(aResponse().withStatus(HttpStatus.SC_SERVER_ERROR)));

		URI source = URI.create("https://waterpigs.example/post-by-barnaby");
		URI target = URI.create("https://aaronpk.example/post-by-aaron");

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			assertThatThrownBy(() -> endpointService.notifyEndpoint(httpClient,
				URI.create(WIREMOCK.url("/webmention-endpoint-client")),
				source,
				target)).isInstanceOf(IOException.class);
			assertThatThrownBy(() -> endpointService.notifyEndpoint(httpClient,
				URI.create(WIREMOCK.url("/webmention-endpoint-unauthorized")),
				source,
				target)).isInstanceOf(IOException.class);
			assertThatThrownBy(() -> endpointService.notifyEndpoint(httpClient,
				URI.create(WIREMOCK.url("/webmention-endpoint-not-found")),
				source,
				target)).isInstanceOf(IOException.class);
			assertThatThrownBy(() -> endpointService.notifyEndpoint(httpClient,
				URI.create(WIREMOCK.url("/webmention-endpoint-server-error")),
				source,
				target)).isInstanceOf(IOException.class);
		}
	}

	@Test
	@DisplayName("Spec: 'Note that if the Webmention endpoint URL contains query string parameters," +
		"the query string parameters MUST be preserved, and MUST NOT be sent in the POST body'")
	void keepsQueryParams() throws IOException {
		WIREMOCK.stubFor(post("/webmention-endpoint?version=1").willReturn(ok()));

		URI endpoint = URI.create(WIREMOCK.url("/webmention-endpoint?version=1"));
		URI source = URI.create("https://waterpigs.example/post-by-barnaby");
		URI target = URI.create("https://aaronpk.example/post-by-aaron");

		try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
			endpointService.notifyEndpoint(httpClient, endpoint, source, target);
		}

		UrlPattern urlPattern = new UrlPattern(new EqualToPattern("/webmention-endpoint?version=1", false), false);
		EqualToPattern bodyPattern = new EqualToPattern("source=https%3A%2F%2Fwaterpigs.example%2Fpost-by-barnaby" +
			"&target=https%3A%2F%2Faaronpk.example%2Fpost-by-aaron");
		WIREMOCK.verify(newRequestPattern(RequestMethod.POST, urlPattern).withRequestBody(bodyPattern));
	}

}