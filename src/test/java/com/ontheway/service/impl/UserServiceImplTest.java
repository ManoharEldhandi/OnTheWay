package com.ontheway.service.impl;

import com.ontheway.dto.UserCreateDTO;
import com.ontheway.dto.UserResponseDTO;
import com.ontheway.exception.BadRequestException;
import com.ontheway.exception.ConflictException;
import com.ontheway.model.User;
import com.ontheway.model.enums.UserRole;
import com.ontheway.repository.UserRepository;
import com.ontheway.repository.MerchantRepository;
import com.ontheway.repository.OrderRepository;
import com.ontheway.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private MerchantRepository merchantRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @InjectMocks
    private UserServiceImpl userService;

    private UserCreateDTO dto(String email, UserRole role) {
        return UserCreateDTO.builder()
                .email(email).password("password123").name("Test User").role(role).build();
    }

    @Test
    void register_rejectsSelfAssignedAdmin() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.registerUser(dto("a@x.com", UserRole.ADMIN)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("ADMIN");

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_rejectsDuplicateEmailWith409() {
        when(userRepository.findByEmailIgnoreCase("dup@x.com"))
                .thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> userService.registerUser(dto("dup@x.com", UserRole.USER)))
                .isInstanceOf(ConflictException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    void register_defaultsToUserRole_andHashesPassword() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponseDTO result = userService.registerUser(dto("new@x.com", null));

        assertThat(result.getRole()).isEqualTo(UserRole.USER);
        verify(userRepository).save(argThat(u ->
                u.getPassword().equals("HASHED") && u.getRole() == UserRole.USER));
    }

    @Test
    void register_allowsMerchantSignup() {
        when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("HASHED");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserResponseDTO result = userService.registerUser(dto("merchant@x.com", UserRole.MERCHANT));

        assertThat(result.getRole()).isEqualTo(UserRole.MERCHANT);
    }
}
