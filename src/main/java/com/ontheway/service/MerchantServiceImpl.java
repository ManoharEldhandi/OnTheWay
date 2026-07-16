package com.ontheway.service.impl;

import com.ontheway.dto.*;
import com.ontheway.exception.BadRequestException;
import com.ontheway.exception.ConflictException;
import com.ontheway.exception.ForbiddenException;
import com.ontheway.exception.ResourceNotFoundException;
import com.ontheway.model.*;
import com.ontheway.model.enums.MerchantStatus;
import com.ontheway.repository.*;
import com.ontheway.service.MerchantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MerchantServiceImpl implements MerchantService {

    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    @Transactional
    @Override
    public MerchantResponseDTO applyForShop(String email, MerchantCreateDTO dto) {
        User owner = resolveUser(email);
        validateLocation(dto.getLatitude(), dto.getLongitude());

        // A new shop is an application: it starts PENDING and is not yet discoverable.
        Merchant shop = Merchant.builder()
                .user(owner)
                .storeName(dto.getStoreName())
                .storeType(dto.getStoreType())
                .status(MerchantStatus.PENDING)
                .address(dto.getAddress())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .prepTimeMins(dto.getPrepTimeMins())
                .etaBufferMins(dto.getEtaBufferMins())
                .build();

        merchantRepository.save(shop);
        return toResponseDTO(shop);
    }

    @Override
    public List<MerchantResponseDTO> listMyShops(String email) {
        User owner = resolveUser(email);
        return merchantRepository.findByUser_UserId(owner.getUserId())
                .stream().map(this::toResponseDTO).toList();
    }

    @Override
    public MerchantResponseDTO getMyShop(String email, Long shopId) {
        return toResponseDTO(requireOwnedShop(email, shopId));
    }

    @Transactional
    @Override
    public MerchantResponseDTO updateMyShop(String email, Long shopId, MerchantUpdateDTO dto) {
        Merchant shop = requireOwnedShop(email, shopId);
        validateLocation(dto.getLatitude(), dto.getLongitude());
        shop.setStoreName(dto.getStoreName());
        shop.setAddress(dto.getAddress());
        shop.setLatitude(dto.getLatitude());
        shop.setLongitude(dto.getLongitude());
        shop.setPrepTimeMins(dto.getPrepTimeMins());
        shop.setEtaBufferMins(dto.getEtaBufferMins());
        merchantRepository.save(shop);
        return toResponseDTO(shop);
    }

    @Transactional
    @Override
    public void deleteMyShop(String email, Long shopId) {
        Merchant shop = requireOwnedShop(email, shopId);
        if (orderRepository.existsByMerchantMerchantId(shopId)) {
            throw new ConflictException(
                    "Shops with order history cannot be deleted; ask an administrator to suspend it");
        }
        merchantRepository.delete(shop);
    }

    @Override
    public MerchantResponseDTO getMerchantById(Long merchantId) {
        return merchantRepository.findById(merchantId)
                .map(this::toResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
    }

    // ----- helpers -------------------------------------------------------

    private User resolveUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    /** Loads a shop and verifies the caller owns it. */
    private Merchant requireOwnedShop(String email, Long shopId) {
        User owner = resolveUser(email);
        Merchant shop = merchantRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
        if (!shop.getUser().getUserId().equals(owner.getUserId())) {
            throw new ForbiddenException("You do not own this shop");
        }
        return shop;
    }

    private void validateLocation(Double latitude, Double longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new BadRequestException("Latitude and longitude must be provided together");
        }
    }

    private MerchantResponseDTO toResponseDTO(Merchant merchant) {
        return MerchantResponseDTO.builder()
                .merchantId(merchant.getMerchantId())
                .userId(merchant.getUser().getUserId())
                .storeName(merchant.getStoreName())
                .storeType(merchant.getStoreType())
                .status(merchant.getStatus())
                .statusReason(merchant.getStatusReason())
                .address(merchant.getAddress())
                .latitude(merchant.getLatitude())
                .longitude(merchant.getLongitude())
                .prepTimeMins(merchant.getPrepTimeMins())
                .etaBufferMins(merchant.getEtaBufferMins())
                .createdAt(merchant.getCreatedAt())
                .updatedAt(merchant.getUpdatedAt())
                .build();
    }
}
