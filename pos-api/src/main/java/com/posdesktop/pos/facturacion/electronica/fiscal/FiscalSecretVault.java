package com.posdesktop.pos.facturacion.electronica.fiscal;

import com.posdesktop.pos.facturacion.electronica.config.DataicoTokenCipher;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Reutiliza AES-GCM y proteccion de archivo existentes, con una clave fiscal independiente. */
@Component
public final class FiscalSecretVault {
    private final DataicoTokenCipher cipher;
    private final FiscalJson json;
    public record Secrets(String environment, String technicalKey, String softwarePin, String certificateBase64, String certificatePassword) {
        @Override public String toString() { return "FiscalSecrets[REDACTED]"; }
    }
    public FiscalSecretVault(@Value("${pos.invoicing.key-file:${POS_INVOICING_KEY_FILE:}}") String configured, FiscalJson json) {
        String local = System.getenv("LOCALAPPDATA");
        Path fallback = local == null || local.isBlank() ? Path.of(System.getProperty("user.home"), ".posdesktop", "secrets", "fiscal.key")
                : Path.of(local, "PosDesktop", "secrets", "fiscal.key");
        cipher = new DataicoTokenCipher(configured == null || configured.isBlank() ? fallback.toString() : configured);
        this.json = json;
    }
    public String encrypt(Secrets secrets) {
        try { return cipher.cifrar(json.write(secrets)); }
        catch (IllegalStateException error) { throw new FiscalConfigurationException("No se pudieron proteger las credenciales DIAN. Revisa los permisos de la carpeta de claves fiscales."); }
    }
    public Secrets decrypt(String encrypted, String environment) {
        if (encrypted == null) return new Secrets(environment, null, null, null, null);
        Secrets secrets;
        try { secrets = json.read(cipher.descifrar(encrypted), Secrets.class); }
        catch (IllegalStateException error) { throw new FiscalConfigurationException("No se pudieron abrir las credenciales DIAN. Restaura la clave fiscal del servidor desde su respaldo."); }
        if (!environment.equals(secrets.environment())) throw new IllegalStateException("La configuracion cifrada pertenece a otro ambiente.");
        return secrets;
    }
}
