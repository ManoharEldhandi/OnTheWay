package com.ontheway.controller;

import com.ontheway.dto.*;
import com.ontheway.model.User;
import com.ontheway.repository.UserRepository;
import com.ontheway.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/api/locations", "/api/v1/locations"})
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;
    private final UserRepository userRepository;

    @PreAuthorize("hasRole('USER')")
    @PostMapping
    public ResponseEntity<LocationResponseDTO> saveLocation(Authentication auth,
                                                            @Valid @RequestBody LocationCreateDTO dto) {
        Long userId = extractUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(locationService.saveLocation(userId, dto));
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping
    public ResponseEntity<List<LocationResponseDTO>> getUserLocations(Authentication auth) {
        Long userId = extractUserId(auth);
        return ResponseEntity.ok(locationService.getUserLocations(userId));
    }

    private Long extractUserId(Authentication authentication) {
        String email = authentication.getName(); // JWT subject = email
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new RuntimeException("User not found with email: " + email));
        return user.getUserId();
    }
}
