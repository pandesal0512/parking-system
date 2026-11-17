package com.mabini.parking.user;

import com.mabini.parking.auth.AuthUser;
import com.mabini.parking.user.dto.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** The logged-in user's own profile. The id comes from the token, never from the request. */
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AuthUser current) {
        return userService.getProfile(current.id());
    }
}
