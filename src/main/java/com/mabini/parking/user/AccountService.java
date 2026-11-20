package com.mabini.parking.user;

import com.mabini.parking.common.PasswordGenerator;
import com.mabini.parking.common.enums.ReservationStatus;
import com.mabini.parking.common.enums.Role;
import com.mabini.parking.common.exception.ApiException;
import com.mabini.parking.reservation.Reservation;
import com.mabini.parking.reservation.ReservationRepository;
import com.mabini.parking.user.dto.CreateStaffRequest;
import com.mabini.parking.user.dto.TemporaryPasswordResponse;
import com.mabini.parking.user.dto.UserResponse;
import com.mabini.parking.user.dto.UserSummary;
import com.mabini.parking.vehicle.Vehicle;
import com.mabini.parking.vehicle.VehicleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Account administration.
 *
 * Who may touch whom is decided by the TARGET's role, passed in by the controller:
 *   - /api/admin/staff/**    (super admin only) works on PARKING_MANAGER accounts
 *   - /api/manager/users/**  (manager or super admin) works on USER accounts
 * A super admin account can never be changed through these methods. If the target has a
 * different role than expected, the answer is "not found", so nothing is revealed.
 */
@Service
public class AccountService {

    private static final int TEMP_PASSWORD_LENGTH = 12;

    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final ReservationRepository reservations;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AccountService(UserRepository users, VehicleRepository vehicles, ReservationRepository reservations,
                          PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.vehicles = vehicles;
        this.reservations = reservations;
        this.encoder = encoder;
        this.clock = clock;
    }

    /* ------------------------- staff accounts (super admin) ------------------------- */

    @Transactional
    public TemporaryPasswordResponse createManager(CreateStaffRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsername(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "That username is already taken.");
        }

        String temporary = PasswordGenerator.random(TEMP_PASSWORD_LENGTH);
        User manager = new User();
        manager.setUsername(username);
        manager.setFullName(request.fullName().trim());
        manager.setPasswordHash(encoder.encode(temporary));
        manager.setRole(Role.PARKING_MANAGER); // fixed. The request has no role field.
        manager.setActive(true);
        manager.setMustChangePassword(true);
        manager.setCreatedAt(LocalDateTime.now(clock));

        try {
            return new TemporaryPasswordResponse(UserResponse.from(users.saveAndFlush(manager)), temporary);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict("USERNAME_TAKEN", "That username is already taken.");
        }
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listStaff() {
        return users.findByRoleInOrderByCreatedAtDesc(List.of(Role.PARKING_MANAGER, Role.SUPER_ADMIN))
                .stream().map(UserResponse::from).toList();
    }

    /* ------------------------- user list (staff) ------------------------- */

    @Transactional(readOnly = true)
    public List<UserSummary> listUsers(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return users.findByRoleOrderByCreatedAtDesc(Role.USER).stream()
                .map(this::summary)
                .filter(s -> q.isEmpty()
                        || s.user().username().toLowerCase(Locale.ROOT).contains(q)
                        || s.user().fullName().toLowerCase(Locale.ROOT).contains(q)
                        || (s.plate() != null && s.plate().toLowerCase(Locale.ROOT).contains(q)))
                .toList();
    }

    private UserSummary summary(User user) {
        Optional<Vehicle> vehicle = vehicles.findByOwnerId(user.getId());
        return new UserSummary(
                UserResponse.from(user),
                vehicle.map(Vehicle::getIdentifier).orElse(null),
                vehicle.map(Vehicle::getVehicleType).orElse(null),
                vehicle.map(Vehicle::getStatus).orElse(null));
    }

    /* ------------------------- shared actions ------------------------- */

    /** New temporary password; the person must change it at next login. Also clears a login lockout. */
    @Transactional
    public TemporaryPasswordResponse resetPassword(Long targetId, Role expectedRole) {
        User target = find(targetId, expectedRole);
        String temporary = PasswordGenerator.random(TEMP_PASSWORD_LENGTH);
        target.setPasswordHash(encoder.encode(temporary));
        target.setMustChangePassword(true);
        target.setFailedLoginAttempts(0);
        target.setLockedUntil(null);
        users.save(target);
        return new TemporaryPasswordResponse(UserResponse.from(target), temporary);
    }

    /**
     * Turning an account off blocks login at once (the token filter re-checks the database)
     * and cancels the person's active reservations so the spaces are released.
     */
    @Transactional
    public UserResponse setActive(Long targetId, Role expectedRole, boolean active) {
        User target = find(targetId, expectedRole);
        target.setActive(active);
        users.save(target);

        if (!active) {
            LocalDateTime now = LocalDateTime.now(clock);
            for (Reservation r : reservations.findByUserIdAndStatusIn(target.getId(), ReservationStatus.OPEN)) {
                r.setStatus(ReservationStatus.CANCELLED);
                r.setCancelledAt(now);
                reservations.save(r);
            }
        }
        return UserResponse.from(target);
    }

    private User find(Long id, Role expectedRole) {
        if (expectedRole == Role.SUPER_ADMIN) {
            throw new IllegalArgumentException("Super admin accounts cannot be managed here.");
        }
        return users.findById(id)
                .filter(u -> u.getRole() == expectedRole)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Account not found."));
    }
}
