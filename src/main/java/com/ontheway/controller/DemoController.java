package com.ontheway.controller;

import com.ontheway.dto.DemoStatusResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exists only in the seeded, in-memory demo profile. It lets the client expose deterministic
 * route playback without leaking a presenter-only control into normal deployments.
 */
@RestController
@RequestMapping("/api/demo")
@ConditionalOnProperty(name = "ontheway.seed.enabled", havingValue = "true")
public class DemoController {

    @GetMapping("/status")
    @PreAuthorize("hasRole('USER')")
    public DemoStatusResponse status() {
        return new DemoStatusResponse(true);
    }
}
