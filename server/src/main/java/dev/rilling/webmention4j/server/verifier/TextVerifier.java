package dev.rilling.webmention4j.server.verifier;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;

public class TextVerifier implements Verifier {

	@NotNull
	@Override
	public String getSupportedMimeType() {
		return ContentType.TEXT_PLAIN.getMimeType();
	}

	@Override
	public boolean isValid(@NotNull ClassicHttpResponse httpResponse, @NotNull URI target) throws IOException {
		try {
			return EntityUtils.toString(httpResponse.getEntity()).contains(target.toString());
		} catch (ParseException | IOException e) {
			throw new IOException("Could not parse body.", e);
		}
	}
}
