package com.chrisdev.supporthub.auth;

import com.chrisdev.supporthub.IntegrationTest;
import com.chrisdev.supporthub.IntegrationTestSupport;
import com.chrisdev.supporthub.user.domain.RoleName;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class SessionCookiesIntegrationTest extends IntegrationTestSupport {

    private static final String XSRF = "xsrf-test-value";

    private static String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}""".formatted(email, password);
    }

    private static Cookie cookie(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertThat(cookie).as("cookie %s", name).isNotNull();
        return cookie;
    }

    private static String setCookieHeader(MvcResult result, String name) {
        return result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(name + "="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Set-Cookie for " + name));
    }

    private static Cookie xsrfCookie() {
        return new Cookie("XSRF-TOKEN", XSRF);
    }

    // Helpers
    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    /**
     * Lo que hace Angular: la cookie XSRF-TOKEN y su valor repetido en la cabecera X-XSRF-TOKEN.
     */
    private ResultActions refresh(Cookie refreshCookie) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/refresh").cookie(refreshCookie, xsrfCookie())
                .header("X-XSRF-TOKEN", XSRF));
    }

    private ResultActions me(Cookie accessCookie) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/me").cookie(accessCookie));
    }

    // Tests

    @Test
    void loginSetsHardenedCookies() throws Exception {
        TestUser user = createUser(RoleName.USER);
        MvcResult result = login(user.email(), PASSWORD).andExpect(status().isOk()).andReturn();

        assertThat(setCookieHeader(result, "SH_ACCESS"))
                .contains("HttpOnly", "SameSite=Strict", "Path=/api").doesNotContain("Path=/api/v1");
        assertThat(setCookieHeader(result, "SH_REFRESH"))
                .contains("HttpOnly", "SameSite=Strict", "Path=/api/v1/auth");

        Cookie xsrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(xsrfCookie).as("XSRF-TOKEN cookie").isNotNull();
        assertThat(xsrfCookie.isHttpOnly()).isFalse();
        assertThat(xsrfCookie.getAttribute("SameSite")).isEqualTo("Strict");
    }

    @Test
    void theCookieAloneAuthenticatesReads() throws Exception {
        TestUser user = createUser(RoleName.USER);
        MvcResult result = login(user.email(), PASSWORD).andReturn();

        me(cookie(result, "SH_ACCESS")).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(user.email()));
    }

    @Test
    void writesAuthenticatedByCookieNeedTheCsrfHeader() throws Exception {
        TestUser user = createUser(RoleName.USER);
        Cookie access = cookie(login(user.email(), PASSWORD).andReturn(), "SH_ACCESS");
        String body = """
                {"title":"Con cookie","description":"Ticket creado autenticado por cookie",\
                "categoryId":"%s","priority":"LOW"}""".formatted(categoryId());

        // Sin cabecera
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/tickets").cookie(access, xsrfCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_TOKEN_INVALID"));
        // Cabecera distinta de la cookie
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/tickets")
                        .cookie(access, xsrfCookie())
                        .header("X-XSRF-TOKEN", "otro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());
        // Coinciden: pasa
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/tickets")
                        .cookie(access, xsrfCookie())
                        .header("X-XSRF-TOKEN", XSRF)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
        // Un cliente con Authorization (sin cookies) no necesita CSRF
        post(user, "/api/v1/tickets", body).andExpect(status().isCreated());
    }

    @Test
    void anyResponseHandsOutTheCsrfCookie() throws Exception {
        MvcResult result = mvc.perform(MockMvcRequestBuilders.get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andReturn();
        assertThat(setCookieHeader(result, "XSRF-TOKEN")).contains("Path=/");
    }

    @Test
    void refreshRotatesTheTokenAndToleratesAConcurrentUse() throws Exception {
        TestUser user = createUser(RoleName.USER);
        Cookie original = cookie(login(user.email(), PASSWORD).andReturn(), "SH_REFRESH");

        MvcResult rotated = refresh(original).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(user.email())).andReturn();
        Cookie next = cookie(rotated, "SH_REFRESH");
        assertThat(next.getValue()).isNotEqualTo(original.getValue());
        cookie(rotated, "SH_ACCESS");

        // Otra pestaña usó el viejo a la vez: es una carrera, no un robo
        refresh(original).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_REFRESH"));
        refresh(next).andExpect(status().isOk());   // la familia sigue viva
    }

    @Test
    void reusingAConsumedRefreshTokenRevokesTheWholeFamily() throws Exception {
        TestUser user = createUser(RoleName.USER);
        Cookie stolen = cookie(login(user.email(), PASSWORD).andReturn(), "SH_REFRESH");
        Cookie legitimate = cookie(refresh(stolen).andReturn(), "SH_REFRESH");

        // Pasada la ventana de carrera, volver a presentar el token consumido es sospechoso
        jdbc.update("UPDATE refresh_tokens SET used_at = used_at - INTERVAL '1 minute' WHERE user_id = ? AND used_at " +
                        "IS NOT null",
                user.id());
        refresh(stolen).andExpect(status().isUnauthorized());

        // La sesión legítima también cae: no se sabe cuál de los dos es el atacante
        refresh(legitimate).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'REFRESH_REUSE_DETECTED' AND " +
                        "entity_id = ?",
                Long.class, user.id())).isEqualTo(1L);
    }

    @Test
    void anInvalidRefreshTokenIsRejectedAndTheCookiesAreCleared() throws Exception {
        MvcResult result = refresh(new Cookie("SH_REFRESH", "token-inventado"))
                .andExpect(status().isUnauthorized()).andReturn();

        assertThat(setCookieHeader(result, "SH_REFRESH")).contains("Max-Age=0");
        assertThat(setCookieHeader(result, "SH_ACCESS")).contains("Max-Age=0");
    }

    @Test
    void logoutRevokesTheSessionAndClearsTheCookies() throws Exception {
        TestUser user = createUser(RoleName.USER);
        Cookie refreshCookie = cookie(login(user.email(), PASSWORD).andReturn(), "SH_REFRESH");

        MvcResult result = mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/logout")
                        .cookie(refreshCookie, xsrfCookie())
                        .header("X-XSRF-TOKEN", XSRF))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(setCookieHeader(result, "SH_REFRESH")).contains("Max-Age=0");

        refresh(refreshCookie).andExpect(status().isUnauthorized());
        // Cerrar sesión dos veces no es un error
        mvc.perform(MockMvcRequestBuilders.post("/api/v1/auth/logout")
                        .cookie(refreshCookie, xsrfCookie())
                        .header("X-XSRF-TOKEN", XSRF))
                .andExpect(status().isNoContent());
    }

    @Test
    void changingRolesInvalidatesTheOldTokenButARefreshRecoversTheSession() throws Exception {
        TestUser admin = createUser(RoleName.ADMIN);
        TestUser user = createUser(RoleName.USER);
        MvcResult session = login(user.email(), PASSWORD).andReturn();
        Cookie oldAccess = cookie(session, "SH_ACCESS");
        me(oldAccess).andExpect(status().isOk());

        put(admin, "/api/v1/users/" + user.id() + "/roles", "{\"roles\":[\"USER\",\"AGENT\"]}").andExpect(status().isOk());

        // El token viejo (con los roles de antes) ya no vale...
        me(oldAccess).andExpect(status().isUnauthorized());
        // ...pero el refresh entrega uno nuevo con los roles actualizados, sin volver a pedir la contraseña
        MvcResult renewed = refresh(cookie(session, "SH_REFRESH")).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.roles", hasItem("AGENT"))).andReturn();
        me(cookie(renewed, "SH_ACCESS")).andExpect(status().isOk()).andExpect(jsonPath("$.roles", hasItem("AGENT")));
    }

    @Test
    void deactivatingAUserKillsBothTokensAtOnce() throws Exception {
        TestUser admin = createUser(RoleName.ADMIN);
        TestUser user = createUser(RoleName.USER);
        MvcResult session = login(user.email(), PASSWORD).andReturn();

        post(admin, "/api/v1/users/" + user.id() + "/deactivate", "{}").andExpect(status().isOk());

        me(cookie(session, "SH_ACCESS")).andExpect(status().isUnauthorized());
        refresh(cookie(session, "SH_REFRESH")).andExpect(status().isUnauthorized());
    }

    @Test
    void changingYourPasswordSignsOutOtherDevicesButKeepsThisOne() throws Exception {
        TestUser user = createUser(RoleName.USER);
        MvcResult thisDevice = login(user.email(), PASSWORD).andReturn();
        MvcResult otherDevice = login(user.email(), PASSWORD).andReturn();

        MvcResult changed = mvc.perform(MockMvcRequestBuilders.post("/api/v1/users/me/password")
                        .cookie(cookie(thisDevice, "SH_ACCESS"), xsrfCookie()).header("X-XSRF-TOKEN", XSRF)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"Otra1234clave\"}".formatted(PASSWORD)))
                .andExpect(status().isNoContent()).andReturn();

        // Este dispositivo recibe cookies nuevas y sigue dentro
        me(cookie(changed, "SH_ACCESS")).andExpect(status().isOk());
        me(cookie(thisDevice, "SH_ACCESS")).andExpect(status().isUnauthorized());   // el viejo ya no vale
        // El otro dispositivo se queda sin sesión
        me(cookie(otherDevice, "SH_ACCESS")).andExpect(status().isUnauthorized());
        refresh(cookie(otherDevice, "SH_REFRESH")).andExpect(status().isUnauthorized());
        // La contraseña nueva funciona y la vieja no
        login(user.email(), "Otra1234clave").andExpect(status().isOk());
        login(user.email(), PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void tooManyFailedLoginsLockTheEmailOut() throws Exception {
        TestUser user = createUser(RoleName.USER);
        TestUser innocent = createUser(RoleName.USER);

        for (int i = 0; i < 5; i++) {
            login(user.email(), "mala-" + i).andExpect(status().isUnauthorized());
        }

        // Ya ni la contraseña correcta sirve, y el cliente sabe cuándo reintentar
        MvcResult blocked = login(user.email(), PASSWORD).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS")).andReturn();
        assertThat(blocked.getResponse().getHeader("Retry-After")).isNotNull();

        login(innocent.email(), PASSWORD).andExpect(status().isOk());   // otros correos no se ven afectados
    }
}
