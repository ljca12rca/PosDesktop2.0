package com.posdesktop.pos.invoicing.domain.dian;

import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/** Copias propias, salida redactada y borrado explicito; no persistir este objeto. */
public final class DianSecrets implements AutoCloseable {
    private final char[] technicalKey, softwarePin;
    private boolean closed;
    public DianSecrets(char[] technicalKey, char[] softwarePin) {
        if (technicalKey == null || technicalKey.length == 0 || softwarePin == null || softwarePin.length == 0)
            throw new IllegalArgumentException("Clave tecnica y PIN requeridos.");
        this.technicalKey = technicalKey.clone(); this.softwarePin = softwarePin.clone();
    }
    synchronized void update(MessageDigest digest, boolean technical) {
        if (closed) throw new IllegalStateException("Credenciales cerradas.");
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(technical ? technicalKey : softwarePin));
        digest.update(encoded);
        if (encoded.hasArray()) Arrays.fill(encoded.array(), (byte) 0);
    }
    @Override public synchronized void close() {
        Arrays.fill(technicalKey, '\0'); Arrays.fill(softwarePin, '\0'); closed = true;
    }
    @Override public String toString() { return "DianSecrets[REDACTED]"; }
}
