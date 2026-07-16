package com.ontheway.service.impl;

import com.ontheway.dto.*;
import com.ontheway.exception.BadRequestException;
import com.ontheway.exception.ConflictException;
import com.ontheway.exception.ResourceNotFoundException;
import com.ontheway.model.User;
import com.ontheway.model.enums.UserRole;
import com.ontheway.repository.MerchantRepository;
import com.ontheway.repository.OrderRepository;
import com.ontheway.repository.RefreshTokenRepository;
import com.ontheway.repository.UserRepository;
import com.ontheway.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MerchantRepository merchantRepository;
    private final OrderRepository orderRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    @Override
    public UserResponseDTO registerUser(UserCreateDTO dto) {
        String normalizedEmail = dto.getEmail().trim().toLowerCase(Locale.ROOT);
        // Prevent duplicate accounts (case-insensitive) -> 409 instead of a raw DB error.
        if (userRepository.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new ConflictException("An account with this email already exists");
        }

        // Security: ADMIN can never be self-assigned at registration. Default to USER.
        UserRole requestedRole = dto.getRole() == null ? UserRole.USER : dto.getRole();
        if (requestedRole == UserRole.ADMIN) {
            throw new BadRequestException("ADMIN accounts cannot be self-registered");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .password(passwordEncoder.encode(dto.getPassword()))
                .name(dto.getName())
                .role(requestedRole)
                .build();
        userRepository.save(user);
        return toResponseDTO(user);
    }

    @Override
    public UserResponseDTO getUserById(Long userId) {
        return userRepository.findById(userId)
                .map(this::toResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional
    @Override
    public UserResponseDTO updateUser(Long userId, UserUpdateDTO dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setName(dto.getName());
        userRepository.save(user);
        return toResponseDTO(user);
    }

    @Transactional
    @Override
    public void deleteUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found");
        }
        if (merchantRepository.existsByUser_UserId(userId)
                || orderRepository.existsByUserUserId(userId)) {
            throw new ConflictException(
                    "Accounts with shop or order history cannot be deleted");
        }
        refreshTokenRepository.deleteByUserUserId(userId);
        userRepository.deleteById(userId);
    }

    @Override
    public UserResponseDTO getUserByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .map(this::toResponseDTO)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private UserResponseDTO toResponseDTO(User user) {
        return UserResponseDTO.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}
