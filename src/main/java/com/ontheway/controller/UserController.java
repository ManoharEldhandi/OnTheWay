package com.ontheway.controller;

import com.ontheway.dto.UserResponseDTO;
import com.ontheway.dto.UserUpdateDTO;
import com.ontheway.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Parameter;

@RestController
@RequestMapping({"/api/users", "/api/v1/users"})
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // Get a user by id; permitted only for the user themselves or an admin.
    @PreAuthorize("hasRole('USER') or hasRole('MERCHANT') or hasRole('ADMIN')")
    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(
            @Parameter(description = "User ID to retrieve", required = true)
            @PathVariable("id") Long id,
            Authentication authentication) {

        String email = authentication.getName();
        UserResponseDTO caller = userService.getUserByEmail(email);

        if (caller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authenticated user not found.");
        }

        if (!caller.getRole().name().equals("ADMIN") && !caller.getUserId().equals(id)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You are not authorized to access this user's profile.");
        }

        return ResponseEntity.ok(userService.getUserById(id));
    }

    // Get the authenticated user's own profile.
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        UserResponseDTO userDTO = userService.getUserByEmail(email);

        if (userDTO == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Current user not found.");
        }

        return ResponseEntity.ok(userDTO);
    }

    // Update a user profile; permitted only for the user themselves or an admin.
    @PreAuthorize("hasRole('USER') or hasRole('MERCHANT') or hasRole('ADMIN')")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(
            @Parameter(description = "User ID to update", required = true)
            @PathVariable("id") Long id,
            @Valid @RequestBody UserUpdateDTO dto,
            Authentication authentication) {

        String email = authentication.getName();
        UserResponseDTO caller = userService.getUserByEmail(email);

        if (caller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authenticated user not found.");
        }

        boolean isAdmin = caller.getRole().name().equals("ADMIN");
        boolean isSelf = caller.getUserId().equals(id);

        if (!isAdmin && !isSelf) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You are not authorized to update this user.");
        }

        return ResponseEntity.ok(userService.updateUser(id, dto));
    }

    // Delete a user; permitted only for the user themselves or an admin.
    @PreAuthorize("hasRole('USER') or hasRole('MERCHANT') or hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(
            @Parameter(description = "User ID to delete", required = true)
            @PathVariable("id") Long id,
            Authentication authentication) {

        String email = authentication.getName();
        UserResponseDTO caller = userService.getUserByEmail(email);

        if (caller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authenticated user not found.");
        }

        boolean isAdmin = caller.getRole().name().equals("ADMIN");
        boolean isSelf = caller.getUserId().equals(id);

        if (!isAdmin && !isSelf) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("You are not authorized to delete this user.");
        }

        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }
}
