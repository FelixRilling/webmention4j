package dev.rilling.webmention4j.client.internal.link;

import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Set;

/**
 * Simplified implementation of a `Link`:
 * <a href="https://datatracker.ietf.org/doc/html/rfc8288">https://datatracker.ietf.org/doc/html/rfc8288</a>
 * <p>
 * The URL is always in the resolved, absolute form.
 */
public record Link(@NotNull URI uri, @NotNull Set<String> rel) {

}
