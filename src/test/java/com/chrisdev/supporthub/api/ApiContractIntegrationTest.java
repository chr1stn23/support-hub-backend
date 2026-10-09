package com.chrisdev.supporthub.api;

import com.chrisdev.supporthub.IntegrationTest;
import com.chrisdev.supporthub.IntegrationTestSupport;
import com.chrisdev.supporthub.user.domain.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class ApiContractIntegrationTest extends IntegrationTestSupport {

    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    private TestUser manager;
    private TestUser agent;
    private TestUser admin;

    @BeforeEach
    void setUp() {
        manager = createUser(RoleName.ASSET_MANAGER);
        agent = createUser(RoleName.AGENT);
        admin = createUser(RoleName.ADMIN);
    }

    private void assign(String assetId, TestUser holder) throws Exception {
        post(manager, "/api/v1/assets/" + assetId + "/assign", "{\"userId\":\"%s\"}".formatted(holder.id()))
                .andExpect(status().isOk());
    }

    // Error de formulario
    @Test
    void validationErrorsCarryTheConstraintCodeOfEachField() throws Exception {
        TestUser user = createUser(RoleName.USER);

        post(user, "/api/v1/tickets", "{\"title\":\"\",\"description\":\"corta\",\"priority\":\"HIGH\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='title')].code").value(hasItem("NotBlank")))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='description')].code").value(hasItem("Size")))
                .andExpect(jsonPath("$.fieldErrors[?(@.field=='categoryId')].code").value(hasItem("NotNull")));
    }

    // Notifications

    @Test
    void assetNotificationsAreInSpanishAndLinkToTheAsset() throws Exception {
        TestUser holder = createUser(RoleName.USER);
        String assetId = createAsset(manager, "Con aviso");
        assign(assetId, holder);

        get(holder, "/api/v1/notifications")
                .andExpect(jsonPath("$.content[0].type").value("ASSET_ASSIGNED"))
                .andExpect(jsonPath("$.content[0].assetId").value(assetId))
                .andExpect(jsonPath("$.content[0].ticketId").doesNotExist())
                .andExpect(jsonPath("$.content[0].message").value(containsString("Te asignaron el equipo")));
    }

    @Test
    void ticketNotificationsAreInSpanishAndUseTheStatusLabels() throws Exception {
        TestUser requester = createUser(RoleName.USER);
        String ticketId = createTicket(requester, "Con aviso");
        postEmpty(agent, "/api/v1/tickets/" + ticketId + "/assign").andExpect(status().isOk());
        post(agent, "/api/v1/tickets/" + ticketId + "/status", "{\"status\":\"WAITING\"}").andExpect(status().isOk());

        get(requester, "/api/v1/notifications")
                .andExpect(jsonPath("$.content[0].type").value("TICKET_STATUS_CHANGED"))
                .andExpect(jsonPath("$.content[0].ticketId").value(ticketId))
                .andExpect(jsonPath("$.content[0].message").value(containsString("«Esperando»")));
    }

    @Test
    void theAutomaticStatusChangeOnAssignmentCarriesNoEnglishNote() throws Exception {
        TestUser requester = createUser(RoleName.USER);
        String ticketId = createTicket(requester, "Historial");
        postEmpty(agent, "/api/v1/tickets/" + ticketId + "/assign").andExpect(status().isOk());

        get(requester, "/api/v1/tickets/" + ticketId + "/history")
                .andExpect(jsonPath("$[?(@.eventType=='STATUS_CHANGED')].note").value(org.hamcrest.Matchers.contains((Object) null)));
    }

    // Equipos y tickets

    @Test
    void theRelatedTicketsOfAnAssetAreVisibleToThoseWhoManageInventory() throws Exception {
        TestUser holder = createUser(RoleName.USER);
        String assetId = createAsset(manager, "Con historia");
        assign(assetId, holder);

        post(holder, "/api/v1/tickets", """
                {"title":"Falla del equipo","description":"El equipo se reinicia solo cada hora",\
                "categoryId":"%s","priority":"HIGH","assetId":"%s"}""".formatted(categoryId(), assetId))
                .andExpect(status().isCreated());

        // El gestor de activos no ve el ticket completo (regla de visibilidad), pero sí este resumen
        get(manager, "/api/v1/assets/" + assetId + "/tickets")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Falla del equipo"));
        get(agent, "/api/v1/assets/" + assetId + "/tickets").andExpect(status().isOk());
        get(holder, "/api/v1/assets/" + assetId + "/tickets").andExpect(status().isForbidden());
        get(manager, "/api/v1/assets/" + UUID.randomUUID() + "/tickets").andExpect(status().isNotFound());
    }

    @Test
    void assetsCanBeFilteredByTheirCurrentHolder() throws Exception {
        TestUser holder = createUser(RoleName.USER);
        TestUser nobody = createUser(RoleName.USER);
        String assetId = createAsset(manager, "De alguien");
        assign(assetId, holder);

        get(manager, "/api/v1/assets?assignedTo=" + holder.id())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(assetId));
        get(manager, "/api/v1/assets?assignedTo=" + nobody.id()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void ordinaryUsersCanOnlyLinkAssetsAssignedToThem() throws Exception {
        TestUser holder = createUser(RoleName.USER);
        String mine = createAsset(manager, "Mío");
        String notMine = createAsset(manager, "Ajeno");
        assign(mine, holder);

        String body = """
                {"title":"Con equipo","description":"Descripción suficientemente larga",\
                "categoryId":"%s","priority":"LOW","assetId":"%s"}""";

        post(holder, "/api/v1/tickets", body.formatted(categoryId(), mine)).andExpect(status().isCreated());
        post(holder, "/api/v1/tickets", body.formatted(categoryId(), notMine))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        // Un equipo inexistente da la misma respuesta: no revela qué hay en el inventario
        post(holder, "/api/v1/tickets", body.formatted(categoryId(), UUID.randomUUID()))
                .andExpect(status().isBadRequest());
        // El personal de TI puede vincular cualquiera
        post(agent, "/api/v1/tickets", body.formatted(categoryId(), notMine)).andExpect(status().isCreated());
    }

    @Test
    void assetActionsFollowTheStatusAndTheRole() throws Exception {
        TestUser holder = createUser(RoleName.USER);
        String assetId = createAsset(manager, "Con acciones");
        String url = "/api/v1/assets/" + assetId;

        get(manager, url)
                .andExpect(jsonPath("$.actions.assign").value(true))
                .andExpect(jsonPath("$.actions.transfer").value(false))
                .andExpect(jsonPath("$.actions.changeStatus").value(containsInAnyOrder("MAINTENANCE", "RETIRED")));
        get(agent, url)
                .andExpect(jsonPath("$.actions.edit").value(false))
                .andExpect(jsonPath("$.actions.assign").value(false))
                .andExpect(jsonPath("$.actions.changeStatus").isEmpty());

        assign(assetId, holder);
        get(admin, url)
                .andExpect(jsonPath("$.actions.assign").value(false))
                .andExpect(jsonPath("$.actions.transfer").value(true))
                .andExpect(jsonPath("$.actions.returnAsset").value(true))
                .andExpect(jsonPath("$.actions.changeStatus").isEmpty());
    }

    // Adjuntos
    @Test
    void attachmentDeletionIsAdvertisedPerAttachmentAndPerViewer() throws Exception {
        TestUser owner = createUser(RoleName.USER);
        String ticketId = createTicket(owner, "Con adjunto");
        mvc.perform(multipart("/api/v1/tickets/{id}/attachments", ticketId)
                        .file(new MockMultipartFile("file", "pixel.png", "image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + owner.token()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.canDelete").value(true));

        String url = "/api/v1/tickets/" + ticketId + "/attachments";
        get(owner, url).andExpect(jsonPath("$[0].canDelete").value(true));
        get(agent, url).andExpect(jsonPath("$[0].canDelete").value(false));   // no lo subió ni está asignado
        get(admin, url).andExpect(jsonPath("$[0].canDelete").value(true));
    }

    // Dashboard
    @Test
    void theDashboardTrendHasOnePointPerDayIncludingEmptyOnes() throws Exception {
        TestUser user = createUser(RoleName.USER);
        createTicket(user, "Tendencia 1");
        createTicket(user, "Tendencia 2");

        get(user, "/api/v1/dashboard/summary?days=7")
                .andExpect(jsonPath("$.tickets.trend.length()").value(8))   // 7 días atrás + hoy
                .andExpect(jsonPath("$.tickets.trend[0].created").value(0))
                .andExpect(jsonPath("$.tickets.trend[7].day").value(LocalDate.now(ZoneOffset.UTC).toString()))
                .andExpect(jsonPath("$.tickets.trend[7].created").value(2))
                .andExpect(jsonPath("$.tickets.trend[7].resolved").value(0));
    }
}
