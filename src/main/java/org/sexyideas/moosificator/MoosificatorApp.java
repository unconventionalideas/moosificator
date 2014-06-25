package org.sexyideas.moosificator;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.glassfish.jersey.filter.LoggingFilter;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.TracingConfig;

import javax.ws.rs.ApplicationPath;

@ApplicationPath("/")
public class MoosificatorApp extends ResourceConfig {
    public MoosificatorApp() {
        registerClasses(MooseResource.class);
        register(LoggingFilter.class);
        property(ServerProperties.TRACING, TracingConfig.ALL.name());

        System.out.printf("Loaded app with resources: %s\n", getClasses());
    }

    public static void main(String[] args) throws Exception {
//        FilterHolder filterHolder = new FilterHolder(ServletContainer.class);
//        filterHolder.setInitParameter("javax.ws.rs.Application", "org.sexyideas.moosificator.MoosificatorApp");
//        filterHolder.setInitParameter("jersey.config.servlet.filter.forwardOn404", "true");
//        //filterHolder.setInitParameter("jersey.config.servlet.filter.staticContentRegex", "/resources/*.html");
//
//        Server server = new Server(Integer.valueOf(System.getenv("PORT")));
//        ServletContextHandler context = new ServletContextHandler(server, "/", ServletContextHandler.SESSIONS);
//        context.addFilter(filterHolder, "/*", 1);
//        server.start();
//        server.join();

        Server server = new Server(Integer.valueOf(System.getenv("PORT")));
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        ServletHolder jerseyServlet = context.addServlet(org.glassfish.jersey.servlet.ServletContainer.class, "/*");
        jerseyServlet.setInitOrder(1);
        jerseyServlet.setInitParameter("javax.ws.rs.Application", "org.sexyideas.moosificator.MoosificatorApp");
        jerseyServlet.setInitParameter("com.sun.jersey.config.property.packages", "jetty");

        try {
            server.start();
            server.join();
        } catch (Throwable t) {
            t.printStackTrace(System.err);
        }
    }
}