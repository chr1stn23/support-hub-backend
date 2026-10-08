package com.chrisdev.supporthub;

import com.chrisdev.supporthub.asset.infrastructure.AssetTypeRepository;
import com.chrisdev.supporthub.auth.infrastructure.JwtService;
import com.chrisdev.supporthub.ticket.infrastructure.CategoryRepository;
import com.chrisdev.supporthub.user.domain.RoleName;
import com.chrisdev.supporthub.user.domain.User;
import com.chrisdev.supporthub.user.infrastructure.RoleRepository;
import com.chrisdev.supporthub.user.infrastructure.UserRepository;
import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class IntegrationTestSupport {

    public static final String PASSWORD = "Test1234pass";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private AssetTypeRepository assetTypeRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, TestUser user) {
        return user == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token());
    }

    /**
     * Extrae un valor del cuerpo JSON de la respuesta.
     */
    protected static String read(ResultActions result, String jsonPath) throws Exception {
        Object value = JsonPath.read(result.andReturn().getResponse().getContentAsString(), jsonPath);
        return String.valueOf(value);
    }

    protected static int statusOf(ResultActions result) {
        return result.andReturn().getResponse().getStatus();
    }

    protected TestUser createUser(RoleName... roles) {
        String email = "u-" + UUID.randomUUID() + "@test.local";
        User user = new User(email, passwordEncoder.encode(PASSWORD), "Test " + roles[0]);
        for (RoleName role : roles) {
            user.addRole(roleRepository.findByName(role).orElseThrow());
        }
        user = userRepository.saveAndFlush(user);
        return new TestUser(user.getId(), email, jwtService.issue(user).value());
    }

    protected UUID categoryId() {
        return categoryRepository.findByActiveTrueOrderByNameAsc().getFirst().getId();
    }

    protected UUID assetTypeId() {
        return assetTypeRepository.findByActiveTrueOrderByNameAsc().getFirst().getId();
    }

    protected String createTicket(TestUser requester, String title) throws Exception {
        String body = """
                {"title":"%s","description":"Descripción de prueba suficientemente larga",\
                "categoryId":"%s","priority":"MEDIUM"}""".formatted(title, categoryId());
        return read(post(requester, "/api/v1/tickets", body).andExpect(status().isCreated()), "$.id");
    }

    protected String createAsset(TestUser manager, String model) throws Exception {
        String body = """
                {"assetTypeId":"%s","brand":"Dell","model":"%s"}""".formatted(assetTypeId(), model);
        return read(post(manager, "/api/v1/assets", body).andExpect(status().isCreated()), "$.id");
    }

    protected ResultActions get(TestUser user, String url) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.get(url), user));
    }

    protected ResultActions post(TestUser user, String url, String json) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.post(url), user)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions postEmpty(TestUser user, String url) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.post(url), user));
    }

    protected ResultActions patch(TestUser user, String url, String json) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.patch(url), user)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions put(TestUser user, String url, String json) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.put(url), user)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions delete(TestUser user, String url) throws Exception {
        return mvc.perform(auth(MockMvcRequestBuilders.delete(url), user));
    }

    /**
     * Lanza todas las tareas a la vez (compuerta de salida común) y devuelve sus códigos HTTP.
     */
    protected List<Integer> runConcurrently(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            CountDownLatch ready = new CountDownLatch(tasks.size());
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (Callable<Integer> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return task.call();
                }));
            }
            ready.await();
            go.countDown();

            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    public record TestUser(UUID id, String email, String token) {
    }
}
