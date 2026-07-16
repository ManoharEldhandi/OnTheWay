package com.ontheway.service.impl;

import com.ontheway.dto.*;
import com.ontheway.exception.ResourceNotFoundException;
import com.ontheway.model.*;
import com.ontheway.repository.*;
import com.ontheway.service.PreferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PreferenceServiceImpl implements PreferenceService {

    private final PreferenceRepository preferenceRepository;
    private final UserRepository userRepository;

    @Override
    public PreferenceResponseDTO getPreferenceByUserId(Long userId) {
        return preferenceRepository.findByUserUserId(userId)
                .map(this::toResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Preference not found"));
    }

    @Transactional
    @Override
    public PreferenceResponseDTO getOrCreatePreference(Long userId) {
        Preference pref = preferenceRepository.findByUserUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                    Preference newPref = Preference.builder()
                            .user(user)
                            .vegNonVeg(null)
                            .favoriteCuisine(null)
                            .build();
                    return preferenceRepository.save(newPref);
                });

        return toResponseDTO(pref);
    }

    @Transactional
    @Override
    public PreferenceResponseDTO updatePreference(Long userId, PreferenceUpdateDTO dto) {
        Preference pref = preferenceRepository.findByUserUserId(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
                    return Preference.builder().user(user).build();
                });

        pref.setVegNonVeg(dto.getVegNonVeg());
        pref.setFavoriteCuisine(dto.getFavoriteCuisine());
        preferenceRepository.save(pref);
        return toResponseDTO(pref);
    }

    private PreferenceResponseDTO toResponseDTO(Preference pref) {
        return PreferenceResponseDTO.builder()
                .preferenceId(pref.getPreferenceId())
                .userId(pref.getUser().getUserId())
                .vegNonVeg(pref.getVegNonVeg())
                .favoriteCuisine(pref.getFavoriteCuisine())
                .createdAt(pref.getCreatedAt())
                .updatedAt(pref.getUpdatedAt())
                .build();
    }
}
