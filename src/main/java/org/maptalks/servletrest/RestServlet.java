package org.maptalks.servletrest;

import org.maptalks.servletrest.config.ServletPattern;
import org.maptalks.servletrest.config.exceptions.ServletFactoryNotReadyException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class RestServlet extends HttpServlet {
	private static ServletFactory servletFactory;
	private static String encoding;
	private static final String DEFAULT_ENCODING = "UTF-8";

	@Override
	public void init() throws ServletException {
		System.out.println("[RestServlet] begin to initialize servlets");
		ServletConfig config = getServletConfig();
		final String servletConfigPath = getInitParameter("ServletConfig");
		System.out.println("[RestServlet] servlet config:"
			+ servletConfigPath);
		servletFactory = new ServletFactory();
		servletFactory.init(servletConfigPath, config);
		List<ServletPattern> patterns = servletFactory.getServletPatterns();
		if (patterns != null) {
			for (Iterator iterator = patterns.iterator(); iterator
				.hasNext(); ) {
				ServletPattern servletPattern = (ServletPattern) iterator
					.next();
				System.out.println("[RestServlet] servlet pattern:"
					+ servletPattern.getPattern());
			}
		} else {
			System.out.println("[RestServlet] no servlet pattern found.;");
		}
		encoding = getInitParameter("Encoding");
		if (encoding == null) {
			encoding = DEFAULT_ENCODING;
		}
		System.out.println("[RestServlet] initializing ended;");
	}

	@Override
	public void service(ServletRequest req, ServletResponse rep)
		throws ServletException, IOException {
		final HttpServletRequest request = (HttpServletRequest) req;

		final String realUrl = request.getRequestURI().replaceFirst(
			request.getContextPath(), "");
		HttpServlet servlet;
		try {
			servlet = servletFactory.getServletByUrl(realUrl);
		} catch (final ServletFactoryNotReadyException e) {
			throw new ServletException(e);
		}
		if (servlet != null) {
			rep.setCharacterEncoding(encoding);
			rep.setLocale(new Locale("zh"));
			servlet.service(req, rep);
		} else {
			super.service(req, rep);
		}

	}

}
