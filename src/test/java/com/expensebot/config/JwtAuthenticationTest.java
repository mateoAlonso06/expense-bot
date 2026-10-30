package com.expensebot.config;

import com.expensebot.expenses.ListCategoriesUseCase;
import com.expensebot.expenses.infrastructure.web.CategoryController;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, JwtAuthenticationTest.TestJwtConfig.class})
class JwtAuthenticationTest {

    private static final String ISSUER = "http://localhost:8081/realms/expense-bot";
    private static final RSAKey TRUSTED_KEY = generateKey();
    private static final RSAKey UNTRUSTED_KEY = generateKey();

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ListCategoriesUseCase listCategoriesUseCase;

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = sign(TRUSTED_KEY, ISSUER, Instant.now().minusSeconds(3600));

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithInvalidSignature() throws Exception {
        String token = sign(UNTRUSTED_KEY, ISSUER, Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenFromAnotherIssuer() throws Exception {
        String token = sign(TRUSTED_KEY, "http://evil.example/realms/other", Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsValidToken() throws Exception {
        String token = sign(TRUSTED_KEY, ISSUER, Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sign(RSAKey key, String issuer, Instant expiresAt) throws JOSEException {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("user-1")
                .issuer(issuer)
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    /**
     * Replaces the issuer-uri decoder (which would call Keycloak) with one that trusts only TRUSTED_KEY,
     * keeping the same validations: signature, expiration and issuer.
     */
    @TestConfiguration
    static class TestJwtConfig {
        @Bean
        JwtDecoder jwtDecoder() throws JOSEException {
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TRUSTED_KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
            return decoder;
        }
    }
}
