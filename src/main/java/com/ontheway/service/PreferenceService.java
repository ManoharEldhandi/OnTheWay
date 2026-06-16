package com.ontheway.service;

import com.ontheway.dto.*;

public interface PreferenceService {
    PreferenceResponseDTO getPreferenceByUserId(Long userId);
    PreferenceResponseDTO updatePreference(Long userId, PreferenceUpdateDTO dto);

    /** Returns the user's preferences, creating a default record on first access. */
    PreferenceResponseDTO getOrCreatePreference(Long userId);
}
