package com.burstintofire.bandcore.shared.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses request bodies larger than {@code bandcore.http.max-request-body} with 413: at once
 * when {@code Content-Length} says so, or while reading a body sent without it (chunked).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class RequestBodyLimitFilter extends OncePerRequestFilter {

	/** Thrown while reading a chunked body past the limit; answered with 413. */
	static class RequestBodyTooLargeException extends IOException {

		RequestBodyTooLargeException() {
			super("Request body too large");
		}

	}

	private final long maxBytes;

	RequestBodyLimitFilter(HttpProperties properties) {
		this.maxBytes = properties.maxRequestBody().toBytes();
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long length = request.getContentLengthLong();
		if (length > maxBytes) {
			response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
			return;
		}
		chain.doFilter(length >= 0 ? request : new LimitedRequest(request, maxBytes), response);
	}

	private static final class LimitedRequest extends HttpServletRequestWrapper {

		private final long maxBytes;

		private ServletInputStream stream;

		LimitedRequest(HttpServletRequest request, long maxBytes) {
			super(request);
			this.maxBytes = maxBytes;
		}

		@Override
		public ServletInputStream getInputStream() throws IOException {
			if (stream == null) {
				stream = new LimitedInputStream(super.getInputStream(), maxBytes);
			}
			return stream;
		}

	}

	private static final class LimitedInputStream extends ServletInputStream {

		private final ServletInputStream delegate;

		private final long maxBytes;

		private long read;

		LimitedInputStream(ServletInputStream delegate, long maxBytes) {
			this.delegate = delegate;
			this.maxBytes = maxBytes;
		}

		@Override
		public int read() throws IOException {
			int value = delegate.read();
			if (value >= 0) {
				count(1);
			}
			return value;
		}

		@Override
		public int read(byte[] buffer, int offset, int length) throws IOException {
			int count = delegate.read(buffer, offset, length);
			if (count > 0) {
				count(count);
			}
			return count;
		}

		private void count(int bytes) throws IOException {
			read += bytes;
			if (read > maxBytes) {
				throw new RequestBodyTooLargeException();
			}
		}

		@Override
		public boolean isFinished() {
			return delegate.isFinished();
		}

		@Override
		public boolean isReady() {
			return delegate.isReady();
		}

		@Override
		public void setReadListener(ReadListener listener) {
			delegate.setReadListener(listener);
		}

	}

}
