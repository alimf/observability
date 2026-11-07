package org.alim.user.api;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private void register(String email) throws Exception {

        mockMvc.perform(post("/api/v1/users")
                        .contentType(JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\", \"displayName\": \"Test User\"}"))
                .andExpect(status().isCreated());
    }

    private String login(String email, String password) throws Exception {

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("access_token").asText();
    }

    @Test
    void registerReturnsProfileWithoutPasswordAndNormalizesEmail() throws Exception {

        String email = uniqueEmail();

        mockMvc.perform(post("/api/v1/users")
                        .contentType(JSON)
                        .content("{\"email\": \"" + email.toUpperCase() + "\", \"password\": \"" + PASSWORD + "\", \"displayName\": \"Test User\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, org.hamcrest.Matchers.endsWith("/api/v1/users/me")))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {

        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\", \"displayName\": \"Again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void registerValidatesInput() throws Exception {

        mockMvc.perform(post("/api/v1/users")
                        .contentType(JSON)
                        .content("{\"email\": \"not-an-email\", \"password\": \"short\", \"displayName\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void loginIssuesBearerTokenThatAuthenticatesMe() throws Exception {

        String email = uniqueEmail();
        register(email);

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(3600))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(body).get("access_token").asText();

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void tokenCarriesUserIdRolesAndAKeyIdPublishedInJwks() throws Exception {

        String email = uniqueEmail();
        register(email);
        SignedJWT jwt = SignedJWT.parse(login(email, PASSWORD));

        assertThat(jwt.getJWTClaimsSet().getSubject()).matches("[0-9a-f-]{36}");
        assertThat(jwt.getJWTClaimsSet().getIssuer()).isEqualTo("http://localhost:8081");
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly("observability-api");
        assertThat(jwt.getJWTClaimsSet().getStringListClaim("roles")).containsExactly("USER");

        JsonNode keys = objectMapper.readTree(
                mockMvc.perform(get("/.well-known/jwks.json"))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString()).get("keys");

        assertThat(keys).hasSize(1);
        assertThat(keys.get(0).get("kid").asText()).isEqualTo(jwt.getHeader().getKeyID());
        assertThat(keys.get(0).has("d")).as("private exponent must never be published").isFalse();
    }

    @Test
    void loginFailuresAreIndistinguishable() throws Exception {

        String email = uniqueEmail();
        register(email);

        String wrongPassword = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(JSON)
                        .content("{\"email\": \"" + email + "\", \"password\": \"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        String unknownUser = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(JSON)
                        .content("{\"email\": \"" + uniqueEmail() + "\", \"password\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(unknownUser).get("detail"))
                .isEqualTo(objectMapper.readTree(wrongPassword).get("detail"));
    }

    @Test
    void meRequiresAValidToken() throws Exception {

        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registrationIsPublicButOtherUserEndpointsAreNot() throws Exception {

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }
}
