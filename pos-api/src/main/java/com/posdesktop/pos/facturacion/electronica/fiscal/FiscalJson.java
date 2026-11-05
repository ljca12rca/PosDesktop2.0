package com.posdesktop.pos.facturacion.electronica.fiscal;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public final class FiscalJson {
    private final JsonMapper mapper = JsonMapper.builder().build();
    public String write(Object value) { return mapper.writeValueAsString(value); }
    public <T> T read(String value, Class<T> type) { return mapper.readValue(value, type); }
    public boolean equivalent(String left,String right) { return mapper.readTree(left).equals(mapper.readTree(right)); }
    public String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
