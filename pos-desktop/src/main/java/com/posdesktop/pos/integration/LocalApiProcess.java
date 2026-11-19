package com.posdesktop.pos.integration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

/** Starts the bundled API or the API built in the local Maven checkout. */
public final class LocalApiProcess {
    private static Process process;
    private static String error;

    private LocalApiProcess() { }

    public static void startIfPackaged() {
        try {
            if (portInUse(LocalSettings.apiPort())) return;
            Path apiJar = resolveApiJar();
            Path logs = Path.of(System.getProperty("user.home"), "posdesktop-media", "logs");
            Files.createDirectories(logs);
            String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
            Path java = Path.of(System.getProperty("java.home"), "bin", executable);
            process = new ProcessBuilder(java.toString(), "-Dfile.encoding=UTF-8", "-jar", apiJar.toString(),
                    "--spring.config.location=" + LocalSettings.path().toUri())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.appendTo(logs.resolve("api.log").toFile()))
                    .start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (process != null && process.isAlive()) process.destroy();
            }, "pos-api-shutdown"));
        } catch (Exception exception) {
            error = exception.getMessage();
        }
    }

    private static Path resolveApiJar() throws Exception {
        Path location = Path.of(LocalApiProcess.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Path bundled = location.getParent().resolve("pos-api-local.jar");
        if (Files.isRegularFile(bundled)) return bundled;
        if (Files.isDirectory(location)) {
            // Maven uses target/classes; IntelliJ can use out/production/<module>.
            for (Path root = location; root != null; root = root.getParent()) {
                if (!Files.isRegularFile(root.resolve("pom.xml")) || !Files.isDirectory(root.resolve("pos-api"))) continue;
                Path apiTarget = root.resolve("pos-api/target");
                if (Files.isDirectory(apiTarget)) {
                    try (var jars = Files.list(apiTarget)) {
                        var jar = jars.filter(path -> path.getFileName().toString().matches("pos-api-.+\\.jar"))
                                .max(java.util.Comparator.comparingLong(path -> path.toFile().lastModified()));
                        if (jar.isPresent()) return jar.get();
                    }
                }
                throw new IOException("Compila la API antes de abrir el POS: scripts/run-desktop.ps1 prepara ambos módulos.");
            }
        }
        throw new IOException("No se encontró la API incluida en la instalación.");
    }

    public static String startupError() {
        if (error != null) return error;
        if (process != null && !process.isAlive()) {
            return "La API se detuvo. Revisa posdesktop-media/logs/api.log en tu carpeta de usuario.";
        }
        return null;
    }

    private static boolean portInUse(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 250);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
