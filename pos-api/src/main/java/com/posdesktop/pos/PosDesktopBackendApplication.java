package com.posdesktop.pos;

import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PosDesktopBackendApplication {

    public static void main(String[] args) {
        configureApplicationTimezone();
        SpringApplication.run(PosDesktopBackendApplication.class, args);
    }

    private static void configureApplicationTimezone() {
        String configuredTimezone = System.getProperty("pos.timezone");
        if (configuredTimezone == null || configuredTimezone.isBlank()) {
            configuredTimezone = System.getenv().getOrDefault("POS_TIMEZONE", "America/Bogota");
        }

        ZoneId zoneId = ZoneId.of(configuredTimezone);
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        System.setProperty("user.timezone", zoneId.getId());
    }
}
