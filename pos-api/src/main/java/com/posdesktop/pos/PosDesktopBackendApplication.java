package com.posdesktop.pos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PosDesktopBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(PosDesktopBackendApplication.class, args);
    }
}
