// package com.nexatech.user.utils;

// import com.nexatech.user.exception.AppException;
// import com.nexatech.user.exception.ErrorCode;
// import com.nexatech.user.filter.UserContext;
// import com.nexatech.user.model.User;
// import com.nexatech.user.repository.UserRepository;

// public class UserUtils {

//     UserRepository userRepository;

//     public User getCurrentUser() {
//         String id = UserContext.get().getKeycloakId();
//         return userRepository.findByIdAndActiveTrue(id)
//                 .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
//     }
// }
