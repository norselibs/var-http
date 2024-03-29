package io.varhttp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.odinjector.ContextBinder;
import io.odinjector.binding.Binder;
import io.odinjector.binding.BindingContext;

public class OdinContext extends BindingContext {
	private VarConfig config;

	public OdinContext(VarConfig config) {
		this.config = config;
	}

	@Override
	public void configure(Binder binder) {
		binder.bind(VarConfig.class).to(() -> config);
		binder.bind(Serializer.class).to(VarTestSerializer.class);
		binder.bind(ObjectMapper.class).to(() -> new ObjectMapper());
		binder.bind(XmlMapper.class).to(() -> new XmlMapper());
		binder.bind(ObjectFactory.class).to(TestFactory.class);
	}
}
