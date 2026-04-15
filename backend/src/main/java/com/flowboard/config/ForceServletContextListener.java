package com.flowboard.config;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.ApplicationListener;

/**
 * Forces the SpringApplication to create a servlet web server application context
 * regardless of the detected or configured web application type.
 *
 * This is a workaround for an issue in aws-serverless-java-container-springboot3
 * with Spring Boot 3.2 where the context is incorrectly created as a non-web
 * AnnotationConfigApplicationContext in the Lambda environment.
 */
public class ForceServletContextListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        event.getSpringApplication().setWebApplicationType(WebApplicationType.SERVLET);
        event.getSpringApplication().setApplicationContextFactory(webAppType ->
                new AnnotationConfigServletWebServerApplicationContext());
        System.out.println("[ForceServletContextListener] Forced SERVLET web application type and context factory");
    }
}
