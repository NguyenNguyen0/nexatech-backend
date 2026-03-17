package com.nexatech.identity.service;

import com.nexatech.identity.enums.UserRole;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class TokenService {

    public String generateAccessToken(UUID userId, String email, UserRole role) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public String generateRefreshToken(UUID userId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public UUID validateAccessToken(String token) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public void blacklistToken(String token) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
