package com.chrisdev.supporthub.dashboard.api;

import com.chrisdev.supporthub.common.security.AuthenticatedUser;
import com.chrisdev.supporthub.dashboard.application.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    /**
     * Cualquier usuario autenticado; el contenido depende de su rol.
     */
    @GetMapping("/summary")
    public DashboardSummaryResponse summary(@RequestParam(defaultValue = "30") int days,
            @AuthenticationPrincipal Jwt jwt) {
        return service.summary(days, AuthenticatedUser.from(jwt));
    }
}
