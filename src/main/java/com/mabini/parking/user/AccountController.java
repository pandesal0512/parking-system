package com.mabini.parking.user;

import com.mabini.parking.common.Roles;
import com.mabini.parking.common.enums.Role;
import com.mabini.parking.user.dto.CreateStaffRequest;
import com.mabini.parking.user.dto.TemporaryPasswordResponse;
import com.mabini.parking.user.dto.UserResponse;
import com.mabini.parking.user.dto.UserSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AccountController {

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    /* ----- parking managers: super admin only ----- */

    @PostMapping("/admin/staff")
    @PreAuthorize(Roles.SUPER_ADMIN_ONLY)
    @ResponseStatus(HttpStatus.CREATED)
    public TemporaryPasswordResponse createManager(@Valid @RequestBody CreateStaffRequest request) {
        return accounts.createManager(request);
    }

    @GetMapping("/admin/staff")
    @PreAuthorize(Roles.SUPER_ADMIN_ONLY)
    public List<UserResponse> listStaff() {
        return accounts.listStaff();
    }

    @PostMapping("/admin/staff/{id}/reset-password")
    @PreAuthorize(Roles.SUPER_ADMIN_ONLY)
    public TemporaryPasswordResponse resetManagerPassword(@PathVariable Long id) {
        return accounts.resetPassword(id, Role.PARKING_MANAGER);
    }

    @PostMapping("/admin/staff/{id}/deactivate")
    @PreAuthorize(Roles.SUPER_ADMIN_ONLY)
    public UserResponse deactivateManager(@PathVariable Long id) {
        return accounts.setActive(id, Role.PARKING_MANAGER, false);
    }

    @PostMapping("/admin/staff/{id}/activate")
    @PreAuthorize(Roles.SUPER_ADMIN_ONLY)
    public UserResponse activateManager(@PathVariable Long id) {
        return accounts.setActive(id, Role.PARKING_MANAGER, true);
    }

    /* ----- regular users: managers can look, only the super admin can change ----- */

    @GetMapping("/manager/users")
    @PreAuthorize(Roles.STAFF)
    public List<UserSummary> listUsers(@RequestParam(required = false) String q) {
        return accounts.listUsers(q);
    }

    @PostMapping("/manager/users/{id}/reset-password")
    @PreAuthorize(Roles.SUPER_ADMIN_ONLY)
    public TemporaryPasswordResponse resetUserPassword(@PathVariable Long id) {
        return accounts.resetPassword(id, Role.USER);
    }

    @PostMapping("/manager/users/{id}/deactivate")
    @PreAuthorize(Roles.SUPER_ADMIN_ONLY)
    public UserResponse deactivateUser(@PathVariable Long id) {
        return accounts.setActive(id, Role.USER, false);
    }

    @PostMapping("/manager/users/{id}/activate")
    @PreAuthorize(Roles.SUPER_ADMIN_ONLY)
    public UserResponse activateUser(@PathVariable Long id) {
        return accounts.setActive(id, Role.USER, true);
    }
}
