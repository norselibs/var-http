package io.varhttp;

import javax.servlet.http.HttpServletRequest;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RequestParametersImplementation implements RequestParameters {
	private HttpServletRequest request;

	public RequestParametersImplementation(HttpServletRequest request) {
		this.request = request;
	}

	@Override
	public String get(String name) {
		return request.getParameter(name);
	}

	@Override
	public List<String> getAll(String name) {
		String[] paramValues = request.getParameterValues(name);
		return paramValues == null ? Collections.emptyList() : Arrays.asList(paramValues);
	}

	@Override
	public boolean contains(String name) {
		return request.getParameterMap().containsKey(name);
	}

	@Override
	public Map<String, List<String>> getMap() {
		return request.getParameterMap().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> Arrays.asList(e.getValue())));
	}
}
