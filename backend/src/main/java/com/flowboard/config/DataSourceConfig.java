package com.flowboard.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;

@Configuration
@Profile("lambda")
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        // Explicitly load the driver
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL Driver not found", e);
        }

        String dbUrl = System.getenv("DB_URL");
        String dbUsername = System.getenv("DB_USERNAME");
        String dbPassword = System.getenv("DB_PASSWORD");

        // Fallback for local development when env vars are not set
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = "jdbc:postgresql://localhost:5432/flowboard";
        }
        if (dbUsername == null || dbUsername.isBlank()) {
            dbUsername = "flowboard";
        }
        if (dbPassword == null || dbPassword.isBlank()) {
            dbPassword = "flowboard_password";
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUsername);
        config.setPassword(dbPassword);
        config.setDriverClassName("org.postgresql.Driver");

        // Lambda-optimized pool settings to avoid connection leaks and minimize cold start overhead
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(20000);
        config.setIdleTimeout(0);
        config.setMaxLifetime(300000); // 5 minutes
        config.setInitializationFailTimeout(-1);

        return new HikariDataSource(config);
    }
}
