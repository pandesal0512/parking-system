package com.mabini.parking.penalty;

import com.mabini.parking.auth.AuthUser;
import com.mabini.parking.common.Roles;
import com.mabini.parking.penalty.dto.PenaltyResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PenaltyController {

    private final PenaltyService penaltyService;

    public PenaltyController(PenaltyService penaltyService) {
        this.penaltyService = penaltyService;
    }

    /** A user's own warnings and penalties. The id comes from the token. */
    @GetMapping("/penalties/me")
    @PreAuthorize(Roles.USER_ONLY)
    public List<PenaltyResponse> mine(@AuthenticationPrincipal AuthUser current) {
        return penaltyService.listMine(current.id());
    }

    @GetMapping("/manager/penalties")
    @PreAuthorize(Roles.STAFF)
    public List<PenaltyResponse> all(@RequestParam(defaultValue = "100") int limit) {
        return penaltyService.listAll(limit);
    }

    @PostMapping("/manager/users/{id}/clear-penalties")
    @PreAuthorize(Roles.SUPER_ADMIN_ONLY)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable Long id) {
        penaltyService.clearPenalties(id);
    }
}
