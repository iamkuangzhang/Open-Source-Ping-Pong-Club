package org.opensourcepingpong.competition;

import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.opensourcepingpong.auth.AuthPrincipal;
import org.opensourcepingpong.common.api.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** ScoreAPI and ScoreQueryAPI for Sprint 3 / E6-D. */
@RestController
@RequestMapping("/api/v1")
public class ScoreController {
    private final ScoreSubmissionService scores;

    public ScoreController(ScoreSubmissionService scores) {
        this.scores = scores;
    }

    @PostMapping("/matches/{id}/scores")
    ApiResponse<Map<String, Object>> submit(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID id,
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody ScoreSubmissionService.ScoreRequest request
    ) {
        return ApiResponse.ok(scores.submit(principal.userId(), id, request, idempotencyKey));
    }

    @PutMapping("/matches/{id}/scores")
    ApiResponse<Map<String, Object>> edit(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID id,
        @Valid @RequestBody ScoreSubmissionService.ScoreRequest request
    ) {
        return ApiResponse.ok(scores.edit(principal.userId(), id, request));
    }

    @PostMapping("/admin/matches/{id}/walkover")
    ApiResponse<Map<String, Object>> walkover(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID id,
        @RequestBody ScoreSubmissionService.ResultRequest request
    ) {
        return ApiResponse.ok(scores.walkover(principal.userId(), id, request));
    }

    @PostMapping("/admin/matches/{id}/retirement")
    ApiResponse<Map<String, Object>> retirement(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID id,
        @RequestBody ScoreSubmissionService.RetirementRequest request
    ) {
        return ApiResponse.ok(scores.retirement(principal.userId(), id, request));
    }

    @PostMapping("/admin/matches/{id}/score-changes/{changeId}/approve")
    ApiResponse<Map<String, Object>> approveChange(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID id,
        @PathVariable UUID changeId,
        @RequestBody(required = false) Map<String, String> body
    ) {
        String note = body == null ? null : body.get("note");
        return ApiResponse.ok(scores.approveChange(principal.userId(), id, changeId, note));
    }

    @PostMapping("/admin/matches/{id}/score-changes/{changeId}/reject")
    ApiResponse<Map<String, Object>> rejectChange(
        @AuthenticationPrincipal AuthPrincipal principal,
        @PathVariable UUID id,
        @PathVariable UUID changeId,
        @RequestBody(required = false) Map<String, String> body
    ) {
        String note = body == null ? null : body.get("note");
        return ApiResponse.ok(scores.rejectChange(principal.userId(), id, changeId, note));
    }

    @GetMapping("/events/{id}/bracket")
    ApiResponse<Map<String, Object>> bracket(@PathVariable UUID id) {
        return ApiResponse.ok(scores.bracket(id));
    }
}
