package com.chrisdev.supporthub.user;

import com.chrisdev.supporthub.IntegrationTest;
import com.chrisdev.supporthub.IntegrationTestSupport;
import com.chrisdev.supporthub.user.domain.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class UserAdministrationIntegrationTest extends IntegrationTestSupport {

    private TestUser admin;

    private static String newUserBody(String email, String password, String role) {
        return """
                {"email":"%s","fullName":"Persona Nueva","password":"%s","roles":["%s"]}""".formatted(email, password
                , role);
    }

    @BeforeEach
    void setUp() {
        admin = createUser(RoleName.ADMIN);
    }

    @Test
    void anAdminCreatesAUserWhoCanThenSignIn() throws Exception {
        String email = "nuevo-" + UUID.randomUUID() + "@test.local";

        post(admin, "/api/v1/users", newUserBody(email, "Clave12345", "USER"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roles[0]").value("USER"));
        post(null, "/api/v1/auth/login", "{\"email\":\"%s\",\"password\":\"Clave12345\"}".formatted(email))
                .andExpect(status().isOk());

        // El mismo email con otras mayúsculas ya existe
        post(admin, "/api/v1/users", newUserBody(email.toUpperCase(), "Clave12345", "USER"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    void weakPasswordsAreRejected() throws Exception {
        post(admin, "/api/v1/users", newUserBody("x-" + UUID.randomUUID() + "@test.local", "sololetras", "USER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
    }

    @Test
    void anAdminCannotDemoteOrDeactivateThemselves() throws Exception {
        post(admin, "/api/v1/users/" + admin.id() + "/deactivate", "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));
        put(admin, "/api/v1/users/" + admin.id() + "/roles", "{\"roles\":[\"AGENT\"]}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANNOT_MODIFY_SELF"));
    }

    @Test
    void aUserHoldingAssetsCannotBeDeactivated() throws Exception {
        TestUser holder = createUser(RoleName.USER);
        TestUser manager = createUser(RoleName.ASSET_MANAGER);
        String assetId = createAsset(manager, "En uso");
        post(manager, "/api/v1/assets/" + assetId + "/assign", "{\"userId\":\"%s\"}".formatted(holder.id()))
                .andExpect(status().isOk());

        post(admin, "/api/v1/users/" + holder.id() + "/deactivate", "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_HAS_ACTIVE_ASSIGNMENTS"));

        post(manager, "/api/v1/assets/" + assetId + "/return", "{}").andExpect(status().isOk());
        post(admin, "/api/v1/users/" + holder.id() + "/deactivate", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void roleChangesAreAudited() throws Exception {
        TestUser target = createUser(RoleName.USER);

        put(admin, "/api/v1/users/" + target.id() + "/roles", "{\"roles\":[\"USER\",\"AGENT\"]}")
                .andExpect(status().isOk());

        String metadata = jdbc.queryForObject("""
                SELECT CAST(metadata AS TEXT) FROM audit_logs
                WHERE action = 'USER_ROLES_CHANGED' AND entity_id = ?""", String.class, target.id());
        assertThat(metadata).contains("AGENT");
    }

    @Test
    void theLookupOnlyOffersActiveUsersWithMinimalData() throws Exception {
        TestUser active = createUser(RoleName.USER);
        TestUser inactive = createUser(RoleName.USER);
        jdbc.update("UPDATE users SET active = false WHERE id = ?", inactive.id());
        TestUser agent = createUser(RoleName.AGENT);

        get(agent, "/api/v1/users/lookup?search=" + active.email())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].roles").doesNotExist());
        get(agent, "/api/v1/users/lookup?search=" + inactive.email()).andExpect(jsonPath("$.totalElements").value(0));
        get(createUser(RoleName.USER), "/api/v1/users/lookup").andExpect(status().isForbidden());
    }

    /**
     * Dos admins se desactivan entre sí a la vez. Sin el bloqueo pesimista del rol ADMIN
     * ambos tendrían éxito y el sistema quedaría sin administradores.
     */
    @Test
    void twoAdminsDeactivatingEachOtherLeaveExactlyOneActive() throws Exception {
        TestUser a = createUser(RoleName.ADMIN);
        TestUser b = createUser(RoleName.ADMIN);

        List<Callable<Integer>> tasks = new ArrayList<>();
        tasks.add(() -> statusOf(post(a, "/api/v1/users/" + b.id() + "/deactivate", "{}")));
        tasks.add(() -> statusOf(post(b, "/api/v1/users/" + a.id() + "/deactivate", "{}")));

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(Collections.frequency(statuses, 200)).isEqualTo(1);
        // El perdedor ya no es un admin activo cuando obtiene el bloqueo
        assertThat(statuses.stream().filter(s -> s != 200)).allMatch(s -> s == 401 || s == 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE id IN (?, ?) AND active",
                Long.class, a.id(), b.id())).isEqualTo(1L);
    }
}
