package com.posdesktop.pos.integration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Local configuration is shared by the desktop launcher and its bundled API. */
public final class LocalSettings {
    private LocalSettings() { }

    public static Path path() {
        return Path.of(System.getProperty("user.home"), "posdesktop-media", "config", "application.yml");
    }

    public static String read() throws IOException {
        Path file = path();
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            try (InputStream defaults = LocalSettings.class.getResourceAsStream("/application.yml")) {
                if (defaults == null) throw new IOException("No se encontró la configuración inicial.");
                try {
                    Files.copy(defaults, file);
                } catch (java.nio.file.FileAlreadyExistsException ignored) {
                    // Another instance created it first.
                }
            }
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    public static boolean authenticate(String entered) throws IOException {
        Object password = lookup(parse(read()), "pos", "configuracion", "password");
        if (!(password instanceof String value) || value.isBlank()) return false;
        return MessageDigest.isEqual(value.getBytes(StandardCharsets.UTF_8), entered.getBytes(StandardCharsets.UTF_8));
    }

    public static int apiPort() throws IOException {
        return apiPort(parse(read()));
    }

    private static int apiPort(Map<?, ?> root) {
        Object raw = lookup(root, "server", "port");
        String value = raw == null ? "8083" : raw.toString().trim();
        if (value.startsWith("${") && value.endsWith("}")) {
            String expression = value.substring(2, value.length() - 1);
            String[] parts = expression.split(":", 2);
            value = System.getenv().getOrDefault(parts[0], parts.length == 2 ? parts[1] : "");
        }
        try {
            int port = Integer.parseInt(value);
            if (port >= 1 && port <= 65535) return port;
        } catch (NumberFormatException ignored) { }
        throw new IllegalArgumentException("server.port debe ser un puerto entre 1 y 65535.");
    }

    public static void save(String yaml) throws IOException {
        Map<?, ?> root = parse(yaml);
        apiPort(root);
        Object password = lookup(root, "pos", "configuracion", "password");
        if (!(password instanceof String value) || value.isBlank()) {
            throw new IllegalArgumentException("La contraseña de configuración no puede quedar vacía; escríbela entre comillas.");
        }
        Path file = path();
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "application-", ".tmp");
        try {
            Files.writeString(temp, yaml, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Map<?, ?> parse(String text) {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Object value = new Yaml(new SafeConstructor(options)).load(text);
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("El YAML debe contener un mapa de configuración.");
        return map;
    }

    private static Object lookup(Map<?, ?> map, String... keys) {
        Object current = map;
        for (String key : keys) {
            if (!(current instanceof Map<?, ?> section)) return null;
            current = section.get(key);
        }
        return current;
    }
}
