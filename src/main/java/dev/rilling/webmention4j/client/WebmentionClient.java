package dev.rilling.webmention4j.client;

import dev.rilling.webmention4j.client.link.HeaderLinkParser;
import dev.rilling.webmention4j.client.link.HtmlLinkParser;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.util.function.Supplier;

public final class WebmentionClient {
	private final EndpointDiscoveryService endpointDiscoveryService;
	private final EndpointService endpointService;

	public WebmentionClient() {
		this(HttpClients::createDefault);
	}

	public WebmentionClient(@NotNull Supplier<CloseableHttpClient> httpClientFactory) {
		this(new EndpointService(httpClientFactory),
			new EndpointDiscoveryService(httpClientFactory, new HeaderLinkParser(), new HtmlLinkParser()));
	}

	WebmentionClient(EndpointService endpointService, EndpointDiscoveryService endpointDiscoveryService) {
		this.endpointDiscoveryService = endpointDiscoveryService;
		this.endpointService = endpointService;
	}

	/**
	 * Notifies the target page that it was mention by the source page.
	 *
	 * @param source Source page that is mentioning the target.
	 * @param target Page being mention.
	 * @throws IOException if IO fails.
	 */
	public void notify(@NotNull URI source, @NotNull URI target) throws IOException {
		// TODO: set fitting UA
		URI endpoint = endpointDiscoveryService.discoverEndpoint(target)
			.orElseThrow(() -> new IOException("Could not find any webmention endpoint URI in the target resource."));

		endpointService.notifyEndpoint(endpoint, source, target);
	}

}
