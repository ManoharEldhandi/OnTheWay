package com.ontheway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ontheway.model.enums.MerchantStatus;
import com.ontheway.model.enums.StoreType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MerchantResponseDTO {
    @JsonProperty("merchantId")
    private Long merchantId;

    @JsonProperty("userId")
    private Long userId;

    @JsonProperty("storeName")
    private String storeName;

    @JsonProperty("storeType")
    private StoreType storeType;

    @JsonProperty("status")
    private MerchantStatus status;

    @JsonProperty("statusReason")
    private String statusReason;

    @JsonProperty("address")
    private String address;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("prepTimeMins")
    private Integer prepTimeMins;

    @JsonProperty("etaBufferMins")
    private Integer etaBufferMins;

    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    @JsonProperty("updatedAt")
    private LocalDateTime updatedAt;
}
