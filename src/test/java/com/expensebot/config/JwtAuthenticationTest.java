package com.expensebot.config;

import com.expensebot.accounts.ProvisionUserProfileUseCase;
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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CategoryController.class)
@Import({SecurityConfig.class, JwtAuthenticationTest.TestJwtConfig.class})
@Tag("web")
class JwtAuthenticationTest {

    private static final String ISSUER = "http://localhost:8081/realms/expense-bot";
    private static final String API_AUDIENCE = "expense-bot-api";
    private static final UUID SUBJECT = UUID.randomUUID();
    private static final RSAKey TRUSTED_KEY = generateKey();
    private static final RSAKey UNTRUSTED_KEY = generateKey();

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ListCategoriesUseCase listCategoriesUseCase;
    @MockitoBean
    private ProvisionUserProfileUseCase provisionUserProfile;

    @Test
    void rejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        String token = sign(TRUSTED_KEY, ISSUER, API_AUDIENCE, Instant.now().minusSeconds(3600));

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithInvalidSignature() throws Exception {
        String token = sign(UNTRUSTED_KEY, ISSUER, API_AUDIENCE, Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenFromAnotherIssuer() throws Exception {
        String token = sign(TRUSTED_KEY, "http://evil.example/realms/other", API_AUDIENCE, Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenWithoutAudience() throws Exception {
        // e.g. a token from admin-cli: same realm and signing key, but not issued for this API
        String token = sign(TRUSTED_KEY, ISSUER, null, Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsTokenForAnotherAudience() throws Exception {
        String token = sign(TRUSTED_KEY, ISSUER, "another-api", Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsValidToken() throws Exception {
        String token = sign(TRUSTED_KEY, ISSUER, API_AUDIENCE, Instant.now().plusSeconds(3600));

        mockMvc.perform(get("/api/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        verify(provisionUserProfile).execute(SUBJECT, "ana@example.com");
    }

    private static RSAKey generateKey() {
        try {
            return new RSAKeyGenerator(2048).generate();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sign(RSAKey key, String issuer, String audience, Instant expiresAt) throws JOSEException {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(SUBJECT.toString())
                .claim("email", "ana@example.com")
                .issuer(issuer)
                .expirationTime(Date.from(expiresAt));
        if (audience != null) {
            // Keycloak adds the realm's "account" client next to ours, so aud is a list with more than one value
            claims.audience(List.of("account", audience));
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    /**
     * Replaces the issuer-uri decoder (which would call Keycloak) with one that trusts only TRUSTED_KEY.
     * The validations mirror what Spring Boot builds from application.yaml (expiration, issuer-uri and audiences),
     * and read the same bound properties, so a wrong or missing audience setting fails these tests.
     */
    @TestConfiguration
    static class TestJwtConfig {
        @Bean
        JwtDecoder jwtDecoder(OAuth2ResourceServerProperties properties) throws JOSEException {
            OAuth2ResourceServerProperties.Jwt jwt = properties.getJwt();
            List<String> audiences = jwt.getAudiences();
            List<OAuth2TokenValidator<Jwt>> validators = List.of(
                    new JwtIssuerValidator(jwt.getIssuerUri()),
                    new JwtClaimValidator<List<String>>(JwtClaimNames.AUD,
                            aud -> aud != null && aud.stream().anyMatch(audiences::contains)));
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(TRUSTED_KEY.toRSAPublicKey()).build();
            decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(validators));
            return decoder;
        }
    }
}
