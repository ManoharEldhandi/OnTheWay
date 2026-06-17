package com.ontheway.controller;

import com.ontheway.dto.EtaQuoteRequest;
import com.ontheway.dto.EtaQuoteResponse;
import com.ontheway.exception.ResourceNotFoundException;
import com.ontheway.fulfillment.EtaCalculation;
import com.ontheway.fulfillment.EtaService;
import com.ontheway.fulfillment.GeoPoint;
import com.ontheway.model.Merchant;
import com.ontheway.repository.MerchantRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * ETA quotes: "if I leave now, when will my order be ready?"
 * Powers the customer-facing countdown before an order is placed.
 */
@RestController
@RequestMapping("/api/eta")
@RequiredArgsConstructor
public class EtaController {

    private final MerchantRepository merchantRepository;
    private final EtaService etaService;

    @PreAuthorize("hasAnyRole('USER', 'MERCHANT', 'ADMIN')")
    @PostMapping("/quote")
    public ResponseEntity<EtaQuoteResponse> quote(@Valid @RequestBody EtaQuoteRequest request) {
        Merchant merchant = merchantRepository.findById(request.getMerchantId())
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"));

        EtaCalculation eta = etaService.estimate(
                new GeoPoint(request.getLatitude(), request.getLongitude()), merchant);

        return ResponseEntity.ok(toQuote(merchant.getMerchantId(), eta));
    }

    /** Maps an ETA calculation to the API response, including the traffic-aware arrival window. */
    public static EtaQuoteResponse toQuote(Long merchantId, EtaCalculation eta) {
        return EtaQuoteResponse.builder()
                .merchantId(merchantId)
                .distanceKm(Math.round(eta.distanceKm() * 100.0) / 100.0)
                .travelMins(eta.travelMins())
                .prepTimeMins(eta.prepTimeMins())
                .bufferMins(eta.bufferMins())
                .trafficBufferMins(eta.trafficBufferMins())
                .prepStartAt(eta.prepStartAt())
                .readyAt(eta.readyAt())
                .etaEarliest(eta.etaEarliest())
                .etaLatest(eta.etaLatest())
                .build();
    }
}
