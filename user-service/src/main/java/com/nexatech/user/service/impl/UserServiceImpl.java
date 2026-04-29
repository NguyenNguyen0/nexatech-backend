package com.nexatech.user.service.impl;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.security.SecureRandom;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nexatech.user.dto.event.EmailVerificationEvent;
import com.nexatech.user.dto.base.ApiResponse;
import com.nexatech.user.dto.base.PageResponse;
import com.nexatech.user.dto.request.IdentityUserUpdateRequest;
import com.nexatech.user.dto.request.UserCreationRequest;
import com.nexatech.user.dto.request.UserUpdatenRequest;
import com.nexatech.user.dto.response.OnboardingStatusResponse;
import com.nexatech.user.dto.response.UserResponse;
import com.nexatech.user.exception.AppException;
import com.nexatech.user.exception.ErrorCode;
import com.nexatech.user.mapper.UserMapper;
import com.nexatech.user.model.User;
import com.nexatech.user.repository.UserRepository;
import com.nexatech.user.service.IdentityResilienceService;
import com.nexatech.user.service.UserService;

import feign.FeignException;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserServiceImpl implements UserService {

    UserRepository userRepository;
    UserMapper userMapper;
    IdentityResilienceService identityResilienceService;
    KafkaTemplate<String, Object> kafkaTemplate;

    @NonFinal
    @Value("${spring.kafka.topics.user-events:user.events}")
    String userEventsTopic;

    private static final SecureRandom OTP_RANDOM = new SecureRandom();
    private static final long OTP_TTL_SECONDS = 300L;
    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    @Override
    public UserResponse createUser(UserCreationRequest request) {
        if (request == null || request.getEmail() == null || request.getEmail().isBlank()) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.USER_EMAIL_EXISTS);
        }

        User user = userMapper.toUser(request);
        if (request.getId() != null && !request.getId().isBlank()) {
            user.setId(request.getId());
        } else {
            user.setId(UUID.randomUUID().toString());
        }
        if (user.getOnBoarded() == null) {
            user.setOnBoarded(false);
        }

        // Kafka / internal API: no JWT — attribute created/updated "by" to the new user's Keycloak id (self).
        if (!hasJwtAuthentication()) {
            String selfId = user.getId();
            if (selfId != null && !selfId.isBlank()) {
                user.setCreatedBy(selfId);
                user.setUpdatedBy(selfId);
            }
        }

        return enrichAuditUsernames(userMapper.toResponse(userRepository.save(user)));
    }

    @Override
    public PageResponse<UserResponse> getAllUsers(
            int page,
            int size,
            String email,
            String phone,
            Boolean active,
            String role) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");
        Specification<User> specification = buildUserFilterSpecification(email, phone, active);
        String normalizedRole = normalizeRoleFilter(role);

        if (normalizedRole != null) {
            List<UserResponse> allMatched = userRepository.findAll(specification, sort).stream()
                    .map(this::mapToUserResponseWithRole)
                    .filter(u -> normalizedRole.equalsIgnoreCase(u.getRole()))
                    .collect(Collectors.toList());
            int fromIndex = Math.min((safePage - 1) * safeSize, allMatched.size());
            int toIndex = Math.min(fromIndex + safeSize, allMatched.size());
            List<UserResponse> content = allMatched.subList(fromIndex, toIndex);
            int totalPages = allMatched.isEmpty() ? 0 : (int) Math.ceil((double) allMatched.size() / safeSize);
            boolean hasMore = safePage < totalPages;
            return PageResponse.<UserResponse>builder()
                    .content(content)
                    .page(safePage)
                    .size(safeSize)
                    .totalElements(allMatched.size())
                    .totalPages(totalPages)
                    .hasMore(hasMore)
                    .build();
        }

        Pageable pageable = PageRequest.of(safePage - 1, safeSize, sort);
        var pageUsers = userRepository.findAll(specification, pageable).map(this::mapToUserResponseWithRole);
        return new PageResponse<>(pageUsers);
    }

    @Override
    public UserResponse getUserById(String id) {
        User user = userRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return enrichAuditUsernames(userMapper.toResponse(user));
    }

    @Transactional
    @Override
    public UserResponse updateUser(String id, UserUpdatenRequest dto) {
        User user = userRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        boolean syncKeycloak = dto != null && (isChanged(user.getFirstName(), dto.getFirstName())
                || isChanged(user.getLastName(), dto.getLastName())
                || isChanged(user.getEmail(), dto.getEmail()));

        if (dto != null && isChanged(user.getEmail(), dto.getEmail())) {
            assertEmailAllowedForUpdate(id, user, dto);
        }

        if (dto != null) {
            userMapper.updateUser(user, dto);
        }

        User updatedUser = userRepository.save(user);
        if (syncKeycloak) {
            syncUserToIdentity(updatedUser, true);
        }

        return enrichAuditUsernames(userMapper.toResponse(updatedUser));
    }

    /**
     * Postgres + Keycloak must agree before we persist a new email (avoids DB/IdP mismatch).
     */
    private void assertEmailAllowedForUpdate(String id, User user, UserUpdatenRequest dto) {
        String newEmail = dto.getEmail() != null ? dto.getEmail().trim() : "";
        if (!isPlausibleEmail(newEmail)) {
            throw new AppException(ErrorCode.UNCATEGORIZED_EXCEPTION);
        }
        if (userRepository.existsByEmailAndIdNot(newEmail, id)) {
            throw new AppException(ErrorCode.USER_EMAIL_EXISTS);
        }
        ApiResponse<Boolean> kc;
        try {
            kc = identityResilienceService.isKeycloakEmailAvailable(newEmail, id);
        } catch (FeignException e) {
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        } catch (AppException e) {
            throw e;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        }
        if (kc == null || Boolean.FALSE.equals(kc.getResult())) {
            throw new AppException(ErrorCode.USER_EMAIL_EXISTS);
        }
    }

    @Transactional
    @Override
    public void deleteUser(String id) {
        disableUser(id);
    }

    @Transactional
    @Override
    public void disableUser(String id) {
        User user = userRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        user.setActive(false);
        User deletedUser = userRepository.save(user);
        syncUserToIdentity(deletedUser, false);
    }

    @Transactional
    @Override
    public void enableUser(String id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        user.setActive(true);
        User enabledUser = userRepository.save(user);
        syncUserToIdentity(enabledUser, true);
    }

    @Override
    public OnboardingStatusResponse getOnboardingStatus(String userId) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return OnboardingStatusResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .onBoarded(Boolean.TRUE.equals(user.getOnBoarded()))
                .build();
    }

    @Override
    public void requestEmailVerificationOtp(String userId) {
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (Boolean.TRUE.equals(user.getOnBoarded())) {
            return;
        }

        String otp = generateOtp();
        Instant expiresAt = Instant.now().plusSeconds(OTP_TTL_SECONDS);
        otpStore.put(userId, new OtpEntry(otp, expiresAt));

        publishEmailOtpRequiredEvent(user, otp);
    }

    @Override
    @Transactional
    public void verifyEmailVerificationOtp(String userId, String otp) {
        if (otp == null || otp.isBlank()) {
            throw new AppException(ErrorCode.OTP_REQUIRED);
        }
        User user = userRepository.findByIdAndActiveTrue(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        OtpEntry entry = otpStore.get(userId);
        if (entry == null) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            otpStore.remove(userId);
            throw new AppException(ErrorCode.OTP_EXPIRED);
        }
        if (!entry.code().equals(otp.trim())) {
            throw new AppException(ErrorCode.OTP_INVALID);
        }

        otpStore.remove(userId);
        user.setOnBoarded(true);
        userRepository.save(user);
        // Keep DB state as source of truth, then best-effort sync Keycloak emailVerified flag.
        try {
            syncUserToIdentity(user, true);
        } catch (Exception ex) {
            log.warn("OTP verified in DB but failed to sync Keycloak emailVerified for userId={}", userId, ex);
        }
        publishEmailVerifiedEvent(user);
    }

    // private method: check if the value is changed
    private boolean isChanged(String currentValue, String newValue) {
        return newValue != null && !newValue.isBlank() && !Objects.equals(currentValue, newValue);
    }

    // private method: UPDATE OR DELETE
    private void syncUserToIdentity(User user, boolean enabled) {
        if (!isPlausibleEmail(user.getEmail())) {
            log.warn(
                    "Skipping Keycloak sync for user id={}: email is missing or not a valid address format.",
                    user.getId());
            return;
        }
        try {
            identityResilienceService.updateIdentityUser(user.getId(), IdentityUserUpdateRequest.builder()
                    .username(user.getUsername())
                    .firstName(user.getFirstName())
                    .lastName(user.getLastName())
                    .email(user.getEmail())
                    .enabled(enabled)
                    .emailVerified(true)
                    .build());
        } catch (FeignException e) {
            if (e.status() == 404) {
                log.warn(
                        "Identity/Keycloak has no user for id={}; DB change kept (user may predate Keycloak sync).",
                        user.getId());
                return;
            }
            if (e.status() == 409) {
                throw new AppException(ErrorCode.USER_EMAIL_EXISTS);
            }
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.KEYCLOAK_ERROR);
        }
    }

    private static boolean hasJwtAuthentication() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && auth instanceof JwtAuthenticationToken;
    }

    private String resolvePrimaryRole(String userId) {
        try {
            ApiResponse<Map<String, Object>> roleMappings = identityResilienceService.getUserRoleMappings(userId);
            Map<String, Object> payload = roleMappings != null ? roleMappings.getResult() : null;
            if (payload == null) {
                return null;
            }
            Object realmMappingsObj = payload.get("realmMappings");
            if (!(realmMappingsObj instanceof List<?> realmMappings) || realmMappings.isEmpty()) {
                return null;
            }
            for (Object roleItem : realmMappings) {
                if (!(roleItem instanceof Map<?, ?> roleMap)) {
                    continue;
                }
                Object roleNameObj = roleMap.get("name");
                if (roleNameObj == null) {
                    continue;
                }
                String roleName = roleNameObj.toString().trim();
                if (!roleName.isEmpty()) {
                    return roleName.toUpperCase(Locale.ROOT);
                }
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private UserResponse mapToUserResponseWithRole(User user) {
        UserResponse response = userMapper.toResponse(user);
        response.setRole(resolvePrimaryRole(user.getId()));
        return enrichAuditUsernames(response);
    }

    private UserResponse enrichAuditUsernames(UserResponse response) {
        if (response == null) {
            return null;
        }
        response.setCreatedBy(resolveUsernameByUserId(response.getCreatedBy()));
        response.setUpdatedBy(resolveUsernameByUserId(response.getUpdatedBy()));
        return response;
    }

    private String resolveUsernameByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return userId;
        }
        return userRepository.findById(userId)
                .map(User::getUsername)
                .filter(username -> username != null && !username.isBlank())
                .orElse(userId);
    }

    private static Specification<User> buildUserFilterSpecification(String email, String phone, Boolean active) {
        Specification<User> specification = (root, query, cb) -> cb.conjunction();
        if (email != null && !email.isBlank()) {
            String normalized = "%" + email.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, cb) -> cb.like(cb.lower(root.get("email")), normalized));
        }
        if (phone != null && !phone.isBlank()) {
            String normalized = "%" + phone.trim().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, cb) -> cb.like(cb.lower(root.get("phone")), normalized));
        }
        if (active != null) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        return specification;
    }

    private static String normalizeRoleFilter(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        return role.trim().toUpperCase(Locale.ROOT);
    }

    private static String generateOtp() {
        int value = OTP_RANDOM.nextInt(1_000_000);
        return String.format("%06d", value);
    }

    private void publishEmailOtpRequiredEvent(User user, String otp) {
        EmailVerificationEvent event = EmailVerificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("EMAIL_OTP_REQUIRED")
                .timestamp(Instant.now())
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(buildDisplayName(user))
                .otpCode(otp)
                .expiresInSeconds(OTP_TTL_SECONDS)
                .build();
        kafkaTemplate.send(userEventsTopic, event);
    }

    private void publishEmailVerifiedEvent(User user) {
        EmailVerificationEvent event = EmailVerificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("EMAIL_VERIFIED")
                .timestamp(Instant.now())
                .userId(user.getId())
                .email(user.getEmail())
                .displayName(buildDisplayName(user))
                .build();
        kafkaTemplate.send(userEventsTopic, event);
    }

    private static String buildDisplayName(User user) {
        String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String last = user.getLastName() != null ? user.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? user.getUsername() : full;
    }

    private record OtpEntry(String code, Instant expiresAt) {
    }

    /** Rejects values that are not email-shaped (e.g. UUID accidentally stored as email). */
    private static boolean isPlausibleEmail(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        int at = email.indexOf('@');
        return at > 0 && at < email.length() - 1;
    }

}
