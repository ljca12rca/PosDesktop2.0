package com.posdesktop.pos.facturacion.electronica.config;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** AES-GCM: la clave local se conserva fuera del repositorio y de la base de datos. */
@Component
public class DataicoTokenCipher {
    private final Path keyFile;
    private final SecureRandom random = new SecureRandom();

    @Autowired
    public DataicoTokenCipher(@Value("${pos.facturacion-electronica.dataico.token-key-file:}") String configuredPath) {
        this(configuredPath == null || configuredPath.isBlank() ? defaultKeyFile() : Path.of(configuredPath));
    }

    DataicoTokenCipher(Path keyFile) { this.keyFile = keyFile; }

    public synchronized String cifrar(String token) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(claveParaGuardar(), "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
            byte[] result = ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
            return "v1:" + Base64.getEncoder().encodeToString(result);
        } catch (IOException | GeneralSecurityException error) {
            throw new IllegalStateException("No fue posible proteger el token. Revisa los permisos de la carpeta local de claves.");
        }
    }

    public String descifrar(String value) {
        try {
            if (value == null || !value.startsWith("v1:")) throw new GeneralSecurityException();
            byte[] key = Files.readAllBytes(keyFile);
            if (key.length != 32) throw new GeneralSecurityException();
            byte[] packed = Base64.getDecoder().decode(value.substring(3));
            if (packed.length < 29) throw new GeneralSecurityException();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, Arrays.copyOfRange(packed, 0, 12)));
            return new String(cipher.doFinal(packed, 12, packed.length - 12), StandardCharsets.UTF_8);
        } catch (IOException | GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalStateException("No se pudo leer el token guardado. Restaura la clave local o configura un token nuevo desde el POS.");
        }
    }

    private byte[] claveParaGuardar() throws IOException {
        Files.createDirectories(keyFile.toAbsolutePath().getParent());
        try (FileChannel channel = FileChannel.open(keyFile, StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
                var lock = channel.lock()) {
            protegerArchivo();
            if (channel.size() == 0) {
                byte[] key = new byte[32];
                random.nextBytes(key);
                ByteBuffer buffer = ByteBuffer.wrap(key);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
                return key;
            }
            if (channel.size() != 32) throw new IOException("Clave inválida");
            ByteBuffer buffer = ByteBuffer.allocate(32);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) throw new IOException("Clave incompleta");
            }
            return buffer.array();
        }
    }

    private void protegerArchivo() throws IOException {
        if (Files.getFileStore(keyFile).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------"));
        } else {
            AclFileAttributeView acl = Files.getFileAttributeView(keyFile, AclFileAttributeView.class);
            if (acl == null) throw new IOException("No hay control de permisos");
            acl.setAcl(List.of(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(acl.getOwner())
                    .setPermissions(EnumSet.allOf(AclEntryPermission.class)).build()));
        }
    }

    private static Path defaultKeyFile() {
        String localAppData = System.getenv("LOCALAPPDATA");
        return localAppData == null || localAppData.isBlank()
                ? Path.of(System.getProperty("user.home"), ".posdesktop", "secrets", "dataico-token.key")
                : Path.of(localAppData, "PosDesktop", "secrets", "dataico-token.key");
    }
}
