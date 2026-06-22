package com.financetracker.config;

import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Parses RSA keys supplied as PEM (or bare base64) strings from configuration. */
final class RsaKeys {

  private RsaKeys() {}

  static RSAPublicKey parsePublic(String pem) {
    try {
      byte[] der = der(pem);
      return (RSAPublicKey)
          KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    } catch (Exception e) {
      throw new IllegalStateException("Invalid RSA public key configuration", e);
    }
  }

  static RSAPrivateKey parsePrivate(String pem) {
    try {
      byte[] der = der(pem);
      return (RSAPrivateKey)
          KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (Exception e) {
      throw new IllegalStateException("Invalid RSA private key configuration", e);
    }
  }

  private static byte[] der(String pem) {
    String base64 = pem.replaceAll("-----(BEGIN|END)[^-]*-----", "").replaceAll("\\s", "");
    return Base64.getDecoder().decode(base64);
  }
}
