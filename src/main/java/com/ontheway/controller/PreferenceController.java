package com.ontheway.controller;

import com.ontheway.dto.*;
import com.ontheway.model.User;
import com.ontheway.repository.UserRepository;
import com.ontheway.service.PreferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/preferences", "/api/v1/preferences"})
@RequiredArgsConstructor
public class PreferenceController {

    private final PreferenceService preferenceService;
    private final UserRepository userRepository;

    @PreAuthorize("hasRole('USER')")
    @GetMapping
    public ResponseEntity<PreferenceResponseDTO> getPreference(Authentication auth) {
        Long userId = extractUserId(auth);
        return ResponseEntity.ok(preferenceService.getOrCreatePreference(userId));
    }

    @PreAuthorize("hasRole('USER')")
    @PutMapping
    public ResponseEntity<PreferenceResponseDTO> updatePreference(Authentication auth,
                                                                  @Valid @RequestBody PreferenceUpdateDTO dto) {
        Long userId = extractUserId(auth);
        return ResponseEntity.ok(preferenceService.updatePreference(userId, dto));
    }

    private Long extractUserId(Authentication authentication) {
        String email = authentication.getName(); // JWT subject = email
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getUserId();
    }
}
