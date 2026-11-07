package org.alim.user.config;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class PemKeys {

    private PemKeys() {
    }

    static RSAPrivateKey privateKey(String pem) {

        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decode(pem)));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("app.jwt.private-key is not a valid PKCS#8 RSA PEM key", exception);
        }
    }

    static RSAPublicKey publicKey(String pem) {

        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decode(pem)));
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("app.jwt.public-key is not a valid X.509 RSA PEM key", exception);
        }
    }

    private static byte[] decode(String pem) {

        String base64 = pem
                .replace("\\n", "")
                .replaceAll("-----(BEGIN|END)[A-Z ]+-----", "")
                .replaceAll("\\s", "");

        return Base64.getDecoder().decode(base64);
    }
}
