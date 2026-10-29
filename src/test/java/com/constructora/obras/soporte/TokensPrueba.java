package com.constructora.obras.soporte;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tokens firmados con una clave RSA creada al iniciar las pruebas. La clave pública se escribe en un archivo
 * temporal y el perfil de pruebas valida con ella (PruebaIntegracion), con el mismo emisor y audiencia que en desarrollo.
 */
public final class TokensPrueba {

    public static final String EMISOR = "http://localhost:8080/realms/supervision";
    public static final String AUDIENCIA = "plataforma-api";

    private static final KeyPair CLAVES = generarClaves();
    private static final Path CLAVE_PUBLICA = escribirClavePublica(CLAVES);

    private TokensPrueba() {
    }

    public static String ubicacionClavePublica() {
        return CLAVE_PUBLICA.toUri().toString();
    }

    /** Token válido de un usuario con sus roles, emitido para el ERP. */
    public static String valido(String usuarioId, String... roles) {
        return firmar(CLAVES, datos(usuarioId, roles).build());
    }

    /** Token válido emitido para otra aplicación (azp): portal, app-obra o un cliente de servicio. */
    public static String desde(String azp, String usuarioId, String... roles) {
        return firmar(CLAVES, datos(usuarioId, roles).claim("azp", azp).build());
    }

    public static JWTClaimsSet.Builder datos(String usuarioId, String... roles) {
        Instant ahora = Instant.now();
        return new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .issuer(EMISOR)
                .audience(List.of(AUDIENCIA, "account"))
                .subject(usuarioId)
                .claim("azp", "erp")
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .issueTime(Date.from(ahora))
                .expirationTime(Date.from(ahora.plusSeconds(300)));
    }

    public static String firmar(JWTClaimsSet datos) {
        return firmar(CLAVES, datos);
    }

    /** Firma con otra clave: la firma no coincide con la que conoce el servicio. */
    public static String firmadoConOtraClave(JWTClaimsSet datos) {
        return firmar(generarClaves(), datos);
    }

    private static String firmar(KeyPair claves, JWTClaimsSet datos) {
        try {
            SignedJWT token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("prueba").build(), datos);
            token.sign(new RSASSASigner((RSAPrivateKey) claves.getPrivate()));
            return token.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static KeyPair generarClaves() {
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance("RSA");
            generador.initialize(2048);
            return generador.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path escribirClavePublica(KeyPair claves) {
        try {
            Path archivo = Files.createTempFile("obras-clave-prueba", ".pem");
            archivo.toFile().deleteOnExit();
            String pem = "-----BEGIN PUBLIC KEY-----\n"
                    + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                            .encodeToString(claves.getPublic().getEncoded())
                    + "\n-----END PUBLIC KEY-----\n";
            return Files.writeString(archivo, pem);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
