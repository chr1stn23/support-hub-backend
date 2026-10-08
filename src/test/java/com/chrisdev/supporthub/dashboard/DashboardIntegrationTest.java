package com.chrisdev.supporthub.dashboard;

import com.chrisdev.supporthub.IntegrationTest;
import com.chrisdev.supporthub.IntegrationTestSupport;
import com.chrisdev.supporthub.user.domain.RoleName;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class DashboardIntegrationTest extends IntegrationTestSupport {

    private static final String URL = "/api/v1/dashboard/summary";

    @Test
    void aRequesterSeesExactlyTheirOwnNumbers() throws Exception {
        TestUser user = createUser(RoleName.USER);
        TestUser agent = createUser(RoleName.AGENT);

        String resolved = createTicket(user, "Resuelto");
        String inProgress = createTicket(user, "En curso");
        String untouched = createTicket(user, "Sin tocar");
        postEmpty(agent, "/api/v1/tickets/" + resolved + "/assign").andExpect(status().isOk());
        post(agent, "/api/v1/tickets/" + resolved + "/status", "{\"status\":\"RESOLVED\"}").andExpect(status().isOk());
        postEmpty(agent, "/api/v1/tickets/" + inProgress + "/assign").andExpect(status().isOk());

        String body = get(user, URL)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("OWN"))
                .andExpect(jsonPath("$.mine").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.assets").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.tickets.open").value(2))              // en curso + sin tocar
                .andExpect(jsonPath("$.tickets.unassigned").value(1))        // el que nadie tomó
                .andExpect(jsonPath("$.tickets.createdInPeriod").value(3))
                .andExpect(jsonPath("$.tickets.resolvedInPeriod").value(1))
                .andExpect(jsonPath("$.tickets.avgResolutionHours").value(notNullValue()))
                .andExpect(jsonPath("$.tickets.byStatus.OPEN").value(1))
                .andExpect(jsonPath("$.tickets.byStatus.IN_PROGRESS").value(1))
                .andExpect(jsonPath("$.tickets.byStatus.RESOLVED").value(1))
                .andExpect(jsonPath("$.tickets.byStatus.CLOSED").value(0))   // los ceros se incluyen
                .andExpect(jsonPath("$.recentActivity.length()").value(lessThanOrEqualTo(10)))
                .andReturn().getResponse().getContentAsString();

        // La actividad reciente solo menciona sus propios tickets
        List<String> mentioned = JsonPath.read(body, "$.recentActivity[*].ticketId");
        assertThat(mentioned).isNotEmpty().isSubsetOf(resolved, inProgress, untouched);
    }

    @Test
    void anAgentSeesTheWholeOperationPlusTheirOwnWorkload() throws Exception {
        TestUser user = createUser(RoleName.USER);
        TestUser agent = createUser(RoleName.AGENT);
        String ticketId = createTicket(user, "Para el agente");
        postEmpty(agent, "/api/v1/tickets/" + ticketId + "/assign").andExpect(status().isOk());

        get(agent, URL)
                .andExpect(jsonPath("$.scope").value("ALL"))
                .andExpect(jsonPath("$.mine.assignedOpen").value(1))
                .andExpect(jsonPath("$.mine.resolvedInPeriod").value(0))
                .andExpect(jsonPath("$.assets").value(notNullValue()));
    }

    @Test
    void anAssetManagerSeesAssetsButOnlyTheirOwnTickets() throws Exception {
        TestUser manager = createUser(RoleName.ASSET_MANAGER);
        createAsset(manager, "Para el dashboard");

        get(manager, URL)
                .andExpect(jsonPath("$.scope").value("OWN"))
                .andExpect(jsonPath("$.mine").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.assets.total").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.assets.byStatus.AVAILABLE").value(greaterThanOrEqualTo(1)));
    }

    @Test
    void theNumbersOfOneResponseAreConsistentWithEachOther() throws Exception {
        TestUser admin = createUser(RoleName.ADMIN);
        createTicket(createUser(RoleName.USER), "Para sumar");

        String body = get(admin, URL).andReturn().getResponse().getContentAsString();

        int open = JsonPath.read(body, "$.tickets.open");
        int openByStatus = (int) JsonPath.read(body, "$.tickets.byStatus.OPEN")
                + (int) JsonPath.read(body, "$.tickets.byStatus.IN_PROGRESS")
                + (int) JsonPath.read(body, "$.tickets.byStatus.WAITING");
        assertThat(open).isEqualTo(openByStatus);

        List<Integer> perCategory = JsonPath.read(body, "$.tickets.byCategory[*].count");
        int created = JsonPath.read(body, "$.tickets.createdInPeriod");
        assertThat(perCategory.stream().mapToInt(Integer::intValue).sum()).isEqualTo(created);
    }

    @Test
    void theDashboardAgreesWithTheTicketList() throws Exception {
        TestUser user = createUser(RoleName.USER);
        TestUser agent = createUser(RoleName.AGENT);
        createTicket(user, "Uno");
        String two = createTicket(user, "Dos");
        postEmpty(agent, "/api/v1/tickets/" + two + "/assign").andExpect(status().isOk());

        // Dos caminos de código distintos (SQL agregado y Specifications de JPA) deben dar lo mismo
        String dashboard = get(user, URL).andReturn().getResponse().getContentAsString();
        for (String status : List.of("OPEN", "IN_PROGRESS", "WAITING", "RESOLVED", "CLOSED", "CANCELLED")) {
            int fromDashboard = JsonPath.read(dashboard, "$.tickets.byStatus." + status);
            int fromList = JsonPath.read(get(user, "/api/v1/tickets?size=1&status=" + status)
                    .andReturn().getResponse().getContentAsString(), "$.totalElements");
            assertThat(fromDashboard).as("estado %s", status).isEqualTo(fromList);
        }
    }

    @Test
    void theDaysParameterIsValidated() throws Exception {
        TestUser user = createUser(RoleName.USER);
        get(user, URL + "?days=0").andExpect(status().isBadRequest());
        get(user, URL + "?days=366").andExpect(status().isBadRequest());
        get(user, URL + "?days=abc").andExpect(status().isBadRequest());
        get(null, URL).andExpect(status().isUnauthorized());
    }
}
