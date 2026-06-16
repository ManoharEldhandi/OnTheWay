package com.ontheway.service;

import com.ontheway.dto.*;

import java.util.List;

public interface MenuItemService {
    MenuItemResponseDTO addMenuItem(Long merchantId, MenuItemCreateDTO dto, String callerEmail);
    MenuItemResponseDTO updateMenuItem(Long menuItemId, MenuItemUpdateDTO dto, String callerEmail);
    void deleteMenuItem(Long menuItemId, String callerEmail);
    MenuItemResponseDTO getMenuItemById(Long menuItemId);
    List<MenuItemResponseDTO> getMenuItemsByMerchant(Long merchantId);
}
