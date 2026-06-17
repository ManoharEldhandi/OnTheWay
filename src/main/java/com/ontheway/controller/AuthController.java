package com.ontheway.controller;

import com.ontheway.dto.LoginRequest;
import com.ontheway.dto.RefreshTokenRequest;
import com.ontheway.dto.UserCreateDTO;
import com.ontheway.security.JwtTokenProvider;
import com.ontheway.service.AuthTokenService;
import com.ontheway.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/auth", "/api/v1/auth"})
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthTokenService authTokenService;
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody UserCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.registerUser(dto));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest dto) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(dto.getEmail(), dto.getPassword())
        );
        String token = jwtTokenProvider.generateToken(authentication);
        String refreshToken = authTokenService.issueRefreshToken(authentication.getName());
        return ResponseEntity.ok().body(AuthResponse.of(
                token, refreshToken, jwtTokenProvider.getAccessTokenExpirationMs()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest dto) {
        AuthTokenService.TokenPair pair = authTokenService.rotate(dto.getRefreshToken());
        return ResponseEntity.ok(AuthResponse.of(
                pair.accessToken(), pair.refreshToken(), jwtTokenProvider.getAccessTokenExpirationMs()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest dto) {
        authTokenService.revoke(dto.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    // Keeps the legacy "token" field while adding explicit refresh-token metadata.
    private record AuthResponse(String token, String accessToken, String refreshToken,
                                String tokenType, long expiresInMs) {
        static AuthResponse of(String accessToken, String refreshToken, long expiresInMs) {
            return new AuthResponse(accessToken, accessToken, refreshToken, "Bearer", expiresInMs);
        }
    }
}
