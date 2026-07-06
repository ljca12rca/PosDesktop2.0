package com.posdesktop.pos.integration;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class PosDesktopConfig {

    private static final String DEFAULT_API_BASE_URL = "http://localhost:8083/api/v1";
    private final Properties properties = new Properties();

    private PosDesktopConfig() {
        try (InputStream inputStream = getClass().getResourceAsStream("/pos-desktop.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("No fue posible cargar la configuracion del POS Desktop.", exception);
        }
    }

    public static PosDesktopConfig load() {
        return new PosDesktopConfig();
    }

    public String apiBaseUrl() {
        return properties.getProperty("pos.api.base-url", DEFAULT_API_BASE_URL);
    }
}
