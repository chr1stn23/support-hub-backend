package com.chrisdev.supporthub.ticket;

import com.chrisdev.supporthub.IntegrationTest;
import com.chrisdev.supporthub.IntegrationTestSupport;
import com.chrisdev.supporthub.ticket.domain.TicketStatus;
import com.chrisdev.supporthub.user.domain.RoleName;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class TicketActionsConsistencyIntegrationTest extends IntegrationTestSupport {

    private static final List<String> STATES =
            List.of("OPEN", "IN_PROGRESS", "WAITING", "RESOLVED", "CLOSED", "CANCELLED");

    private TestUser requester;
    private TestUser assignedAgent;
    private TestUser otherAgent;
    private TestUser thirdAgent;
    private TestUser admin;

    private static String url(String ticketId) {
        return "/api/v1/tickets/" + ticketId;
    }

    @BeforeEach
    void setUp() {
        requester = createUser(RoleName.USER);
        assignedAgent = createUser(RoleName.AGENT);
        otherAgent = createUser(RoleName.AGENT);
        thirdAgent = createUser(RoleName.AGENT);
        admin = createUser(RoleName.ADMIN);
    }

    private void move(TestUser who, String ticketId, String target) throws Exception {
        post(who, url(ticketId) + "/status", "{\"status\":\"%s\"}".formatted(target)).andExpect(status().isOk());
    }

    /**
     * Un ticket recién creado y llevado al estado pedido, con assignedAgent como agente (si procede).
     */
    private String ticketIn(String state) throws Exception {
        String id = createTicket(requester, "Acciones " + state);
        switch (state) {
            case "OPEN" -> {
            }
            case "CANCELLED" -> move(requester, id, "CANCELLED");
            default -> {
                postEmpty(assignedAgent, url(id) + "/assign").andExpect(status().isOk());
                if (state.equals("WAITING")) {
                    move(assignedAgent, id, "WAITING");
                }
                if (state.equals("RESOLVED") || state.equals("CLOSED")) {
                    move(assignedAgent, id, "RESOLVED");
                }
                if (state.equals("CLOSED")) {
                    move(requester, id, "CLOSED");
                }
            }
        }
        return id;
    }

    private long versionOf(String ticketId) throws Exception {
        return Long.parseLong(read(get(admin, url(ticketId)), "$.version"));
    }

    /**
     * Si `actions` ofrece algo, el servidor lo acepta. Si no lo ofrece, el servidor lo rechaza.
     * Es lo que impide que la interfaz y el backend se desincronicen sin que nadie lo note.
     */
    @Test
    void theAdvertisedActionsAreExactlyTheOnesTheServerAccepts() throws Exception {
        List<String> mismatches = new ArrayList<>();
        for (String state : STATES) {
            check(state, requester, "requester", mismatches);
            check(state, assignedAgent, "assignedAgent", mismatches);
            check(state, otherAgent, "otherAgent", mismatches);
            check(state, admin, "admin", mismatches);
        }
        assertThat(mismatches).isEmpty();
    }

    // Coherencia

    private void check(String state, TestUser actor, String actorName, List<String> mismatches) throws Exception {
        String[] current = {ticketIn(state)};
        String body = get(actor, url(current[0])).andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        List<String> offered = JsonPath.read(body, "$.actions.changeStatus[*].status");
        Boolean editContent = JsonPath.read(body, "$.actions.editContent");
        Boolean editTriage = JsonPath.read(body, "$.actions.editTriage");
        Boolean comment = JsonPath.read(body, "$.actions.comment");
        Boolean assignToSelf = JsonPath.read(body, "$.actions.assignToSelf");
        Boolean assignToOthers = JsonPath.read(body, "$.actions.assignToOthers");

        String who = state + " as " + actorName;

        for (TicketStatus target : TicketStatus.values()) {
            probe(who + " → status " + target, offered.contains(target.name()), state, current, mismatches,
                    id -> statusOf(post(actor, url(id) + "/status",
                            "{\"status\":\"%s\",\"note\":\"nota\"}".formatted(target))));
        }

        // Si ya es suyo, asignárselo de nuevo no hace nada y responde 200: no es un caso de "ofrecer o no"
        boolean alreadyHis = actor == assignedAgent && (state.equals("IN_PROGRESS") || state.equals("WAITING"));
        if (!alreadyHis) {
            probe(who + " → assign to self", assignToSelf, state, current, mismatches,
                    id -> statusOf(postEmpty(actor, url(id) + "/assign")));
        }
        probe(who + " → assign to others", assignToOthers, state, current, mismatches,
                id -> statusOf(post(actor, url(id) + "/assign", "{\"agentId\":\"%s\"}".formatted(thirdAgent.id()))));
        probe(who + " → edit content", editContent, state, current, mismatches,
                id -> statusOf(patch(actor, url(id),
                        "{\"version\":%d,\"title\":\"Título editado\"}".formatted(versionOf(id)))));
        probe(who + " → edit triage", editTriage, state, current, mismatches,
                id -> statusOf(patch(actor, url(id),
                        "{\"version\":%d,\"priority\":\"HIGH\"}".formatted(versionOf(id)))));
        probe(who + " → comment", comment, state, current, mismatches,
                id -> statusOf(post(actor, url(id) + "/comments", "{\"body\":\"hola\"}")));
    }

    private void probe(String label, boolean offered, String state, String[] current,
            List<String> mismatches, Probe probe) throws Exception {
        int code = probe.run(current[0]);
        boolean accepted = code / 100 == 2;
        if (accepted != offered) {
            mismatches.add("%s: offered=%s but the server answered %d".formatted(label, offered, code));
        }
        if (accepted) {
            current[0] = ticketIn(state);   // la prueba cambió el ticket: se sigue con uno limpio
        }
    }

    // Casos concretos, como documentación
    @Test
    void theOfferedStatusesFollowTheWorkflowForEachRole() throws Exception {
        String inProgress = ticketIn("IN_PROGRESS");
        get(assignedAgent, url(inProgress))
                .andExpect(jsonPath("$.actions.changeStatus[*].status").value(containsInAnyOrder("WAITING", "RESOLVED"
                        , "CANCELLED")))
                .andExpect(jsonPath("$.actions.assignToSelf").value(false))
                .andExpect(jsonPath("$.actions.assignToOthers").value(false));
        get(admin, url(inProgress))
                .andExpect(jsonPath("$.actions.assignToSelf").value(true))
                .andExpect(jsonPath("$.actions.assignToOthers").value(true));

        // Reabrir exige una nota, y la interfaz lo sabe de antemano
        String resolved = ticketIn("RESOLVED");
        get(requester, url(resolved))
                .andExpect(jsonPath("$.actions.changeStatus[*].status").value(containsInAnyOrder("CLOSED",
                        "IN_PROGRESS")))
                .andExpect(jsonPath("$.actions.changeStatus[?(@.status=='IN_PROGRESS')].noteRequired").value(contains(true)))
                .andExpect(jsonPath("$.actions.changeStatus[?(@.status=='CLOSED')].noteRequired").value(contains(false)));

        // Sin agente no hay trabajo en curso: ni siquiera el admin puede pasarlo a IN_PROGRESS
        String open = ticketIn("OPEN");
        get(admin, url(open))
                .andExpect(jsonPath("$.actions.changeStatus[*].status").value(contains("CANCELLED")));
        get(otherAgent, url(open))
                .andExpect(jsonPath("$.actions.assignToSelf").value(true))
                .andExpect(jsonPath("$.actions.assignToOthers").value(false))
                .andExpect(jsonPath("$.actions.changeStatus").isEmpty());
    }

    @Test
    void aClosedTicketOffersNothing() throws Exception {
        String closed = ticketIn("CLOSED");
        get(admin, url(closed))
                .andExpect(jsonPath("$.actions.changeStatus").isEmpty())
                .andExpect(jsonPath("$.actions.editContent").value(false))
                .andExpect(jsonPath("$.actions.editTriage").value(false))
                .andExpect(jsonPath("$.actions.comment").value(false))
                .andExpect(jsonPath("$.actions.attach").value(false))
                .andExpect(jsonPath("$.actions.assignToSelf").value(false))
                .andExpect(jsonPath("$.actions.assignToOthers").value(false));
    }

    @Test
    void listRowsAdvertiseWhetherTheViewerCanTakeTheTicket() throws Exception {
        TestUser owner = createUser(RoleName.USER);
        String id = createTicket(owner, "Para tomar");

        get(otherAgent, "/api/v1/tickets?search=Para tomar&size=5")
                .andExpect(jsonPath("$.content[0].canAssignToSelf").value(true));
        get(owner, "/api/v1/tickets?size=5")
                .andExpect(jsonPath("$.content[0].canAssignToSelf").value(false));

        postEmpty(otherAgent, url(id) + "/assign").andExpect(status().isOk());
        get(thirdAgent, "/api/v1/tickets?search=Para tomar&size=5")
                .andExpect(jsonPath("$.content[0].canAssignToSelf").value(false));
    }

    private interface Probe {
        int run(String ticketId) throws Exception;
    }

}
