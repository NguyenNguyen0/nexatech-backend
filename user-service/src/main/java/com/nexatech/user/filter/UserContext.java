package com.nexatech.user.filter;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserContext {

    String email;

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    /**
     * Workflow:
     * - {@link UserContextFilter} builds a {@link UserContext} from request headers at the start of a request.
     * - It stores the context in a ThreadLocal for downstream services (same request thread).
     * - It always clears the ThreadLocal in a finally-block to prevent leakage across requests.
     */
    public static void set(UserContext context) {
        HOLDER.set(context);
    }

    public static UserContext get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    // Naming aliases (keep old API to avoid breaking other callers)
    public static void setCurrent(UserContext context) {
        set(context);
    }

    public static UserContext current() {
        return get();
    }

    public static void clearCurrent() {
        clear();
    }
}

