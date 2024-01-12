package io.varhttp;

import io.odinjector.binding.Binder;
import io.odinjector.binding.BindingContext;

public class ControllerFilterContext extends BindingContext {
	@Override
	public void configure(Binder binder) {
		binder.bind(ControllerFilter.class).to(() -> new ControllerFilter() {
			@Override
			public boolean accepts(Request request, ControllerExecution execution) {
				if (execution.getMethod().getName().equals("muh")) {
					return false;
				} else {
					return true;
				}
			}
		});
	}
}
