package com.reeltrip.domain.admin;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.reeltrip.domain.place.dto.PlaceResponse;
import com.reeltrip.domain.plan.dto.PlanDetailResponse;
import com.reeltrip.global.security.LoginUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@RestController
@RequestMapping("/admin/api")
@Validated
public class AdminController {
    private final AdminService admin;
    public AdminController(AdminService admin) { this.admin = admin; }

    @GetMapping("/me")
    public AdminService.Account me(@LoginUser Long actor) { return admin.me(actor); }

    @GetMapping("/users")
    public Map<String, Object> users(@LoginUser Long actor, @RequestParam(defaultValue = "") @Size(max = 100) String query,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return admin.users(actor, query, page, size);
    }

    @GetMapping("/users/{userId}/sources")
    public Map<String, Object> sources(@LoginUser Long actor, @PathVariable @Positive Long userId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return admin.sources(actor, userId, page, size);
    }

    @GetMapping("/users/{userId}/places")
    public Map<String, Object> places(@LoginUser Long actor, @PathVariable @Positive Long userId,
            @RequestParam(defaultValue = "false") boolean trash,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return admin.places(actor, userId, trash, page, size);
    }

    @GetMapping("/users/{userId}/places/{placeId}")
    public PlaceResponse place(@LoginUser Long actor, @PathVariable @Positive Long userId,
            @PathVariable @Positive Long placeId) { return admin.place(actor, userId, placeId); }

    @PatchMapping("/users/{userId}/places/{placeId}")
    public PlaceResponse editPlace(@LoginUser Long actor, @PathVariable @Positive Long userId,
            @PathVariable @Positive Long placeId, @RequestBody JsonNode body) {
        return admin.editPlace(actor, userId, placeId, body);
    }

    @DeleteMapping("/users/{userId}/places/{placeId}")
    public ResponseEntity<Void> trashPlace(@LoginUser Long actor, @PathVariable @Positive Long userId,
            @PathVariable @Positive Long placeId) {
        admin.trashPlace(actor, userId, placeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/{userId}/places/{placeId}/restorations")
    public ResponseEntity<Void> restore(@LoginUser Long actor, @PathVariable @Positive Long userId,
            @PathVariable @Positive Long placeId) {
        admin.restorePlace(actor, userId, placeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{userId}/plans")
    public Map<String, Object> plans(@LoginUser Long actor, @PathVariable @Positive Long userId,
            @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return admin.plans(actor, userId, page, size);
    }

    @GetMapping("/users/{userId}/plans/{planId}")
    public PlanDetailResponse plan(@LoginUser Long actor, @PathVariable @Positive Long userId,
            @PathVariable @Positive Long planId) { return admin.plan(actor, userId, planId); }

    @DeleteMapping("/users/{userId}/plans/{planId}")
    public ResponseEntity<Void> removePlan(@LoginUser Long actor, @PathVariable @Positive Long userId,
            @PathVariable @Positive Long planId) {
        admin.removePlan(actor, userId, planId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{userId}/reset-preview")
    public AdminService.ResetPreview preview(@LoginUser Long actor, @PathVariable @Positive Long userId) {
        return admin.preview(actor, userId);
    }

    @PostMapping("/users/{userId}/resets")
    public Map<String, Integer> reset(@LoginUser Long actor, @PathVariable @Positive Long userId,
            @Valid @RequestBody ResetRequest request) {
        return admin.reset(actor, userId, request.fingerprint(), request.confirmation());
    }

    @PostMapping("/users/{userId}/samples")
    public Map<String, Object> seed(@LoginUser Long actor, @PathVariable @Positive Long userId,
            @Valid @RequestBody SampleRequest request) { return admin.seed(actor, userId, request.imageUrl()); }

    public record ResetRequest(@NotNull @Pattern(regexp = "[0-9a-f]{64}") String fingerprint,
                               @NotBlank @Size(max = 50) String confirmation) { }
    public record SampleRequest(@NotBlank @Size(max = 2048) String imageUrl) { }
}
