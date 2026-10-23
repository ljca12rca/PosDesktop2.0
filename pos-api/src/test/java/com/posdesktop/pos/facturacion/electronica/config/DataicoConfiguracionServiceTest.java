package com.posdesktop.pos.facturacion.electronica.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.posdesktop.pos.modelo.relacional.DataicoConfiguracion;
import com.posdesktop.pos.modelo.relacional.DataicoConsecutivo;
import com.posdesktop.pos.repositorio.relacional.DataicoConfiguracionRepositorio;
import com.posdesktop.pos.repositorio.relacional.DataicoConsecutivoRepositorio;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataicoConfiguracionServiceTest {
    private final DataicoConfiguracionRepositorio configs = mock(DataicoConfiguracionRepositorio.class);
    private final DataicoConsecutivoRepositorio secuencias = mock(DataicoConsecutivoRepositorio.class);
    private final DataicoProperties yaml = new DataicoProperties(true, null, "11111111-1111-4111-8111-111111111111", "secreto-prueba", null,
            "FEE", "18760000001", "1789500096", true, "PRODUCCION", Duration.ofSeconds(5), Duration.ofSeconds(30));
    private final DataicoTokenCipher cipher = mock(DataicoTokenCipher.class);
    private final DataicoConfiguracionService service = new DataicoConfiguracionService(yaml, configs, secuencias, cipher);
    private final DataicoConfiguracion config = new DataicoConfiguracion();
    private final DataicoConsecutivo consecutivo = new DataicoConsecutivo("FEE", 1789500100L);

    @BeforeEach
    void preparar() {
        when(configs.findById(1)).thenReturn(Optional.of(config));
        when(configs.findForUpdate()).thenReturn(Optional.of(config));
        when(configs.findForEmission()).thenReturn(Optional.of(config));
        when(secuencias.findById("FEE")).thenReturn(Optional.of(consecutivo));
        when(secuencias.findByPrefijoForUpdate("FEE")).thenReturn(Optional.of(consecutivo));
    }

    @Test
    void configuraCuentaHabilitacionUrlYTiemposDesdeElPos() {
        var response = service.guardar(conexion("22222222-2222-4222-8222-222222222222", false,
                "https://api.dataico.com/direct/dataico_api/v2/", 7, 25));
        assertThat(response.accountId()).isEqualTo("22222222-2222-4222-8222-222222222222");
        assertThat(response.proveedorHabilitado()).isFalse();
        assertThat(response.baseUrl()).isEqualTo("https://api.dataico.com/direct/dataico_api/v2");
        assertThat(service.paraProveedor().accountId()).isEqualTo(response.accountId());
        assertThat(service.paraProveedor().enabled()).isFalse();
        assertThat(service.paraProveedor().connectTimeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(service.paraProveedor().readTimeout()).isEqualTo(Duration.ofSeconds(25));
        assertThat(service.paraProveedor().tokenParaProveedor()).isEqualTo("secreto-prueba");
        assertThat(consecutivo.getSiguienteNumero()).isEqualTo(1789500100L);
    }

    @Test
    void rechazaUuidInvalidoSinGuardar() {
        assertThatThrownBy(() -> service.guardar(conexion("no-es-uuid", true,
                DataicoProperties.DEFAULT_BASE_URL, 5, 30))).hasMessageContaining("Account ID");
        verify(configs, never()).saveAndFlush(any());
        verify(secuencias, never()).saveAndFlush(any());
    }

    @Test
    void noPermiteMandarElTokenAUnHostNoOficial() {
        for (String url : java.util.List.of("http://api.dataico.com/direct/dataico_api/v2",
                "https://api.dataico.com.ejemplo.test/direct/dataico_api/v2", "https://otro.example.test/direct/dataico_api/v2",
                "https://usuario:clave@api.dataico.com/direct/dataico_api/v2", "https://api.dataico.com/direct/dataico_api/v2?destino=otro")) {
            assertThatThrownBy(() -> service.guardar(conexion("11111111-1111-4111-8111-111111111111", true, url, 5, 30)))
                    .hasMessageContaining("dominio oficial");
        }
        verify(configs, never()).saveAndFlush(any());
        verify(cipher, never()).cifrar(any());
    }

    @Test
    void limitaLosTiemposParaNoSuperarLaEsperaDelPos() {
        assertThatThrownBy(() -> service.guardar(conexion("11111111-1111-4111-8111-111111111111", true,
                DataicoProperties.DEFAULT_BASE_URL, 11, 31))).hasMessageContaining("tiempo de conexión");
        verify(configs, never()).saveAndFlush(any());
    }

    private GuardarConfiguracionDataicoRequest conexion(String account, boolean enabled, String url, int connect, int read) {
        return new GuardarConfiguracionDataicoRequest("FEE", "18760000001", 1789500100L, "PRODUCCION", true,
                0L, 1789500100L, null, account, enabled, url, connect, read);
    }

    @Test
    void guardaElTokenCifradoYLoUsaSinExponerloEnLaRespuesta() {
        when(cipher.cifrar("token_nuevo_de_prueba")).thenReturn("cifrado-de-prueba");
        when(cipher.descifrar("cifrado-de-prueba")).thenReturn("token_nuevo_de_prueba");
        var request = new GuardarConfiguracionDataicoRequest("FEE", "18760000001", 1789500100L,
                "PRODUCCION", true, 0L, 1789500100L, "token_nuevo_de_prueba");

        var response = service.guardar(request);

        assertThat(config.getTokenCifrado()).isEqualTo("cifrado-de-prueba");
        assertThat(service.tokenParaProveedor()).isEqualTo("token_nuevo_de_prueba");
        assertThat(service.paraEmision().tokenParaProveedor()).isEqualTo("token_nuevo_de_prueba");
        assertThat(response.tokenConfigurado()).isTrue();
        assertThat(response.toString()).doesNotContain("token_nuevo_de_prueba", "cifrado-de-prueba");
        assertThat(request.toString()).doesNotContain("token_nuevo_de_prueba");
        assertThat(consecutivo.getSiguienteNumero()).isEqualTo(1789500100L);
    }

    @Test
    void tokenVacioConservaElGuardadoYTokenAusenteUsaYamlComoRespaldo() {
        assertThat(service.tokenParaProveedor()).isEqualTo("secreto-prueba");
        config.setTokenCifrado("cifrado-anterior");
        when(cipher.descifrar("cifrado-anterior")).thenReturn("token_anterior");
        service.guardar(new GuardarConfiguracionDataicoRequest("FEE", "18760000001", 1789500100L,
                "PRODUCCION", true, 0L, 1789500100L, "   "));
        assertThat(config.getTokenCifrado()).isEqualTo("cifrado-anterior");
        assertThat(service.tokenParaProveedor()).isEqualTo("token_anterior");
        verify(cipher, never()).cifrar(any());
    }

    @Test
    void permiteReemplazarElTokenAunqueSeHayaPerdidoLaClaveAnterior() {
        config.setTokenCifrado("cifrado-con-clave-perdida");
        when(cipher.cifrar("token_de_recuperacion")).thenReturn("nuevo-cifrado");
        assertThat(service.consultar().tokenConfigurado()).isTrue();
        service.guardar(new GuardarConfiguracionDataicoRequest("FEE", "18760000001", 1789500100L,
                "PRODUCCION", true, 0L, 1789500100L, "token_de_recuperacion"));
        verify(cipher, never()).descifrar(any());
        assertThat(config.getTokenCifrado()).isEqualTo("nuevo-cifrado");
    }

    @Test
    void rechazaTokenConSaltosDeLineaSinPersistirDatos() {
        var request = new GuardarConfiguracionDataicoRequest("FEE", "18760000001", 1789500100L,
                "PRODUCCION", true, 0L, 1789500100L, "token\r\ninyectado");
        assertThatThrownBy(() -> service.guardar(request)).hasMessageContaining("token completo");
        verify(cipher, never()).cifrar(any());
        verify(configs, never()).saveAndFlush(any());
        verify(secuencias, never()).saveAndFlush(any());
    }

    @Test
    void conservaYamlYLeeElConsecutivoRealSinReiniciarlo() {
        var response = service.consultar();
        assertThat(response.prefijo()).isEqualTo("FEE");
        assertThat(response.resolucion()).isEqualTo("18760000001");
        assertThat(response.siguienteNumero()).isEqualTo(1789500100L);
        assertThat(response.tokenConfigurado()).isTrue();
        assertThat(response.toString()).doesNotContain("secreto-prueba");
        verify(secuencias, never()).saveAndFlush(any());
    }

    @Test
    void usaLaConfiguracionGuardadaEnLasEmisionesSinCambiarCredenciales() {
        service.guardar(request("FEE", "12345", 1789500101L, 0L, 1789500100L));
        var actual = service.paraEmision();
        assertThat(actual.resolutionNumber()).isEqualTo("12345");
        assertThat(actual.environment()).isEqualTo("PRUEBAS");
        assertThat(actual.flexible()).isFalse();
        assertThat(actual.tokenParaProveedor()).isEqualTo("secreto-prueba");
        assertThat(consecutivo.getSiguienteNumero()).isEqualTo(1789500101L);
        verify(configs).findForEmission();
    }

    @Test
    void rechazaFormularioAbiertoAntesDeOtraVenta() {
        assertThatThrownBy(() -> service.guardar(request("FEE", "12345", 1789500101L, 0L, 1789500099L)))
                .hasMessageContaining("Recarga");
        verify(secuencias, never()).saveAndFlush(any());
        verify(configs, never()).saveAndFlush(any());
    }

    @Test
    void rechazaVersionDesactualizada() {
        assertThatThrownBy(() -> service.guardar(request("FEE", "12345", 1789500101L, 1L, 1789500100L)))
                .hasMessageContaining("Recarga");
        verify(configs, never()).saveAndFlush(any());
    }

    @Test
    void noPermiteReutilizarUnConsecutivoAnterior() {
        assertThatThrownBy(() -> service.guardar(request("FEE", "12345", 1789500099L, 0L, 1789500100L)))
                .hasMessageContaining("retroceder");
        assertThat(consecutivo.getSiguienteNumero()).isEqualTo(1789500100L);
    }

    @Test
    void permiteNuevoPrefijoConNumeracionIndependienteSinModificarLaAnterior() {
        service.guardar(request("ABC", "12345", 1L, 0L, 1789500100L));
        assertThat(config.getPrefijo()).isEqualTo("ABC");
        assertThat(consecutivo.getSiguienteNumero()).isEqualTo(1789500100L);
        verify(secuencias).saveAndFlush(argThat(value -> value.getPrefijo().equals("ABC") && value.getSiguienteNumero() == 1));
    }

    @Test
    void noAgregaUnMaximoLocalAlNumeroConfigurado() {
        service.guardar(request("FEE", "12345", 2000000000L, 0L, 1789500100L));
        assertThat(consecutivo.getSiguienteNumero()).isEqualTo(2000000000L);
    }

    @Test
    void rechazaDatosNoValidosAntesDeGuardar() {
        assertThatThrownBy(() -> service.guardar(request("FEE", "abc", 0L, 0L, 1789500100L)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(configs, never()).saveAndFlush(any());
    }

    private GuardarConfiguracionDataicoRequest request(String prefijo, String resolucion, Long siguiente, Long version, Long original) {
        return new GuardarConfiguracionDataicoRequest(prefijo, resolucion, siguiente, "PRUEBAS", false, version, original);
    }
}
