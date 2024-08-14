package io.varhttp;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static io.undertow.Handlers.websocket;

public class VarUndertow implements Runnable {

    private final static Logger logger = LoggerFactory.getLogger(VarUndertow.class);
    private final CompletableFuture<Boolean> started = new CompletableFuture<>();

    protected VarServlet servlet;

    protected VarConfig varConfig;
    private final Map<String, HttpServlet> servlets = new LinkedHashMap<>();
    private Undertow server;

    @Inject
    public VarUndertow(VarConfig varConfig, Provider<ParameterHandler> parameterHandlerProvider, ControllerMapper controllerMapper,
                       ObjectFactory objectFactory, ControllerFilter controllerFilter) {

        this.varConfig = varConfig;
        this.servlet = new VarServlet(parameterHandlerProvider.get(), controllerMapper, objectFactory, controllerFilter);
        servlets.put("/", servlet);
    }

    public void configure(Consumer<VarConfiguration> configuration) {
        servlet.configure(c -> {
            configuration.accept(c);
        });
    }

    public void registerServlet(String path, HttpServlet servlet) {
        servlets.put(path, servlet);
    }

    @Override
    public void run() {
        try {
            DeploymentInfo servletBuilder = Servlets.deployment()
                    .setClassLoader(VarUndertow.class.getClassLoader())
                    .setContextPath("/");
            for (Map.Entry<String, HttpServlet> servlet : servlets.entrySet()) {

                ServletInfo servletInfo = Servlets.servlet(servlet.getKey(), servlet.getValue().getClass(), new InstanceFactory<Servlet>() {
                            @Override
                            public InstanceHandle<Servlet> createInstance() throws InstantiationException {
                                return new InstanceHandle<Servlet>() {
                                    @Override
                                    public Servlet getInstance() {
                                        return servlet.getValue();
                                    }

                                    @Override
                                    public void release() {
                                        servlet.getValue().destroy();
                                    }
                                };
                            }
                        })
                        .addMapping(servlet.getKey());

                servletBuilder.addServlets(servletInfo);
                servletBuilder.setDeploymentName("Depname");
            }


            DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);

            manager.deploy();
            PathHandler path = Handlers.path(Handlers.redirect("/"));

//            path.addExactPath("/api/socketInit", websocket(webSocketHandler));
            path.addPrefixPath("/", manager.start());

            server = Undertow.builder()
                    .addHttpListener(varConfig.getPort(), "localhost")
                    .setHandler(path)
                    .build();
            server.start();

            started.complete(true);
            logger.info("var-http started");
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    public void stop() {
        server.stop();
        servlets.values().forEach(HttpServlet::destroy);
    }

    public VarServlet getServlet() {
        return servlet;
    }

    public CompletableFuture<Boolean> getStarted() {
        return started;
    }

}

