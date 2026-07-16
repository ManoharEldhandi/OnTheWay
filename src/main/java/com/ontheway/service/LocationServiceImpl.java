package com.ontheway.service.impl;

import com.ontheway.dto.*;
import com.ontheway.exception.ResourceNotFoundException;
import com.ontheway.model.*;
import com.ontheway.repository.*;
import com.ontheway.service.LocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LocationServiceImpl implements LocationService {
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;

    @Transactional
    @Override
    public LocationResponseDTO saveLocation(Long userId, LocationCreateDTO dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Location location = Location.builder()
                .user(user)
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .recordedTime(LocalDateTime.now())
                .build();
        locationRepository.save(location);
        return toResponseDTO(location);
    }

    @Override
    public List<LocationResponseDTO> getUserLocations(Long userId) {
        return locationRepository.findByUserUserId(userId)
                .stream().map(this::toResponseDTO).collect(Collectors.toList());
    }

    private LocationResponseDTO toResponseDTO(Location loc) {
        return LocationResponseDTO.builder()
                .locationId(loc.getLocationId())
                .userId(loc.getUser().getUserId())
                .latitude(loc.getLatitude())
                .longitude(loc.getLongitude())
                .recordedTime(loc.getRecordedTime())
                .createdAt(loc.getCreatedAt())
                .updatedAt(loc.getUpdatedAt())
                .build();
    }
}
