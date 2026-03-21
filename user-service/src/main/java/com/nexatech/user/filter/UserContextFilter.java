// package com.nexatech.user.filter;

// import jakarta.servlet.*;
// import jakarta.servlet.http.HttpServletRequest;
// import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
// import org.springframework.security.core.authority.SimpleGrantedAuthority;
// import org.springframework.security.core.context.SecurityContextHolder;
// import org.springframework.stereotype.Component;

// import java.io.IOException;
// import java.util.Arrays;
// import java.util.Collections;
// import java.util.List;

// @Component
// public class UserContextFilter implements Filter {

//     @Override
//     public void doFilter(
//             ServletRequest servletRequest,
//             ServletResponse servletResponse,
//             FilterChain filterChain) throws IOException, ServletException {

//         HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;

//         String email = httpRequest.getHeader("X-User-Email");
//         String userKeycloakId = httpRequest.getHeader("X-User-KeycloakId");
//         String authoritiesRaw = httpRequest.getHeader("X-User-Authorities");

//         List<SimpleGrantedAuthority> authorities = Collections.emptyList();

//         if (authoritiesRaw != null && !authoritiesRaw.isBlank()) {
//             authorities = Arrays.stream(authoritiesRaw.split(","))
//                     .filter(auth -> !auth.isBlank())
//                     .map(SimpleGrantedAuthority::new)
//                     .toList();
//         }

//         if (hasText(email) || hasText(userKeycloakId) || !authorities.isEmpty()) {
//             var auth = new UsernamePasswordAuthenticationToken(email, null, authorities);
//             SecurityContextHolder.getContext().setAuthentication(auth);
//         } else
//             SecurityContextHolder.clearContext();

//         if (hasText(email) || hasText(userKeycloakId)) {
//             UserContext context = UserContext
//                     .builder()
//                     .email(email)
//                     .keycloakId(userKeycloakId)
//                     .build();

//             UserContext.set(context);
//         } else
//             UserContext.clear();

//         try {
//             filterChain.doFilter(servletRequest, servletResponse);
//         } finally {
//             UserContext.clear();
//             SecurityContextHolder.clearContext();
//         }

//     }

//     private boolean hasText(String value) {
//         return value != null && !value.isBlank();
//     }
// }
