package com.mabini.parking.penalty.dto;

/** What one run of the no-show / overstay check found. */
public record CheckResult(int noShows, int overstays) {
}
