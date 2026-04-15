package com.flowboard;

import com.amazonaws.serverless.proxy.internal.servlet.AwsLambdaServletContainerHandler;
import com.amazonaws.serverless.proxy.internal.servlet.AwsServletRegistration;
import com.amazonaws.serverless.proxy.model.AwsProxyRequest;
import com.amazonaws.serverless.proxy.model.AwsProxyResponse;
import com.amazonaws.serverless.proxy.spring.embedded.ServerlessServletEmbeddedServerFactory;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Custom Lambda container handler that works around a bug in
 * aws-serverless-java-container-springboot3 2.1.0 with Spring Boot 3.2
 * where the SpringApplication incorrectly creates a non-web ApplicationContext
 * in the Lambda environment.
 */
public class FixedSpringBootLambdaContainerHandler
        extends com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler<AwsProxyRequest, AwsProxyResponse> {

    private static final String FIELD_SPRING_BOOT_INITIALIZER = "springBootInitializer";
    private static final String FIELD_SPRING_WEB_APP_TYPE = "springWebApplicationType";
    private static final String FIELD_SPRING_PROFILES = "springProfiles";
    private static final String FIELD_APPLICATION_CONTEXT = "applicationContext";
    private static final String FIELD_INITIALIZED = "initialized";

    public FixedSpringBootLambdaContainerHandler() throws Exception {
        super(AwsProxyRequest.class,
                AwsProxyResponse.class,
                new com.amazonaws.serverless.proxy.internal.servlet.AwsProxyHttpServletRequestReader(),
                new com.amazonaws.serverless.proxy.internal.servlet.AwsProxyHttpServletResponseWriter(),
                new com.amazonaws.serverless.proxy.AwsProxySecurityContextWriter(),
                new com.amazonaws.serverless.proxy.spring.SpringBootAwsProxyExceptionHandler(),
                FlowBoardApplication.class,
                new com.amazonaws.serverless.proxy.InitializationWrapper(),
                WebApplicationType.SERVLET);
    }

    @Override
    public void initialize() throws com.amazonaws.serverless.exceptions.ContainerInitializationException {
        com.amazonaws.serverless.proxy.internal.testutils.Timer.start("SPRINGBOOT2_COLD_START");
        try {
            Class<?> springBootInitializer = getFieldValue(FIELD_SPRING_BOOT_INITIALIZER);
            WebApplicationType webAppType = getFieldValue(FIELD_SPRING_WEB_APP_TYPE);
            String[] profiles = getFieldValue(FIELD_SPRING_PROFILES);

            Class<?>[] sources = new Class[2];
            if (webAppType == WebApplicationType.REACTIVE) {
                try {
                    getClass().getClassLoader().loadClass("org.springframework.web.reactive.HandlerAdapter");
                    sources[0] = com.amazonaws.serverless.proxy.spring.embedded.ServerlessReactiveServletEmbeddedServerFactory.class;
                } catch (ClassNotFoundException e) {
                    webAppType = WebApplicationType.SERVLET;
                    setFieldValue(FIELD_SPRING_WEB_APP_TYPE, webAppType);
                    sources[0] = ServerlessServletEmbeddedServerFactory.class;
                }
            } else {
                sources[0] = ServerlessServletEmbeddedServerFactory.class;
            }
            sources[1] = springBootInitializer;

            // Run Flyway migrations BEFORE Spring Boot starts to avoid HikariCP contention
            String dbUrl = System.getenv("DB_URL");
            String dbUser = System.getenv("DB_USERNAME");
            String dbPass = System.getenv("DB_PASSWORD");
            if (dbUrl != null && dbUser != null && dbPass != null) {
                try {
                    System.out.println("[FixedSpringBootLambdaContainerHandler] Running Flyway migrations directly...");
                    org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                            .dataSource(dbUrl, dbUser, dbPass)
                            .load();
                    flyway.migrate();
                    System.out.println("[FixedSpringBootLambdaContainerHandler] Flyway migrations complete.");
                } catch (Exception e) {
                    System.err.println("[FixedSpringBootLambdaContainerHandler] Flyway migration failed: " + e.getMessage());
                    throw e;
                }
            }

            SpringApplicationBuilder builder = new SpringApplicationBuilder(sources);
            builder.web(WebApplicationType.SERVLET);

            if (profiles != null) {
                builder.profiles(profiles);
            }

            // Force the lambda profile and servlet type via system property/env
            System.setProperty("spring.profiles.active", "lambda");

            // CRITICAL FIX: Override the application context factory so that
            // Spring Boot 3.2 cannot create a non-web context regardless of
            // what it thinks the web application type is.
            java.lang.reflect.Field appField = SpringApplicationBuilder.class.getDeclaredField("application");
            appField.setAccessible(true);
            org.springframework.boot.SpringApplication app = (org.springframework.boot.SpringApplication) appField.get(builder);
            app.setWebApplicationType(WebApplicationType.SERVLET);
            app.setApplicationContextFactory(webApplicationType ->
                    new AnnotationConfigServletWebServerApplicationContext());

            ConfigurableApplicationContext ctx = builder.run();
            setFieldValue(FIELD_APPLICATION_CONTEXT, ctx);

            AnnotationConfigServletWebServerApplicationContext servletCtx =
                    (AnnotationConfigServletWebServerApplicationContext) ctx;
            servletCtx.setServletContext(getServletContext());

            jakarta.servlet.ServletRegistration reg = getServletContext()
                    .getServletRegistration("dispatcherServlet");
            if (reg instanceof AwsServletRegistration) {
                ((AwsServletRegistration) reg).setLoadOnStartup(1);
            }

            // Inline the logic from AwsLambdaServletContainerHandler.initialize()
            // to initialize servlets with loadOnStartup >= 0. We can't call it via
            // reflection/MethodHandles because SpringBootLambdaContainerHandler sits
            // between us and it in the hierarchy and also overrides initialize().
            List<AwsServletRegistration> servlets = new ArrayList<>();
            for (Object obj : getServletContext().getServletRegistrations().values()) {
                if (obj instanceof AwsServletRegistration) {
                    servlets.add((AwsServletRegistration) obj);
                }
            }
            servlets.sort(Comparator.comparing(AwsServletRegistration::getName));
            for (AwsServletRegistration servletReg : servlets) {
                if (servletReg.getLoadOnStartup() != -1 && servletReg.getServlet() != null) {
                    try {
                        servletReg.getServlet().init(servletReg.getServletConfig());
                    } catch (jakarta.servlet.ServletException e) {
                        throw new com.amazonaws.serverless.exceptions.ContainerInitializationException(
                                "Could not initialize servlet " + servletReg.getName(), e);
                    }
                }
            }

            setFieldValue(FIELD_INITIALIZED, true);
        } catch (Throwable e) {
            throw new com.amazonaws.serverless.exceptions.ContainerInitializationException(
                    "Could not initialize Spring Boot Lambda container handler",
                    e instanceof Exception ? (Exception) e : new RuntimeException(e));
        } finally {
            com.amazonaws.serverless.proxy.internal.testutils.Timer.stop("SPRINGBOOT2_COLD_START");
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T getFieldValue(String name) throws Exception {
        Field field = com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(this);
    }

    private void setFieldValue(String name, Object value) throws Exception {
        Field field = com.amazonaws.serverless.proxy.spring.SpringBootLambdaContainerHandler.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(this, value);
    }
}
