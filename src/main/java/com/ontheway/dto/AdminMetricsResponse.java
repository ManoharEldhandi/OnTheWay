package com.ontheway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Platform overview shown on the administrator dashboard.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminMetricsResponse {

    @JsonProperty("totalUsers")
    private long totalUsers;

    @JsonProperty("totalCustomers")
    private long totalCustomers;

    @JsonProperty("totalMerchants")
    private long totalMerchants;

    @JsonProperty("totalShops")
    private long totalShops;

    @JsonProperty("approvedShops")
    private long approvedShops;

    @JsonProperty("pendingShops")
    private long pendingShops;

    @JsonProperty("suspendedShops")
    private long suspendedShops;

    @JsonProperty("rejectedShops")
    private long rejectedShops;

    @JsonProperty("totalOrders")
    private long totalOrders;

    @JsonProperty("ordersByStatus")
    private Map<String, Long> ordersByStatus;

    @JsonProperty("grossRevenue")
    private double grossRevenue;
}
