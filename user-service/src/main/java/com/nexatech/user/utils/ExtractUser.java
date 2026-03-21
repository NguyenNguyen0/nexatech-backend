package com.nexatech.user.utils;

import org.springframework.http.ResponseEntity;

public class ExtractUser {
    public static String extractUserId(ResponseEntity<?> response) {
        String location = response.getHeaders().get("Location").getFirst();
        String[] splitedStr = location.split("/");
        return splitedStr[splitedStr.length - 1];
    }
}
