package com.deanmanagement.testmanagement.user.internal.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.admin")
@Getter
@Setter
public class AdminConfig {

    private String email;
    private String password;
    private String displayName;
}
