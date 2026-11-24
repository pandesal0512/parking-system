package com.mabini.parking.dashboard;

import com.mabini.parking.dashboard.dto.DashboardResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SpacesController {

    private final DashboardService dashboardService;

    public SpacesController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** Any logged-in person may see how full each area is (counts only). */
    @GetMapping("/spaces")
    public List<DashboardResponse.TypeStatus> spaces() {
        return dashboardService.spaces();
    }
}
