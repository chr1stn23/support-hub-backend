package com.chrisdev.supporthub.auth;

import com.chrisdev.supporthub.IntegrationTest;
import com.chrisdev.supporthub.IntegrationTestSupport;
import com.chrisdev.supporthub.user.domain.RoleName;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class AuthIntegrationTest extends IntegrationTestSupport {

    private static String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}""".formatted(email, password);
    }

    @Test
    void validCredentialsReturnATokenAndTheUser() throws Exception {
        TestUser user = createUser(RoleName.USER);

        post(null, "/api/v1/auth/login", loginBody(user.email(), PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value(user.email()))
                .andExpect(jsonPath("$.user.roles[0]").value("USER"));
    }

    @Test
    void wrongPasswordAndUnknownEmailAreIndistinguishable() throws Exception {
        TestUser user = createUser(RoleName.USER);

        String wrongPassword = post(null, "/api/v1/auth/login", loginBody(user.email(), "wrong-password1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();
        String unknownEmail = post(null, "/api/v1/auth/login", loginBody("nobody@test.local", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn().getResponse().getContentAsString();

        // El cliente no puede deducir qué emails existen
        assertThat(JsonPath.<String>read(wrongPassword, "$.message"))
                .isEqualTo(JsonPath.read(unknownEmail, "$.message"));
    }

    @Test
    void aFailedLoginIsAuditedWithoutStoringThePassword() throws Exception {
        TestUser user = createUser(RoleName.USER);
        post(null, "/api/v1/auth/login", loginBody(user.email(), "wrong-password1")).andExpect(status().isUnauthorized());

        String metadata = jdbc.queryForObject("""
                SELECT CAST(metadata AS TEXT) FROM audit_logs
                WHERE action = 'LOGIN_FAILED' AND metadata ->> 'email' = ?""", String.class, user.email());

        assertThat(metadata).contains("BAD_PASSWORD").doesNotContain("wrong-password1");
    }

    @Test
    void inactiveUsersCannotLogIn() throws Exception {
        TestUser user = createUser(RoleName.USER);
        jdbc.update("UPDATE users SET active = false WHERE id = ?", user.id());

        post(null, "/api/v1/auth/login", loginBody(user.email(), PASSWORD)).andExpect(status().isUnauthorized());
    }

    @Test
    void missingOrInvalidTokensGetTheStandardErrorFormat() throws Exception {
        get(null, "/api/v1/auth/me")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/me"));

        get(new TestUser(null, "x", "abc.def.ghi"), "/api/v1/auth/me")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void aTokenOfAnotherRoleCannotReachAdministrationEndpoints() throws Exception {
        TestUser user = createUser(RoleName.USER);
        TestUser agent = createUser(RoleName.AGENT);

        for (TestUser caller : new TestUser[]{user, agent}) {
            get(caller, "/api/v1/users").andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
            get(caller, "/api/v1/audit-logs").andExpect(status().isForbidden());
        }
    }
}
