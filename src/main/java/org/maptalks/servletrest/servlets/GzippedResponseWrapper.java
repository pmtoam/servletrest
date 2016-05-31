package org.maptalks.servletrest.servlets;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.GZIPOutputStream;

/**
 * https://github.com/leonardoxh/spring-gzip-filter
 * https://github.com/eclipse/jetty.project/blob/jetty-9.2.x/jetty-servlets/src/main/java/org/eclipse/jetty/servlets/gzip/CompressedResponseWrapper.java
 */
public class GzippedResponseWrapper extends HttpServletResponseWrapper {
	private PrintWriter writer;
	private GzippedOutputStream gzippedStream;

	/**
	 * Constructs a response adaptor wrapping the given response.
	 *
	 * @throws IllegalArgumentException if the response is null
	 */
	public GzippedResponseWrapper(HttpServletResponse response) {
		super(response);
		response.addHeader("Content-Encoding", "gzip");
	}

	public void finish() throws IOException {
		if (writer != null && !gzippedStream.isClosed()) {
			writer.flush();
		}
		if (gzippedStream != null) {
			gzippedStream.close();
		}
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		if (gzippedStream == null) {
			if (getResponse().isCommitted()) {
				return getResponse().getOutputStream();
			}
		} else if (writer != null) {
			throw new IllegalStateException("getWriter() called");
		}
		return gzippedStream;
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		if (writer == null) {
			if (gzippedStream != null) {
				throw new IllegalStateException("getOutputStream() called");
			}
			if (getResponse().isCommitted()) {
				return getResponse().getWriter();
			}
			gzippedStream = new GzippedOutputStream(getResponse().getOutputStream());
			String encoding = getCharacterEncoding();
			if (encoding == null) {
				writer = new PrintWriter(gzippedStream);
			} else {
				writer = new PrintWriter(new OutputStreamWriter(gzippedStream, encoding));
			}
		}
		return writer;
	}

	@Override
	public void flushBuffer() throws IOException {
		if (writer != null) {
			writer.flush();
		}
		if (gzippedStream != null) {
			gzippedStream.flush();
		} else {
			super.flushBuffer();
		}
	}

	private static class GzippedOutputStream extends ServletOutputStream {
		private GZIPOutputStream out;
		private final AtomicBoolean closed = new AtomicBoolean(false);

		GzippedOutputStream(OutputStream os) throws IOException {
			out = new GZIPOutputStream(os);
		}

		public void write(int b) throws IOException {
			checkOut();
			out.write(b);
		}

		@Override
		public void write(byte[] b) throws IOException {
			checkOut();
			out.write(b);
		}

		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			checkOut();
			out.write(b, off, len);
		}

		@Override
		public void flush() throws IOException {
			out.flush();
		}

		@Override
		public void close() throws IOException {
			if (closed.compareAndSet(false, true)) {
				out.close();
			}
		}

		public boolean isClosed() {
			return closed.get();
		}

		private void checkOut() throws IOException {
			if (closed.get()) {
				throw new IOException("closed");
			}
		}
	}
}
