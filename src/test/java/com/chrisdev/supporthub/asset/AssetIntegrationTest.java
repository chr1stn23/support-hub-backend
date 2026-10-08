package com.chrisdev.supporthub.asset;

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
public class AssetIntegrationTest extends IntegrationTestSupport {

    private TestUser manager;

    private static String assignBody(TestUser target) {
        return "{\"userId\":\"%s\"}".formatted(target.id());
    }

    @BeforeEach
    void setUp() {
        manager = createUser(RoleName.ASSET_MANAGER);
    }

    @Test
    void onlyAssetManagersAndAdminsCanManageInventory() throws Exception {
        String body = "{\"assetTypeId\":\"%s\",\"brand\":\"HP\",\"model\":\"X\"}".formatted(assetTypeId());
        post(createUser(RoleName.USER), "/api/v1/assets", body).andExpect(status().isForbidden());
        post(createUser(RoleName.AGENT), "/api/v1/assets", body).andExpect(status().isForbidden());
        get(createUser(RoleName.USER), "/api/v1/assets").andExpect(status().isForbidden());
        get(createUser(RoleName.AGENT), "/api/v1/assets").andExpect(status().isOk());
    }

    @Test
    void assignTransferAndReturnKeepAFullHistory() throws Exception {
        TestUser first = createUser(RoleName.USER);
        TestUser second = createUser(RoleName.USER);
        String assetId = createAsset(manager, "Ciclo");

        post(manager, "/api/v1/assets/" + assetId + "/assign", assignBody(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedTo.id").value(first.id().toString()));
        post(manager, "/api/v1/assets/" + assetId + "/assign", assignBody(second))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSET_ALREADY_ASSIGNED"));

        post(manager, "/api/v1/assets/" + assetId + "/transfer", assignBody(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo.id").value(second.id().toString()));
        post(manager, "/api/v1/assets/" + assetId + "/return", "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AVAILABLE"));
        post(manager, "/api/v1/assets/" + assetId + "/return", "{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_ASSIGNED"));

        // Dos asignaciones en el historial; ninguna sigue vigente
        get(manager, "/api/v1/assets/" + assetId + "/assignments").andExpect(jsonPath("$.length()").value(2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asset_assignments WHERE asset_id = ? AND returned_at IS " +
                        "null",
                Long.class, UUID.fromString(assetId))).isZero();
    }

    @Test
    void maintenanceAndRetirementBlockAssignment() throws Exception {
        TestUser holder = createUser(RoleName.USER);
        String assetId = createAsset(manager, "Estados");

        post(manager, "/api/v1/assets/" + assetId + "/status", "{\"status\":\"MAINTENANCE\"}").andExpect(status().isOk());
        post(manager, "/api/v1/assets/" + assetId + "/assign", assignBody(holder))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSET_NOT_ASSIGNABLE"));

        post(manager, "/api/v1/assets/" + assetId + "/status", "{\"status\":\"RETIRED\"}").andExpect(status().isOk());
        post(manager, "/api/v1/assets/" + assetId + "/status", "{\"status\":\"AVAILABLE\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ASSET_STATUS_TRANSITION"));
    }

    @Test
    void serialNumbersAreUniqueRegardlessOfCase() throws Exception {
        String serial = "SN-" + UUID.randomUUID();
        String body = "{\"assetTypeId\":\"%s\",\"brand\":\"HP\",\"model\":\"M\",\"serialNumber\":\"%s\"}";

        post(manager, "/api/v1/assets", body.formatted(assetTypeId(), serial.toLowerCase())).andExpect(status().isCreated());
        post(manager, "/api/v1/assets", body.formatted(assetTypeId(), serial.toUpperCase()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SERIAL_NUMBER"));
    }

    /**
     * LA prueba de concurrencia: 8 peticiones simultáneas, de 2 roles, hacia 8 personas distintas,
     * sobre el MISMO activo. La garantía la da el índice único parcial de PostgreSQL.
     */
    @Test
    void ofManySimultaneousAssignmentsExactlyOneWins() throws Exception {
        TestUser admin = createUser(RoleName.ADMIN);
        String assetId = createAsset(manager, "Carrera");
        int attempts = 8;

        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            TestUser caller = (i % 2 == 0) ? manager : admin;
            TestUser target = createUser(RoleName.USER);
            tasks.add(() -> statusOf(post(caller, "/api/v1/assets/" + assetId + "/assign", assignBody(target))));
        }

        List<Integer> statuses = runConcurrently(tasks);

        assertThat(Collections.frequency(statuses, 200)).as("ganadores").isEqualTo(1);
        assertThat(Collections.frequency(statuses, 409)).as("perdedores").isEqualTo(attempts - 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM asset_assignments WHERE asset_id = ? AND returned_at IS " +
                        "null",
                Long.class, UUID.fromString(assetId))).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT status FROM assets WHERE id = ?", String.class,
                UUID.fromString(assetId)))
                .isEqualTo("ASSIGNED");
    }
}
