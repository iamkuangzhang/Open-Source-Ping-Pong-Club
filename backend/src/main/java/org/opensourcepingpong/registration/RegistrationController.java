package org.opensourcepingpong.registration;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.common.api.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** RegistrationAPI for Sprint 3 / E6-B. */
@RestController
@RequestMapping("/api/v1")
public class RegistrationController {
    private final RegistrationService registrations;

    public RegistrationController(RegistrationService registrations) {
        this.registrations = registrations;
    }

    @PostMapping({"/events/{eventId}/registrations", "/competitions/{eventId}/registrations"})
    ApiResponse<Map<String, Object>> register(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID eventId,
        @Valid @RequestBody RegistrationService.RegisterRequest request
    ) {
        return ApiResponse.ok(registrations.register(principal.userId(), eventId, request));
    }

    @PostMapping("/registrations/{id}/pay")
    ApiResponse<Map<String, Object>> pay(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID id
    ) {
        return ApiResponse.ok(registrations.pay(principal.userId(), id));
    }

    @DeleteMapping("/registrations/{id}")
    ApiResponse<Map<String, Object>> withdraw(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID id
    ) {
        return ApiResponse.ok(registrations.withdraw(principal.userId(), id));
    }

    @GetMapping("/me/registrations")
    ApiResponse<List<Map<String, Object>>> mine(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.ok(registrations.mine(principal.userId()));
    }
}
