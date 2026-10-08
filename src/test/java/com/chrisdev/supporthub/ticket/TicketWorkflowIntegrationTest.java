package com.chrisdev.supporthub.ticket;

import com.chrisdev.supporthub.IntegrationTest;
import com.chrisdev.supporthub.IntegrationTestSupport;
import com.chrisdev.supporthub.user.domain.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class TicketWorkflowIntegrationTest extends IntegrationTestSupport {

    private TestUser user;
    private TestUser agent;
    private TestUser admin;

    @BeforeEach
    void setUp() {
        user = createUser(RoleName.USER);
        agent = createUser(RoleName.AGENT);
        admin = createUser(RoleName.ADMIN);
    }

    private ResultActions take(TestUser who, String ticketId) throws Exception {
        return postEmpty(who, "/api/v1/tickets/" + ticketId + "/assign");
    }

    private ResultActions move(TestUser who, String ticketId, String status) throws Exception {
        return post(who, "/api/v1/tickets/" + ticketId + "/status", "{\"status\":\"%s\"}".formatted(status));
    }

    @Test
    void aRequesterOnlySeesTheirOwnTickets() throws Exception {
        String ticketId = createTicket(user, "Solo mío");
        TestUser stranger = createUser(RoleName.USER);

        get(user, "/api/v1/tickets/" + ticketId).andExpect(status().isOk());
        get(agent, "/api/v1/tickets/" + ticketId).andExpect(status().isOk());
        // Un ticket ajeno responde 404, igual que si no existiera
        get(stranger, "/api/v1/tickets/" + ticketId).andExpect(status().isNotFound());
        get(stranger, "/api/v1/tickets").andExpect(jsonPath("$.totalElements").value(0));
        get(user, "/api/v1/tickets").andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void takingATicketStartsTheWorkAndLeavesAHistory() throws Exception {
        String ticketId = createTicket(user, "A tomar");

        take(agent, ticketId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.agent.id").value(agent.id().toString()));

        // CREATED + ASSIGNED + STATUS_CHANGED (OPEN → IN_PROGRESS)
        get(user, "/api/v1/tickets/" + ticketId + "/history")
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].eventType").value("CREATED"));
    }

    @Test
    void invalidTransitionsAndMissingPermissionsAreRejected() throws Exception {
        String ticketId = createTicket(user, "Reglas");

        move(user, ticketId, "CLOSED").andExpect(status().isForbidden());   // el solicitante no puede cerrar un OPEN
        move(agent, ticketId, "IN_PROGRESS").andExpect(status().isForbidden());    // agente no asignado
        move(admin, ticketId, "IN_PROGRESS")                            // sin agente asignado no hay trabajo en curso
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        take(agent, ticketId).andExpect(status().isOk());
        move(agent, ticketId, "CLOSED")                                            // no se puede saltar la resolución
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        take(createUser(RoleName.AGENT), ticketId)                                 // otro agente no puede quitárselo
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_ALREADY_ASSIGNED"));
    }

    @Test
    void fullLifecycleResolveReopenAndClose() throws Exception {
        String ticketId = createTicket(user, "Ciclo completo");
        take(agent, ticketId).andExpect(status().isOk());

        move(agent, ticketId, "RESOLVED").andExpect(status().isOk()).andExpect(jsonPath("$.resolvedAt").isNotEmpty());

        // Reabrir exige una nota
        move(user, ticketId, "IN_PROGRESS").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        post(user, "/api/v1/tickets/" + ticketId + "/status", "{\"status\":\"IN_PROGRESS\",\"note\":\"Sigue " +
                "fallando\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedAt").value(nullValue()));

        move(agent, ticketId, "RESOLVED").andExpect(status().isOk());
        move(user, ticketId, "CLOSED").andExpect(status().isOk()).andExpect(jsonPath("$.closedAt").isNotEmpty());

        // Un ticket cerrado queda bloqueado
        post(user, "/api/v1/tickets/" + ticketId + "/comments", "{\"body\":\"hola\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TICKET_LOCKED"));
    }

    @Test
    void anEditBasedOnAStaleVersionIsRejected() throws Exception {
        String ticketId = createTicket(user, "Edición");
        long version = Long.parseLong(read(get(user, "/api/v1/tickets/" + ticketId), "$.version"));

        patch(user, "/api/v1/tickets/" + ticketId, "{\"version\":%d,\"title\":\"Título nuevo\"}".formatted(version))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(greaterThanOrEqualTo((int) version + 1)));

        patch(user, "/api/v1/tickets/" + ticketId, "{\"version\":%d,\"title\":\"Otro título\"}".formatted(version))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void validationErrorsListEachInvalidField() throws Exception {
        post(user, "/api/v1/tickets", "{\"title\":\"\",\"description\":\"corta\",\"priority\":\"HIGH\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));   // title, description y categoryId
    }

    @Test
    void listsArePaginatedFilterableAndSortSafely() throws Exception {
        TestUser owner = createUser(RoleName.USER);
        for (int i = 1; i <= 3; i++) {
            createTicket(owner, "Paginado " + i);
        }

        get(owner, "/api/v1/tickets?size=2&sort=createdAt,asc")
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2));
        get(owner, "/api/v1/tickets?status=OPEN&unassigned=true").andExpect(jsonPath("$.totalElements").value(3));
        get(owner, "/api/v1/tickets?status=CLOSED").andExpect(jsonPath("$.totalElements").value(0));
        get(owner, "/api/v1/tickets?search=paginado 2").andExpect(jsonPath("$.totalElements").value(1));

        get(owner, "/api/v1/tickets?sort=passwordHash,asc").andExpect(status().isBadRequest());
        get(owner, "/api/v1/tickets?status=FOO").andExpect(status().isBadRequest());
        get(owner, "/api/v1/tickets?size=100000").andExpect(jsonPath("$.size").value(100));
    }
}
