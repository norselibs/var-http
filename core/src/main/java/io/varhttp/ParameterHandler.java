package io.varhttp;


import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParameterHandler {
	private final List<PathVariableInfo> pathVariables = new ArrayList<>();
	private Pattern pattern;
	private final Serializer serializer;

	@Inject
	public ParameterHandler(Serializer serializer) {
		this.serializer = serializer;
	}

	public Set<HttpMethod> initializeHttpMethods(Method method) {
		Controller controller = method.getAnnotation(Controller.class);
		HttpMethod[] allowedMethods = controller.httpMethods();
		if (allowedMethods.length == 0) {
			allowedMethods = HttpMethod.values();
		}
		return new HashSet<>(Arrays.asList(allowedMethods));
	}

	public Function<ControllerContext, Object>[] initializeHandlers(Method method, String baseUri){
		Function<ControllerContext, Object>[] args = new Function[method.getParameterCount()];
		Controller controller = method.getAnnotation(Controller.class);
		if(controller == null){
			throw new RuntimeException("missing @Controller annotation on method: " + method.getDeclaringClass()+ "." + method.getName() + "()");
		}
		String pathPattern = baseUri; // + controller.path();
		for (int i = 0; i < method.getParameterCount(); i++) {
			Parameter parameter = method.getParameters()[i];
			RequestParameter parameterAnnotation = parameter.getAnnotation(RequestParameter.class);
			if (parameterAnnotation != null) {
				String name = parameterAnnotation.name();
				if("".equals(name)) {
					throw new RuntimeException("Could not determine @RequestParameter name annotation for controller: " + method.getName());
				}
				String lambdaName = name;
				Class<?> type = parameter.getType();
				args[i] = (context -> convert(context.request().getParameter(lambdaName), type));
			}
			RequestBody bodyAnnotation = parameter.getAnnotation(RequestBody.class);
			if (bodyAnnotation != null) {
				args[i] = (context -> {
					try {
						Reader body = new InputStreamReader(context.request().getInputStream());
						if (String.class.isAssignableFrom(parameter.getType())) {
							return toString(body);
						}

						return serializer.deserialize(body, parameter.getType(), context.request().getContentType());
					} catch (IOException e) {
						throw new RuntimeException(e);
					}
				});
			}
			PathVariable pathVariableAnnotation = parameter.getAnnotation(PathVariable.class);
			if (pathVariableAnnotation != null) {
				String name = pathVariableAnnotation.name();
				if("".equals(name)) {
					throw new RuntimeException("Could not determine @PathVariable name annotation for controller: " + method.getName());
				}
				String nameField = "{" + name + "}";
				pathVariables.add(new PathVariableInfo(name, parameter.getType(), pathPattern.indexOf(nameField), i));
				pathPattern = pathPattern.replace(nameField, "(.*?)");
			}

			if (RequestHeader.class == parameter.getType()) {
				args[i] = context -> new ExtensionPointRequestHeader(context.request());
			}

			if (ResponseHeader.class == parameter.getType()) {
				args[i] = context -> new ExtensionPointResponseHeader(context.response());
			}

			if (ResponseStream.class == parameter.getType()) {
				args[i] = context ->  new ExtensionPointResponseStream(context.response(), serializer);
			}
		}

		pathVariables.sort(Comparator.comparingInt(PathVariableInfo::getSortOffset));
		pattern = Pattern.compile(pathPattern);

		return args;
	}

	private String toString(Reader reader) {
		try {
			int intValueOfChar;
			String targetString = "";
			while ((intValueOfChar = reader.read()) != -1) {
				targetString += (char) intValueOfChar;
			}
			reader.close();
			return targetString;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private Object convert(String parameter, Class<?> type) {
		if (type.equals(String.class)) {
			return parameter;
		}
		throw new RuntimeException("Unhandled conversion type: "+type.getName());
	}

	public void handleReturnResponse(Object response, ControllerContext context) {
		if (response != null && !(response instanceof Void)) {
			String contentType = "text/html";
			if (!(response instanceof String)) {
				contentType = "application/json";
			}
			new ExtensionPointResponseStream(context.response(), serializer).write(response, contentType);
		}
	}

	private static class ExtensionPointResponseStream implements ResponseStream {
		private final HttpServletResponse response;
		private Serializer serializer;

		private ExtensionPointResponseStream(HttpServletResponse response, Serializer serializer) {
			this.response = response;
			this.serializer = serializer;
		}

		@Override
		public BufferedWriter getContentWriter(String fileName, String contentType, Charset charset) {
			response.addHeader("Content-Disposition", "attachment; filename=\"" + fileName + '"');
			response.setContentType(contentType);
			response.setCharacterEncoding(charset.name());
			try {
				return new BufferedWriter(new OutputStreamWriter(new BufferedOutputStream(response.getOutputStream()), charset));
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		public OutputStream getOutputStream(String contentType, Charset charset) {
			response.setContentType(contentType);
			if(charset != null) {
				response.setCharacterEncoding(charset.name());
			}
			try {
				return response.getOutputStream();
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}
		}

		@Override
		public void write(Object object) {
			try (OutputStreamWriter streamWriter = new OutputStreamWriter(response.getOutputStream(), "UTF-8")) {
				serializer.serialize(streamWriter, object, response.getHeaders("Content-Type").stream().findFirst().orElse("application/json"));
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void write(Object content, String contentType) {
			try (OutputStreamWriter streamWriter = new OutputStreamWriter(response.getOutputStream(), "UTF-8")) {
				response.setContentType(contentType);
				if (content instanceof String) {
					streamWriter.write((String) content);
				} else {
					serializer.serialize(streamWriter, content, contentType);
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
	}

	private class ExtensionPointRequestHeader implements RequestHeader {
		private HttpServletRequest request;

		ExtensionPointRequestHeader(HttpServletRequest request) {
			this.request = request;
		}

		@Override
		public String getHeader(String name) {
			return String.join(", ", getHeaders(name));
		}

		@Override
		public List<String> getHeaders(String name) {
			return Collections.list(request.getHeaders(name));
		}

		@Override
		public Set<String> getHeaderNames() {
			return new HashSet<>(Collections.list(request.getHeaderNames()));
		}
	}

	private class ExtensionPointResponseHeader implements ResponseHeader {
		private HttpServletResponse response;

		private ExtensionPointResponseHeader(HttpServletResponse response) {
			this.response = response;
		}

		@Override
		public void setStatus(int httpResonseCode) {
			response.setStatus(httpResonseCode);
		}

		@Override
		public void addHeader(String name, String value) {
			response.addHeader(name, value);
		}
	}

	public Function<ControllerContext, Object>[] addPathVariables(Function<ControllerContext, Object>[] handlers, HttpServletRequest request) {
		Function<ControllerContext, Object>[] args = handlers.clone();
		String fullPath = request.getServletPath()+request.getPathInfo();
		Matcher m = pattern.matcher(fullPath);
		if (m.matches()) {
			for (int i = 0; i < pathVariables.size(); i++) {
				Class<?> type = pathVariables.get(i).getType();
				String value = m.group(i + 1);
				args[pathVariables.get(i).getArgno()] = (r -> convert(value, type));
			}
		}
		return args;
	}
}
