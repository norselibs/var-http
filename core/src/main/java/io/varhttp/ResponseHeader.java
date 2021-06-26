package io.varhttp;

/**
 * <p>
 * Carrier for http response headers to be injected into extension point controller methods, to allow the methods
 * to gain access to the response headers
 * </p>
 * <pre><b>Example:</b>
 *  <code>@Controller(path = "/myPath")</code>{@code
 *  public void myControllerMethod(ResponseHeader responseHeader) {
 *      responseHeader.addHeader("myHeaderName", "myHeaderValue");
 *      responseHeader.setStatus(200);
 *  }
 * }</pre>
 */
public interface ResponseHeader {
	/**
	 * Explicitly set the http response code
	 * @param httpResponseCode response code, e.g. 200 for "OK"
	 */
	void setStatus(int httpResponseCode);

	/**
	 * Explicitly add a http header to the response
	 * @param name the header name
	 * @param value the header value
	 */
	void addHeader(String name, String value);

	default void setContentType(String s) {
		addHeader("Content-Type", s);
	}
}
