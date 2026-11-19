package com.posdesktop.pos.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalSettingsTest {
    @TempDir Path home;

    @Test
    void protectsAndPersistsConfigurationAndRejectsInvalidChanges() throws Exception {
        String previous = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        try {
            // Independent from the application's configured password and environment.
            Files.createDirectories(LocalSettings.path().getParent());
            String original = """
                    server:
                      port: 8083
                    pos:
                      configuracion:
                        password: ''
                    """;
            Files.writeString(LocalSettings.path(), original);
            assertFalse(LocalSettings.authenticate(""));
            String configured = original.replace("password: ''", "password: 'clave de prueba'")
                    .replace("port: 8083", "port: 9123");
            LocalSettings.save(configured);
            assertTrue(LocalSettings.authenticate("clave de prueba"));
            assertFalse(LocalSettings.authenticate("incorrecta"));
            assertEquals(9123, LocalSettings.apiPort());
            assertTrue(Files.exists(LocalSettings.path()));
            assertThrows(IllegalArgumentException.class, () -> LocalSettings.save(configured.replace("port: 9123", "port: 99999")));
            assertEquals(configured, LocalSettings.read());
            assertThrows(RuntimeException.class, () -> LocalSettings.save("spring: ["));
            assertEquals(configured, LocalSettings.read());
        } finally {
            System.setProperty("user.home", previous);
        }
    }
}
