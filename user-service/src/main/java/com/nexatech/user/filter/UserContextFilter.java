// package com.nexatech.user.filter;

// import jakarta.servlet.*;
// import jakarta.servlet.http.HttpServletRequest;
// import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
// import org.springframework.security.core.authority.SimpleGrantedAuthority;
// import org.springframework.security.core.context.SecurityContextHolder;

// import java.io.IOException;
// import java.util.Arrays;
// import java.util.Collections;
// import java.util.List;

// public class UserContextFilter implements Filter {

//     private static final String HEADER_USER_EMAIL = "X-User-Email";
//     private static final String HEADER_USER_KEYCLOAK_ID = "X-User-KeycloakId";
//     private static final String HEADER_USER_AUTHORITIES = "X-User-Authorities";

//     @Override
//     public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
//             throws IOException, ServletException {

//         HttpServletRequest httpRequest = (HttpServletRequest) request;

//         // Workflow:
//         // - Read pre-authenticated identity from gateway headers.
//         // - Populate Spring SecurityContext + ThreadLocal UserContext for downstream code.
//         // - Always clear both contexts in finally to avoid leaking across requests.
//         String email = httpRequest.getHeader(HEADER_USER_EMAIL);
//         String keycloakId = httpRequest.getHeader(HEADER_USER_KEYCLOAK_ID);
//         String authoritiesRaw = httpRequest.getHeader(HEADER_USER_AUTHORITIES);

//         List<SimpleGrantedAuthority> authorities = parseAuthorities(authoritiesRaw);
//         boolean hasIdentity = hasText(email) || hasText(keycloakId);

//         try {
//             if (hasIdentity || !authorities.isEmpty()) {
//                 String principal = hasText(email) ? email : keycloakId;
//                 var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
//                 SecurityContextHolder.getContext().setAuthentication(auth);
//             }

//             if (hasIdentity) {
//                 UserContext.set(UserContext.builder()
//                         .email(email)
//                         .keycloakId(keycloakId)
//                         .build());
//             }

//             chain.doFilter(request, response);
//         } finally {
//             UserContext.clear();
//             SecurityContextHolder.clearContext();
//         }
//     }

//     private boolean hasText(String value) {
//         return value != null && !value.isBlank();
//     }

//     private List<SimpleGrantedAuthority> parseAuthorities(String authoritiesRaw) {
//         if (!hasText(authoritiesRaw)) return Collections.emptyList();

//         return Arrays.stream(authoritiesRaw.split(","))
//                 .map(String::trim)
//                 .filter(this::hasText)
//                 .map(SimpleGrantedAuthority::new)
//                 .toList();
//     }
// }
