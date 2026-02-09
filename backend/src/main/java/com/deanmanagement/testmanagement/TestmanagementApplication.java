package com.deanmanagement.testmanagement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@SpringBootApplication
@Modulithic(sharedModules = "shared")
public class TestmanagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestmanagementApplication.class, args);
    }
}
