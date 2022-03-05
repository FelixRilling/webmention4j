package dev.rilling.webmention4j.client;

import jakarta.ws.rs.core.Link;
import jakarta.ws.rs.ext.RuntimeDelegate;
import org.apache.hc.core5.http.*;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.jsoup.select.Evaluator;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Set;

class HtmlLinkParser implements LinkParser {

	private static final LinkElementEvaluator LINK_ELEMENT_EVALUATOR = new LinkElementEvaluator();

	public @NotNull List<Link> parse(@NotNull URI uri, @NotNull ClassicHttpResponse httpResponse) throws IOException {
		if (!isHtml(httpResponse)) {
			return List.of();
		}

		String body;
		try {
			body = EntityUtils.toString(httpResponse.getEntity());
		} catch (ParseException e) {
			throw new IOException("Could not parse body.", e);
		}

		Document document = Jsoup.parse(body, uri.toString());
		Elements elements = document.select(LINK_ELEMENT_EVALUATOR);
		RuntimeDelegate runtimeDelegate = RuntimeDelegate.getInstance();
		return elements.stream()
			.map(element -> runtimeDelegate.createLinkBuilder()
				.baseUri(uri)
				.uri(element.attr("href"))
				.rel(element.attr("rel"))
				.build())
			.toList();
	}

	private boolean isHtml(@NotNull HttpResponse httpResponse) {
		Header contentType = httpResponse.getFirstHeader("Content-Type");
		return contentType != null && ContentType.parse(contentType.getValue()).isSameMimeType(ContentType.TEXT_HTML);
	}

	private static class LinkElementEvaluator extends Evaluator {

		private static final Set<String> LINK_ELEMENT_NAMES = Set.of("link", "a");

		@Override
		public boolean matches(@NotNull Element root, @NotNull Element element) {
			return LINK_ELEMENT_NAMES.contains(element.normalName()) && element.hasAttr("href") &&
				element.hasAttr("rel");
		}
	}
}
