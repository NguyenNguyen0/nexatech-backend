package com.nexatech.identity.dto.request;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserTokenExchangeParam {
    String username;
    String password;
    String grant_type;
    String client_id;
    String client_secret;
    String scope;
    /** Token exchange: service account access token. */
    String subject_token;
    /** Token exchange: target Keycloak user id. */
    String requested_subject;
    /** Token exchange: audience (typically the same client_id). */
    String audience;
    /** Token exchange: requested token type (access_token). */
    String requested_token_type;
}
