package dev.rilling.webmention4j.client;

import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.matching.EqualToPattern;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.net.URI;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EndpointServiceTest {

	@RegisterExtension
	static final WireMockExtension WIREMOCK = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort())
		.build();

	final EndpointService endpointService = new EndpointService(HttpClients::createDefault);

	@Test
	@DisplayName("Spec: 'The sender MUST post x-www-form-urlencoded source and target parameters to the Webmention " +
		"endpoint, where source is the URL of the sender's page containing a link, and target is the URL of the page being linked to.'")
	void sendsEncodedData() throws IOException {
		WIREMOCK.stubFor(post("/webmention-endpoint").willReturn(ok()));

		URI endpoint = URI.create(WIREMOCK.url("/webmention-endpoint"));
		URI source = URI.create("https://waterpigs.example/post-by-barnaby");
		URI target = URI.create("https://aaronpk.example/post-by-aaron");

		endpointService.notify(endpoint, source, target);

		UrlPattern urlPattern = new UrlPattern(new EqualToPattern("/webmention-endpoint", false), false);
		EqualToPattern contentTypePattern = new EqualToPattern("application/x-www-form-urlencoded; charset=UTF-8");
		EqualToPattern bodyPattern = new EqualToPattern("source=https%3A%2F%2Fwaterpigs.example%2Fpost-by-barnaby" +
			"&target=https%3A%2F%2Faaronpk.example%2Fpost-by-aaron");
		WIREMOCK.verify(RequestPatternBuilder.newRequestPattern(RequestMethod.POST, urlPattern)
			.withHeader("Content-Type", contentTypePattern)
			.withRequestBody(bodyPattern));
	}

	@Test
	@DisplayName("Spec: 'Any 2xx response code MUST be considered a success.'")
	void allows2XXStatus() throws IOException {
		WIREMOCK.stubFor(post("/webmention-endpoint-ok").willReturn(aResponse().withStatus(200)));
		WIREMOCK.stubFor(post("/webmention-endpoint-created").willReturn(aResponse().withStatus(201)));
		WIREMOCK.stubFor(post("/webmention-endpoint-accepted").willReturn(aResponse().withStatus(201)));
		WIREMOCK.stubFor(post("/webmention-endpoint-error-client").willReturn(aResponse().withStatus(400)));
		WIREMOCK.stubFor(post("/webmention-endpoint-error-server").willReturn(aResponse().withStatus(500)));

		URI source = URI.create("https://waterpigs.example/post-by-barnaby");
		URI target = URI.create("https://aaronpk.example/post-by-aaron");

		endpointService.notify(URI.create(WIREMOCK.url("/webmention-endpoint-ok")), source, target);
		endpointService.notify(URI.create(WIREMOCK.url("/webmention-endpoint-created")), source, target);
		endpointService.notify(URI.create(WIREMOCK.url("/webmention-endpoint-accepted")), source, target);

		assertThatThrownBy(() -> endpointService.notify(URI.create(WIREMOCK.url("/webmention-endpoint-error-client")),
			source,
			target)).isInstanceOf(IOException.class);
		assertThatThrownBy(() -> endpointService.notify(URI.create(WIREMOCK.url("/webmention-endpoint-error-server")),
			source,
			target)).isInstanceOf(IOException.class);
	}
}