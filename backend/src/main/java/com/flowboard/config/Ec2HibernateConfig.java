package com.flowboard.config;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("ec2")
public class Ec2HibernateConfig {

    @Bean
    public HibernatePropertiesCustomizer ec2HibernatePropertiesCustomizer() {
        return hibernateProperties -> {
            hibernateProperties.put("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        };
    }
}
