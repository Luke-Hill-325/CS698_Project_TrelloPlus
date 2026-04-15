package com.flowboard.config;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("lambda")
public class LambdaHibernateConfig {

    @Bean
    public HibernatePropertiesCustomizer lambdaHibernatePropertiesCustomizer() {
        return hibernateProperties -> {
            hibernateProperties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            hibernateProperties.put("hibernate.temp.use_jdbc_metadata_defaults", false);
        };
    }
}
