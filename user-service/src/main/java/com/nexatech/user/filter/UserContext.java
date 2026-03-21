// package com.nexatech.user.filter;

// import lombok.AccessLevel;
// import lombok.Builder;
// import lombok.Getter;
// import lombok.Setter;
// import lombok.experimental.FieldDefaults;

// @Getter
// @Setter
// @Builder
// @FieldDefaults(level = AccessLevel.PRIVATE)
// public class UserContext {

//     String email;
//     String keycloakId;

//     static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

//     public static void set(UserContext context) {
//         HOLDER.set(context);
//     }

//     public static UserContext get() {
//         return HOLDER.get();
//     }

//     public static void clear() {
//         HOLDER.remove();
//     }

// }