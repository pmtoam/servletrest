package org.maptalks.servletrest.servlets;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.io.*;
import java.net.URLDecoder;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * http://stackoverflow.com/questions/16638345/how-to-decode-gzip-compressed-request-body-in-spring-mvc
 * https://github.com/eclipse/jetty.project/blob/jetty-9.3.x/jetty-server/src/main/java/org/eclipse/jetty/server/Request.java
 */
public class GzippedRequestWrapper extends HttpServletRequestWrapper {
	private static final String METHOD_POST = "POST";
	private static final String FORM_ENCODED = "application/x-www-form-urlencoded";
	private static final String DEFAULT_ENCODING = "UTF-8";

	private byte[] bytes;
	private boolean paramsExtracted;
	private Map<String, List<String>> queryParameters;
	private Map<String, List<String>> contentParameters;
	private Map<String, List<String>> parameters;

	/**
	 * Constructs a request object wrapping the given request.
	 *
	 * @throws IllegalArgumentException if the request is null
	 */
	public GzippedRequestWrapper(HttpServletRequest request) throws IOException {
		super(request);
		InputStream is = new GZIPInputStream(request.getInputStream());
		bytes = readBytes(is);
	}

	private static byte[] readBytes(InputStream is) throws IOException {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		copyStream(is, os);
		return os.toByteArray();
	}

	private static void copyStream(InputStream is, OutputStream os) throws IOException {
		byte[] buf = new byte[8192];
		while (true) {
			int n = is.read(buf);
			if (n == -1) {
				break;
			}
			os.write(buf, 0, n);
		}
	}

	@Override
	public ServletInputStream getInputStream() throws IOException {
		final ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
		return new ServletInputStream() {
			public int read() throws IOException {
				return stream.read();
			}

			@Override
			public int read(byte[] b) throws IOException {
				return stream.read(b);
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				return stream.read(b, off, len);
			}

			public void close() throws IOException {
				super.close();
				stream.close();
			}
		};
	}

	@Override
	public String getParameter(String name) {
		if (!paramsExtracted)
			extractParameters();

		if (parameters == null)
			parameters = restoreParameters();

		List<String> vals = parameters.get(name);
		if (vals == null || vals.isEmpty()) {
			return null;
		}

		return vals.get(0);
	}

	@Override
	public Map getParameterMap() {
		if (!paramsExtracted)
			extractParameters();

		if (parameters == null)
			parameters = restoreParameters();

		HashMap<String, String[]> map = new HashMap<String, String[]>(parameters.size() * 3 / 2);
		for (Map.Entry<String, List<String>> entry : parameters.entrySet()) {
			String[] a = null;
			if (entry.getValue() != null) {
				a = new String[entry.getValue().size()];
				a = entry.getValue().toArray(a);
			}
			map.put(entry.getKey(), a);
		}

		return Collections.unmodifiableMap(map);
	}

	@Override
	public Enumeration getParameterNames() {
		if (!paramsExtracted)
			extractParameters();

		if (parameters == null)
			parameters = restoreParameters();

		return Collections.enumeration(parameters.keySet());
	}

	@Override
	public String[] getParameterValues(String name) {
		if (!paramsExtracted)
			extractParameters();

		if (parameters == null)
			parameters = restoreParameters();

		List<String> vals = parameters.get(name);
		if (vals == null)
			return null;

		return vals.toArray(new String[vals.size()]);
	}

	private void extractParameters() {
		if (paramsExtracted)
			return;

		paramsExtracted = true;

		if (queryParameters == null)
			queryParameters = extractQueryParameters();

		if (contentParameters == null)
			contentParameters = extractContentParameters();

		parameters = restoreParameters();
	}

	private Map<String, List<String>> extractQueryParameters() {
		Map<String, List<String>> result = new HashMap<String, List<String>>();
		String queryString = getQueryString();
		if (queryString != null) {
			// use body character encoding
			try {
				parseParameters(result, queryString, getCharacterEncoding());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return result;
	}

	private Map<String, List<String>> extractContentParameters() {
		Map<String, List<String>> result = new HashMap<String, List<String>>();
		String contentType = getContentType();
		if (contentType != null) {
			if (FORM_ENCODED.equals(contentType) && METHOD_POST.equals(getMethod())) {
				extractFormParameters(result);
			}
		}
		return result;
	}

	private void extractFormParameters(Map<String, List<String>> params) {
		String body = new String(bytes);
		try {
			parseParameters(params, body, getCharacterEncoding());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void parseParameters(Map<String, List<String>> params, String s, String encoding) throws IOException {
		encoding = encoding != null ? encoding : DEFAULT_ENCODING;

		for (String pair : s.split("&")) {
			if (pair == null || pair.length() == 0) {
				continue;
			}

			int idx = pair.indexOf("=");

			String key;
			if (idx > 0) {
				key = URLDecoder.decode(pair.substring(0, idx), encoding);
			} else {
				key = pair;
			}
			String value;
			if (idx > 0 && pair.length() > idx + 1) {
				value = URLDecoder.decode(pair.substring(idx + 1), encoding);
			} else {
				value = null;
			}

			List<String> values = params.get(key);
			if (values == null) {
				values = new ArrayList<String>();
			}
			values.add(value);
			params.put(key, values);
		}
	}

	private Map<String, List<String>> restoreParameters() {
		Map<String, List<String>> result = new HashMap<String, List<String>>();

		if (queryParameters == null)
			queryParameters = extractQueryParameters();

		mergeParameters(result, queryParameters);
		mergeParameters(result, contentParameters);

		return result;
	}

	private void mergeParameters(Map<String, List<String>> to, Map<String, List<String>> from) {
		if (from == null || from.isEmpty())
			return;

		for (Map.Entry<String, List<String>> entry : from.entrySet()) {
			String name = entry.getKey();
			List<String> values = entry.getValue();

			List<String> list = to.get(name);
			if (list == null) {
				list = new ArrayList<String>();
			}
			list.addAll(values);
			to.put(name, list);
		}
	}
}
