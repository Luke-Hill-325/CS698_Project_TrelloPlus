package com.flowboard.config;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

public class DebugSpringListener implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {
    @Override
    public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
        WebApplicationType type = event.getSpringApplication().getWebApplicationType();
        System.out.println("[DEBUG] SpringApplication.webApplicationType = " + type);
    }
}
