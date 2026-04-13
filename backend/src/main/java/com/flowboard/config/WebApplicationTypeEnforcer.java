package com.flowboard.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebApplicationTypeEnforcer implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {
    
    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        SpringApplication app = event.getSpringApplication();
        app.setWebApplicationType(WebApplicationType.SERVLET);
        System.out.println("[WebApplicationTypeEnforcer] Set web application type to SERVLET");
    }
}
