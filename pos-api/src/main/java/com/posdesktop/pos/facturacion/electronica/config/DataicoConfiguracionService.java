package com.posdesktop.pos.facturacion.electronica.config;

import com.posdesktop.pos.modelo.relacional.DataicoConfiguracion;
import com.posdesktop.pos.modelo.relacional.DataicoConsecutivo;
import com.posdesktop.pos.repositorio.relacional.DataicoConfiguracionRepositorio;
import com.posdesktop.pos.repositorio.relacional.DataicoConsecutivoRepositorio;
import java.util.Locale;
import java.time.Duration;
import java.net.URI;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DataicoConfiguracionService {
    private final DataicoProperties yaml;
    private final DataicoConfiguracionRepositorio configuraciones;
    private final DataicoConsecutivoRepositorio consecutivos;
    private final DataicoTokenCipher tokenCipher;

    public DataicoConfiguracionService(DataicoProperties yaml, DataicoConfiguracionRepositorio configuraciones,
            DataicoConsecutivoRepositorio consecutivos, DataicoTokenCipher tokenCipher) {
        this.yaml = yaml;
        this.configuraciones = configuraciones;
        this.consecutivos = consecutivos;
        this.tokenCipher = tokenCipher;
    }

    @Transactional
    public void inicializar() { configuraciones.asegurarRegistro(); }

    @Transactional(readOnly = true)
    public ConfiguracionDataicoResponse consultar() {
        DataicoConfiguracion config = configuraciones.findById(1).orElseThrow();
        DataicoProperties actual = combinar(config, false);
        long numero = consecutivos.findById(actual.prefix()).map(DataicoConsecutivo::getSiguienteNumero)
                .orElse(numeroInicial());
        return respuesta(config, actual, numero);
    }

    /** El bloqueo vive hasta finalizar la transacción de emisión: prefijo, resolución y secuencia son coherentes. */
    @Transactional(readOnly = true)
    public DataicoProperties paraEmision() {
        return combinar(configuraciones.findForEmission().orElseThrow(), true);
    }

    @Transactional(readOnly = true)
    public String tokenParaProveedor() {
        DataicoConfiguracion config = configuraciones.findById(1).orElseThrow();
        return config.getTokenCifrado() == null ? yaml.tokenParaProveedor() : tokenCipher.descifrar(config.getTokenCifrado());
    }

    @Transactional(readOnly = true)
    public DataicoProperties paraProveedor() {
        return combinar(configuraciones.findById(1).orElseThrow(), true);
    }

    @Transactional
    public ConfiguracionDataicoResponse guardar(GuardarConfiguracionDataicoRequest request) {
        DataicoConfiguracion config = configuraciones.findForUpdate().orElseThrow();
        DataicoProperties anterior = combinar(config, false);
        DataicoConsecutivo actual = consecutivos.findByPrefijoForUpdate(anterior.prefix()).orElse(null);
        long numeroActual = actual == null ? numeroInicial() : actual.getSiguienteNumero();
        if (!config.getVersion().equals(request.version()) || numeroActual != request.siguienteNumeroActual()) {
            throw new IllegalArgumentException("La configuración o el consecutivo cambió. Recarga los datos antes de guardar.");
        }
        String prefijo = request.prefijo().trim().toUpperCase(Locale.ROOT);
        if (!prefijo.matches("[A-Z0-9]{1,40}") || !request.resolucion().matches("[0-9]{1,40}")
                || !("PRODUCCION".equals(request.ambiente()) || "PRUEBAS".equals(request.ambiente()))
                || request.flexible() == null || request.siguienteNumero() == null || request.siguienteNumero() < 1) {
            throw new IllegalArgumentException("Revisa el prefijo, resolución, ambiente y próximo número positivo.");
        }
        DataicoConsecutivo destino = prefijo.equals(anterior.prefix()) ? actual
                : consecutivos.findByPrefijoForUpdate(prefijo).orElse(null);
        if (destino != null && request.siguienteNumero() < destino.getSiguienteNumero()) {
            throw new IllegalArgumentException("No puedes retroceder el consecutivo ya reservado para " + prefijo + ".");
        }
        String accountId = request.accountId() == null ? anterior.accountId() : request.accountId().trim().toLowerCase(Locale.ROOT);
        boolean habilitado = request.proveedorHabilitado() == null ? anterior.enabled() : request.proveedorHabilitado();
        if (accountId != null && !accountId.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new IllegalArgumentException("El Account ID debe ser el UUID completo de la empresa en Dataico.");
        }
        if (habilitado && (accountId == null || accountId.isBlank())) {
            throw new IllegalArgumentException("Configura el Account ID para habilitar Dataico.");
        }
        String baseUrl = validarUrl(request.baseUrl() == null ? anterior.baseUrl() : request.baseUrl());
        int connect = request.connectTimeoutSeconds() == null ? (int) anterior.connectTimeout().toSeconds() : request.connectTimeoutSeconds();
        int read = request.readTimeoutSeconds() == null ? (int) anterior.readTimeout().toSeconds() : request.readTimeoutSeconds();
        if (connect < 1 || connect > 10 || read < 1 || read > 30) {
            throw new IllegalArgumentException("El tiempo de conexión debe ser de 1 a 10 segundos y el de respuesta de 1 a 30.");
        }
        String token = request.token() == null ? "" : request.token().strip();
        if (!token.isEmpty()) {
            if (!token.matches("[\\x21-\\x7E]{8,512}")) {
                throw new IllegalArgumentException("Pega el token completo, sin espacios internos ni saltos de línea (8 a 512 caracteres).");
            }
            config.setTokenCifrado(tokenCipher.cifrar(token));
        }
        if (destino == null) destino = new DataicoConsecutivo(prefijo, request.siguienteNumero());
        else destino.setSiguienteNumero(request.siguienteNumero());
        consecutivos.saveAndFlush(destino);
        config.setPrefijo(prefijo);
        config.setResolucion(request.resolucion());
        config.setAmbiente(request.ambiente());
        config.setFlexible(request.flexible());
        config.setAccountId(accountId);
        config.setHabilitado(habilitado);
        config.setBaseUrl(baseUrl);
        config.setConnectTimeoutSeconds(connect);
        config.setReadTimeoutSeconds(read);
        configuraciones.saveAndFlush(config);
        return respuesta(config, combinar(config, false), destino.getSiguienteNumero());
    }

    private DataicoProperties combinar(DataicoConfiguracion config, boolean incluirToken) {
        String token = incluirToken && config.getTokenCifrado() != null ? tokenCipher.descifrar(config.getTokenCifrado()) : yaml.tokenParaProveedor();
        return new DataicoProperties(config.getHabilitado() == null ? yaml.enabled() : config.getHabilitado(),
                config.getBaseUrl() == null ? (yaml.baseUrl() == null || yaml.baseUrl().isBlank() ? DataicoProperties.DEFAULT_BASE_URL : yaml.baseUrl()) : config.getBaseUrl(),
                config.getAccountId() == null ? yaml.accountId() : config.getAccountId(), yaml.authToken(), token,
                config.getPrefijo() == null ? yaml.prefix() : config.getPrefijo(),
                config.getResolucion() == null ? yaml.resolutionNumber() : config.getResolucion(), yaml.initialNumber(),
                config.getFlexible() == null ? yaml.flexible() : config.getFlexible(),
                config.getAmbiente() == null ? yaml.environment() : config.getAmbiente(),
                config.getConnectTimeoutSeconds() == null ? (yaml.connectTimeout() == null ? Duration.ofSeconds(5) : yaml.connectTimeout()) : Duration.ofSeconds(config.getConnectTimeoutSeconds()),
                config.getReadTimeoutSeconds() == null ? (yaml.readTimeout() == null ? Duration.ofSeconds(30) : yaml.readTimeout()) : Duration.ofSeconds(config.getReadTimeoutSeconds()));
    }

    private long numeroInicial() {
        String numero = yaml.initialNumber() == null ? "" : yaml.initialNumber().replaceAll("\\D", "");
        return numero.isBlank() ? 1 : Long.parseLong(numero);
    }

    private ConfiguracionDataicoResponse respuesta(DataicoConfiguracion config, DataicoProperties actual, long numero) {
        return new ConfiguracionDataicoResponse(actual.prefix(), actual.resolutionNumber(), numero, actual.environment(),
                actual.flexible(), config.getVersion(), actual.accountId(), actual.enabled(),
                config.getTokenCifrado() != null || (yaml.tokenParaProveedor() != null && !yaml.tokenParaProveedor().isBlank()),
                actual.baseUrl(), (int) actual.connectTimeout().toSeconds(), (int) actual.readTimeout().toSeconds());
    }

    public static String validarUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !"api.dataico.com".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !uri.getPath().matches("/direct/dataico_api(?:/v[0-9]+)?/?")) throw new IllegalArgumentException();
            return value.trim().replaceAll("/+$", "");
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("La URL debe usar HTTPS y el dominio oficial api.dataico.com, sin credenciales ni parámetros.");
        }
    }
}
