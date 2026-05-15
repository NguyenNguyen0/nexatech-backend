package com.nexatech.identity.dto.response;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionResponse {

    private String id;
    private String userId;
    private String username;
    private String type;
    private String started;
    private String lastAccess;
    private String ipAddress;
    private List<String> clients;
    private boolean current;
    private boolean rememberMe;
}
