package com.mabini.parking.user.dto;

import com.mabini.parking.common.enums.Role;
import com.mabini.parking.common.enums.UserType;
import com.mabini.parking.user.User;

import java.time.LocalDateTime;

/** What the API shows about an account. Deliberately has NO password hash. */
public record UserResponse(
        Long id,
        String username,
        String fullName,
        UserType userType,
        String schoolId,
        Role role,
        boolean active,
        boolean mustChangePassword,
        int warningCount,
        LocalDateTime suspendedUntil,
        boolean registrationBanned,
        LocalDateTime createdAt) {

    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(),
                u.getUsername(),
                u.getFullName(),
                u.getUserType(),
                u.getSchoolId(),
                u.getRole(),
                u.isActive(),
                u.isMustChangePassword(),
                u.getWarningCount(),
                u.getSuspendedUntil(),
                u.isRegistrationBanned(),
                u.getCreatedAt());
    }
}
