package com.nexatech.identity.service;

import com.nexatech.identity.dto.request.ChangePasswordRequest;
import com.nexatech.identity.dto.request.LoginRequest;
import com.nexatech.identity.dto.request.RefreshTokenRequest;
import com.nexatech.identity.dto.request.RegisterRequest;
import com.nexatech.identity.dto.response.AuthResponse;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    public AuthResponse register(RegisterRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public AuthResponse login(LoginRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public AuthResponse refreshToken(RefreshTokenRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void changePassword(UUID userId, ChangePasswordRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void logout(UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
