package com.posdesktop.pos.facturacion.electronica.config;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataicoTokenCipherTest {
    @TempDir Path folder;

    @Test
    void cifraConNonceAleatorioYPermiteLeerTrasReiniciarConLaMismaClave() throws Exception {
        Path key = folder.resolve("dataico.key");
        var cipher = new DataicoTokenCipher(key);
        String encrypted = cipher.cifrar("token_ficticio_de_prueba");
        assertThat(encrypted).doesNotContain("token_ficticio_de_prueba");
        assertThat(cipher.cifrar("token_ficticio_de_prueba")).isNotEqualTo(encrypted);
        assertThat(Files.size(key)).isEqualTo(32);
        assertThat(new DataicoTokenCipher(key).descifrar(encrypted)).isEqualTo("token_ficticio_de_prueba");
    }

    @Test
    void rechazaTextoManipuladoSinExponerloEnElError() {
        var cipher = new DataicoTokenCipher(folder.resolve("dataico.key"));
        String encrypted = cipher.cifrar("token_ficticio_de_prueba");
        byte[] bytes = Base64.getDecoder().decode(encrypted.substring(3));
        bytes[bytes.length - 1] ^= 1;
        assertThatThrownBy(() -> cipher.descifrar("v1:" + Base64.getEncoder().encodeToString(bytes)))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("token_ficticio");
    }

    @Test
    void noRegeneraUnaClavePerdidaAlConsultarElToken() {
        var original = new DataicoTokenCipher(folder.resolve("original.key"));
        String encrypted = original.cifrar("token_ficticio_de_prueba");
        Path missing = folder.resolve("inexistente.key");
        assertThatThrownBy(() -> new DataicoTokenCipher(missing).descifrar(encrypted))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("configura un token nuevo");
        assertThat(Files.exists(missing)).isFalse();
    }
}
