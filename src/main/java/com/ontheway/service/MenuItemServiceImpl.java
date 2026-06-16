package com.ontheway.service.impl;

import com.ontheway.dto.*;
import com.ontheway.exception.ForbiddenException;
import com.ontheway.exception.ResourceNotFoundException;
import com.ontheway.model.*;
import com.ontheway.model.enums.UserRole;
import com.ontheway.repository.*;
import com.ontheway.service.MenuItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuItemServiceImpl implements MenuItemService {
    private final MerchantRepository merchantRepository;
    private final MenuItemRepository menuItemRepository;
    private final UserRepository userRepository;

    @Transactional
    @Override
    public MenuItemResponseDTO addMenuItem(Long merchantId, MenuItemCreateDTO dto, String callerEmail) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found"));
        assertOwnsMerchant(merchant, callerEmail);
        MenuItem item = MenuItem.builder()
                .merchant(merchant)
                .name(dto.getName())
                .description(dto.getDescription())
                .price(dto.getPrice())
                .availability(dto.getAvailability())
                .build();
        menuItemRepository.save(item);
        return toResponseDTO(item);
    }

    @Transactional
    @Override
    public MenuItemResponseDTO updateMenuItem(Long menuItemId, MenuItemUpdateDTO dto, String callerEmail) {
        MenuItem item = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found"));
        assertOwnsMerchant(item.getMerchant(), callerEmail);
        item.setPrice(dto.getPrice());
        item.setAvailability(dto.getAvailability());
        menuItemRepository.save(item);
        return toResponseDTO(item);
    }

    @Transactional
    @Override
    public void deleteMenuItem(Long menuItemId, String callerEmail) {
        MenuItem item = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found"));
        assertOwnsMerchant(item.getMerchant(), callerEmail);
        menuItemRepository.delete(item);
    }

    @Override
    public MenuItemResponseDTO getMenuItemById(Long menuItemId) {
        return menuItemRepository.findById(menuItemId)
                .map(this::toResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found"));
    }

    @Override
    public List<MenuItemResponseDTO> getMenuItemsByMerchant(Long merchantId) {
        return menuItemRepository.findByMerchantMerchantId(merchantId)
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    /** A merchant may only manage menu items belonging to their own store; admins may manage any. */
    private void assertOwnsMerchant(Merchant merchant, String callerEmail) {
        User caller = userRepository.findByEmailIgnoreCase(callerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));
        boolean isOwner = merchant.getUser().getUserId().equals(caller.getUserId());
        boolean isAdmin = caller.getRole() == UserRole.ADMIN;
        if (!(isOwner || isAdmin)) {
            throw new ForbiddenException("You are not allowed to manage this merchant's menu");
        }
    }

    private MenuItemResponseDTO toResponseDTO(MenuItem item) {
        return MenuItemResponseDTO.builder()
                .menuItemId(item.getMenuItemId())
                .merchantId(item.getMerchant().getMerchantId())
                .name(item.getName())
                .description(item.getDescription())
                .price(item.getPrice())
                .availability(item.getAvailability())
                .createdAt(item.getCreatedAt())
                .updatedAt(item.getUpdatedAt())
                .build();
    }
}
