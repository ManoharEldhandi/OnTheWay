package com.ontheway.service.impl;

import com.ontheway.dto.AdminMetricsResponse;
import com.ontheway.dto.MerchantResponseDTO;
import com.ontheway.exception.BadRequestException;
import com.ontheway.exception.ResourceNotFoundException;
import com.ontheway.model.Merchant;
import com.ontheway.model.enums.MerchantStatus;
import com.ontheway.model.enums.OrderStatus;
import com.ontheway.model.enums.UserRole;
import com.ontheway.repository.MerchantRepository;
import com.ontheway.repository.OrderRepository;
import com.ontheway.repository.PaymentRepository;
import com.ontheway.repository.UserRepository;
import com.ontheway.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final MerchantRepository merchantRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    @Override
    public List<MerchantResponseDTO> listPendingShops() {
        return merchantRepository.findByStatus(MerchantStatus.PENDING)
                .stream().map(this::toResponseDTO).toList();
    }

    @Override
    public List<MerchantResponseDTO> listShops(MerchantStatus status) {
        List<Merchant> shops = (status == null)
                ? merchantRepository.findAll()
                : merchantRepository.findByStatus(status);
        return shops.stream().map(this::toResponseDTO).toList();
    }

    @Transactional
    @Override
    public MerchantResponseDTO approveShop(Long shopId) {
        Merchant shop = requireShop(shopId);
        if (shop.getStatus() == MerchantStatus.APPROVED) {
            throw new BadRequestException("Shop is already approved");
        }
        shop.setStatus(MerchantStatus.APPROVED);
        shop.setStatusReason(null);
        return toResponseDTO(merchantRepository.save(shop));
    }

    @Transactional
    @Override
    public MerchantResponseDTO rejectShop(Long shopId, String reason) {
        Merchant shop = requireShop(shopId);
        shop.setStatus(MerchantStatus.REJECTED);
        shop.setStatusReason(reason);
        return toResponseDTO(merchantRepository.save(shop));
    }

    @Transactional
    @Override
    public MerchantResponseDTO suspendShop(Long shopId, String reason) {
        Merchant shop = requireShop(shopId);
        shop.setStatus(MerchantStatus.SUSPENDED);
        shop.setStatusReason(reason);
        return toResponseDTO(merchantRepository.save(shop));
    }

    @Transactional
    @Override
    public MerchantResponseDTO reactivateShop(Long shopId) {
        Merchant shop = requireShop(shopId);
        if (shop.getStatus() != MerchantStatus.SUSPENDED) {
            throw new BadRequestException("Only a suspended shop can be reactivated");
        }
        shop.setStatus(MerchantStatus.APPROVED);
        shop.setStatusReason(null);
        return toResponseDTO(merchantRepository.save(shop));
    }

    @Transactional
    @Override
    public void deleteShop(Long shopId) {
        Merchant shop = requireShop(shopId);
        merchantRepository.delete(shop);
    }

    @Override
    public AdminMetricsResponse metrics() {
        Map<String, Long> ordersByStatus = new LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) {
            ordersByStatus.put(status.name(), orderRepository.countByStatus(status));
        }

        return AdminMetricsResponse.builder()
                .totalUsers(userRepository.count())
                .totalCustomers(userRepository.countByRole(UserRole.USER))
                .totalMerchants(userRepository.countByRole(UserRole.MERCHANT))
                .totalShops(merchantRepository.count())
                .approvedShops(merchantRepository.countByStatus(MerchantStatus.APPROVED))
                .pendingShops(merchantRepository.countByStatus(MerchantStatus.PENDING))
                .suspendedShops(merchantRepository.countByStatus(MerchantStatus.SUSPENDED))
                .rejectedShops(merchantRepository.countByStatus(MerchantStatus.REJECTED))
                .totalOrders(orderRepository.count())
                .ordersByStatus(ordersByStatus)
                .grossRevenue(paymentRepository.sumCompletedRevenue())
                .build();
    }

    // ----- helpers -------------------------------------------------------

    private Merchant requireShop(Long shopId) {
        return merchantRepository.findById(shopId)
                .orElseThrow(() -> new ResourceNotFoundException("Shop not found"));
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
