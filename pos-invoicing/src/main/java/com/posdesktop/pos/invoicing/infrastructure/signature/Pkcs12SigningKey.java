package com.posdesktop.pos.invoicing.infrastructure.signature;

import java.io.ByteArrayInputStream;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.*;

/** Material efimero: nunca serializar ni registrar claves o contrasenas. */
public final class Pkcs12SigningKey {
    private final PrivateKey key;
    private final List<X509Certificate> chain;
    private Pkcs12SigningKey(PrivateKey key, List<X509Certificate> chain) { this.key=key; this.chain=List.copyOf(chain); }
    public static Pkcs12SigningKey load(byte[] bytes, char[] password, Instant at) {
        try {
            if (bytes == null || bytes.length > 512_000 || password == null) throw new GeneralSecurityException();
            var store=KeyStore.getInstance("PKCS12"); store.load(new ByteArrayInputStream(bytes),password);
            List<String> aliases=new ArrayList<>();
            for (String alias:Collections.list(store.aliases())) if(store.isKeyEntry(alias)) aliases.add(alias);
            if(aliases.size()!=1) throw new GeneralSecurityException();
            String alias=aliases.getFirst();
            if(!(store.getKey(alias,password) instanceof PrivateKey key) || !"RSA".equals(key.getAlgorithm())) throw new GeneralSecurityException();
            var certificates=store.getCertificateChain(alias);
            if(certificates==null || certificates.length==0) throw new GeneralSecurityException();
            List<X509Certificate> chain=new ArrayList<>();
            for(var c:certificates) { var cert=(X509Certificate)c; cert.checkValidity(Date.from(at)); chain.add(cert); }
            X509Certificate leaf=chain.getFirst();
            if(!(leaf.getPublicKey() instanceof RSAPublicKey rsa) || rsa.getModulus().bitLength()<2048) throw new GeneralSecurityException();
            boolean[] usage=leaf.getKeyUsage();
            if(usage!=null && !usage[0] && (usage.length<2 || !usage[1])) throw new GeneralSecurityException();
            for(int i=0;i<chain.size()-1;i++) {
                if(!chain.get(i).getIssuerX500Principal().equals(chain.get(i+1).getSubjectX500Principal())) throw new GeneralSecurityException();
                chain.get(i).verify(chain.get(i+1).getPublicKey());
            }
            // Prueba criptografica de correspondencia entre clave privada y certificado.
            var probe=Signature.getInstance("SHA256withRSA");byte[] challenge=new byte[32];new SecureRandom().nextBytes(challenge);
            probe.initSign(key);probe.update(challenge);byte[] signature=probe.sign();
            probe.initVerify(leaf);probe.update(challenge);if(!probe.verify(signature))throw new GeneralSecurityException();
            return new Pkcs12SigningKey(key,chain);
        } catch(Exception e) { throw new IllegalArgumentException("Certificado PKCS#12 invalido: revisa contrasena, vigencia, clave RSA de al menos 2048 bits y una sola identidad de firma."); }
    }
    PrivateKey privateKey(){return key;}
    public List<X509Certificate> chain(){return chain;}
    @Override public String toString(){return "Pkcs12SigningKey[REDACTED]";}
}
