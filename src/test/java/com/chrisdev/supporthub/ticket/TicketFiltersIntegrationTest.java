package com.chrisdev.supporthub.ticket;

import com.chrisdev.supporthub.IntegrationTest;
import com.chrisdev.supporthub.IntegrationTestSupport;
import com.chrisdev.supporthub.user.domain.RoleName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class TicketFiltersIntegrationTest extends IntegrationTestSupport {

    private TestUser owner;
    private TestUser agent;

    @BeforeEach
    void setUp() {
        owner = createUser(RoleName.USER);
        agent = createUser(RoleName.AGENT);
    }

    @Test
    void severalStatusesCanBeRequestedAtOnce() throws Exception {
        createTicket(owner, "Abierto");
        String inProgress = createTicket(owner, "En curso");
        String resolved = createTicket(owner, "Resuelto");
        postEmpty(agent, "/api/v1/tickets/" + inProgress + "/assign").andExpect(status().isOk());
        postEmpty(agent, "/api/v1/tickets/" + resolved + "/assign").andExpect(status().isOk());
        post(agent, "/api/v1/tickets/" + resolved + "/status", "{\"status\":\"RESOLVED\"}").andExpect(status().isOk());

        get(owner, "/api/v1/tickets?status=OPEN&status=IN_PROGRESS").andExpect(jsonPath("$.totalElements").value(2));
        get(owner, "/api/v1/tickets?status=OPEN,IN_PROGRESS").andExpect(jsonPath("$.totalElements").value(2));
        get(owner, "/api/v1/tickets?status=RESOLVED").andExpect(jsonPath("$.totalElements").value(1));
        get(owner, "/api/v1/tickets?status=OPEN,IN_PROGRESS,RESOLVED").andExpect(jsonPath("$.totalElements").value(3));
        get(owner, "/api/v1/tickets?status=FOO").andExpect(status().isBadRequest());
    }

    @Test
    void ticketsCanBeSortedByRealPriority() throws Exception {
        createTicket(owner, "P-low", "LOW");
        createTicket(owner, "P-critical", "CRITICAL");
        createTicket(owner, "P-medium", "MEDIUM");
        createTicket(owner, "P-high", "HIGH");

        get(owner, "/api/v1/tickets?sort=priority,desc")
                .andExpect(jsonPath("$.content[*].title").value(contains("P-critical", "P-high", "P-medium", "P-low")));
        get(owner, "/api/v1/tickets?sort=priority,asc")
                .andExpect(jsonPath("$.content[*].title").value(contains("P-low", "P-medium", "P-high", "P-critical")));
        get(owner, "/api/v1/tickets?priority=HIGH,CRITICAL")
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void paginationIsStableWhenManyRowsShareTheSortValue() throws Exception {
        for (int i = 0; i < 5; i++) {
            createTicket(owner, "Igual " + i, "HIGH");
        }

        String first = read(get(owner, "/api/v1/tickets?sort=priority,desc&size=2&page=0"), "$.content[*].id");
        String second = read(get(owner, "/api/v1/tickets?sort=priority,desc&size=2&page=1"), "$.content[*].id");
        String third = read(get(owner, "/api/v1/tickets?sort=priority,desc&size=2&page=2"), "$.content[*].id");

        // Ningún ticket aparece en dos páginas ni se omite.
        List<String> ids = new ArrayList<>();
        for (String page : List.of(first, second, third)) {
            String[] pageIds = page
                    .replaceAll("[\\[\\]\"]", "")
                    .split(",");

            ids.addAll(Arrays.asList(pageIds));
        }

        assertThat(ids).hasSize(5);
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void ticketsCanBeFilteredByCreationDate() throws Exception {
        createTicket(owner, "Con fecha");
        Instant now = Instant.now();

        get(owner, "/api/v1/tickets?createdFrom=" + now.minus(Duration.ofHours(1))).andExpect(jsonPath("$" +
                ".totalElements").value(1));
        get(owner, "/api/v1/tickets?createdFrom=" + now.plus(Duration.ofHours(1))).andExpect(jsonPath("$" +
                ".totalElements").value(0));
        get(owner, "/api/v1/tickets?createdTo=" + now.minus(Duration.ofHours(1))).andExpect(jsonPath("$.totalElements"
        ).value(0));
        get(owner,
                "/api/v1/tickets?createdFrom=" + now.plus(Duration.ofHours(2)) + "&createdTo=" + now.minus(Duration.ofHours(2)))
                .andExpect(status().isBadRequest());
    }
}
