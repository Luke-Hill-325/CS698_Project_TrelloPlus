package com.flowboard.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class LambdaContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        if (applicationContext.getEnvironment().matchesProfiles("ec2")) {
            System.out.println("[LambdaContextInitializer] ec2 profile active, skipping lambda initialization");
            return;
        }

        // Ensure PostgreSQL driver is registered early
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL Driver not found during context initialization", e);
        }

        // Activate lambda profile if not already set
        if (!applicationContext.getEnvironment().matchesProfiles("lambda")) {
            applicationContext.getEnvironment().addActiveProfile("lambda");
        }
    }
}
