package com.posdesktop.pos.facturacion.electronica.fiscal;

import com.posdesktop.pos.invoicing.application.ProviderType;
import com.posdesktop.pos.modelo.relacional.DianSettings;
import com.posdesktop.pos.repositorio.relacional.*;
import java.io.ByteArrayInputStream;
import java.security.KeyStore;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FiscalSettingsService {
    private final FiscalProviderSettingsRepository providers;
    private final DianSettingsRepository dian;
    private final FiscalSecretVault vault;
    private final FiscalJson json;
    public FiscalSettingsService(FiscalProviderSettingsRepository providers, DianSettingsRepository dian, FiscalSecretVault vault, FiscalJson json) {
        this.providers = providers; this.dian = dian; this.vault = vault; this.json = json;
    }
    public record ProviderSettings(String provider, long version, boolean directEmissionEnabled) { }
    public record SaveProvider(String provider, long version) { }
    public record DianResponse(String environment, long version, DianConfiguration configuration,
            boolean technicalKeyConfigured, boolean softwarePinConfigured, boolean certificateConfigured) { }
    public record SaveDian(long version, DianConfiguration configuration, String technicalKey,
            String softwarePin, String certificateBase64, String certificatePassword) {
        @Override public String toString() { return "SaveDian[version=" + version + ", secrets=REDACTED]"; }
    }
    @Transactional(readOnly = true)
    public ProviderSettings provider() {
        var config = providers.findById(1).orElseThrow();
        return new ProviderSettings(config.getProveedor(), config.getVersion(), false);
    }
    @Transactional
    public ProviderSettings saveProvider(SaveProvider request) {
        var config = providers.lockSettings().orElseThrow();
        if (config.getVersion() != request.version()) throw new FiscalConflictException("La configuracion cambio. Recarga antes de guardar.");
        ProviderType type;
        try { type = ProviderType.valueOf(request.provider()); }
        catch (RuntimeException error) { throw new IllegalArgumentException("Selecciona DATAICO o DIAN_DIRECT."); }
        config.setProveedor(type.name()); providers.saveAndFlush(config);
        return new ProviderSettings(config.getProveedor(), config.getVersion(), false);
    }
    @Transactional(readOnly = true)
    public DianResponse dian(String environment) { return response(dian.findById(environment(environment)).orElseThrow()); }

    @Transactional
    public DianResponse saveDian(String environment, SaveDian request) {
        environment = environment(environment);
        var config = dian.lockSettings(environment).orElseThrow();
        if (config.getVersion() != request.version()) throw new FiscalConflictException("La configuracion DIAN cambio. Recarga antes de guardar.");
        if (request.configuration() == null) throw new IllegalArgumentException("Completa la configuracion del emisor y la resolucion.");
        request.configuration().validate();
        var old = vault.decrypt(config.getSecretosCifrados(), environment);
        String key = replace(request.technicalKey(), old.technicalKey(), 200);
        String pin = replace(request.softwarePin(), old.softwarePin(), 100);
        String certificate = old.certificateBase64();
        String password = old.certificatePassword();
        if (request.certificateBase64() != null && !request.certificateBase64().isBlank()) {
            certificate = request.certificateBase64(); password = request.certificatePassword();
            validateCertificate(certificate, password);
        } else if (request.certificatePassword() != null && !request.certificatePassword().isEmpty()) {
            password = request.certificatePassword(); validateCertificate(certificate, password);
        }
        config.setDatosJson(json.write(request.configuration()));
        config.setSecretosCifrados(vault.encrypt(new FiscalSecretVault.Secrets(environment, key, pin, certificate, password)));
        dian.saveAndFlush(config);
        return response(config);
    }
    private DianResponse response(DianSettings config) {
        var secrets = vault.decrypt(config.getSecretosCifrados(), config.getAmbiente());
        return new DianResponse(config.getAmbiente(), config.getVersion(), config.getDatosJson() == null ? null
                : json.read(config.getDatosJson(), DianConfiguration.class), present(secrets.technicalKey()),
                present(secrets.softwarePin()), present(secrets.certificateBase64()));
    }
    public static String environment(String value) {
        if (!Set.of("PRUEBAS", "PRODUCCION").contains(value == null ? "" : value)) throw new IllegalArgumentException("Ambiente invalido.");
        return value;
    }
    private static String replace(String value, String old, int max) {
        if (value == null || value.isBlank()) return old;
        if (value.length() > max || !value.matches("[\\x21-\\x7E]+")) throw new IllegalArgumentException("Clave o PIN invalido: usa el valor completo sin espacios.");
        return value;
    }
    private static boolean present(String value) { return value != null && !value.isEmpty(); }
    private static void validateCertificate(String encoded, String password) {
        byte[] bytes = null; char[] pass = password == null ? null : password.toCharArray();
        try {
            if (encoded == null || encoded.length() > 700_000 || password == null || password.length() > 512) throw new IllegalArgumentException();
            bytes = Base64.getDecoder().decode(encoded);
            if (bytes.length > 512_000) throw new IllegalArgumentException();
            var store = KeyStore.getInstance("PKCS12"); store.load(new ByteArrayInputStream(bytes), pass);
            boolean found = false;
            for (String alias : Collections.list(store.aliases())) {
                if (store.isKeyEntry(alias) && store.getKey(alias, pass) instanceof java.security.PrivateKey) {
                    ((java.security.cert.X509Certificate) store.getCertificate(alias)).checkValidity(); found = true;
                }
            }
            if (!found) throw new IllegalArgumentException();
        } catch (Exception error) { throw new IllegalArgumentException("El certificado PKCS#12 no contiene una clave privada vigente o la contrasena es incorrecta."); }
        finally { if (bytes != null) Arrays.fill(bytes,(byte)0); if (pass != null) Arrays.fill(pass,'\0'); }
    }
}
