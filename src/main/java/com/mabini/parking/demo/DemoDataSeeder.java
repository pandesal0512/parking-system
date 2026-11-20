package com.mabini.parking.demo;

import com.mabini.parking.common.enums.ParkingArea;
import com.mabini.parking.common.enums.PenaltyType;
import com.mabini.parking.common.enums.ReservationStatus;
import com.mabini.parking.common.enums.Role;
import com.mabini.parking.common.enums.UserType;
import com.mabini.parking.common.enums.VehicleStatus;
import com.mabini.parking.common.enums.VehicleType;
import com.mabini.parking.log.AccessLog;
import com.mabini.parking.log.AccessLogRepository;
import com.mabini.parking.penalty.Penalty;
import com.mabini.parking.penalty.PenaltyRepository;
import com.mabini.parking.reservation.Reservation;
import com.mabini.parking.reservation.ReservationRepository;
import com.mabini.parking.user.User;
import com.mabini.parking.user.UserRepository;
import com.mabini.parking.vehicle.Vehicle;
import com.mabini.parking.vehicle.VehicleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * DEMO ONLY: sample data so a presentation does not start with an empty system.
 * Exists only when app.demo.seed-data=true (env DEMO_SEED_DATA=true). It uses ONE well-known
 * password for every sample account, so never turn it on for anything that is not a demo.
 * It does nothing if the sample accounts already exist (safe on a MySQL database that keeps data).
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String DEMO_PASSWORD = "Demo-pass-123";

    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final ReservationRepository reservations;
    private final AccessLogRepository logs;
    private final PenaltyRepository penalties;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public DemoDataSeeder(UserRepository users, VehicleRepository vehicles, ReservationRepository reservations,
                          AccessLogRepository logs, PenaltyRepository penalties, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.vehicles = vehicles;
        this.reservations = reservations;
        this.logs = logs;
        this.penalties = penalties;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByUsername("demo.manager")) {
            log.info("Demo data already exists, skipping.");
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);

        User manager = account("demo.manager", "Demo Parking Manager", Role.PARKING_MANAGER, null, now);
        User juan = account("demo.juan", "Juan Dela Cruz", Role.USER, UserType.STUDENT, now);
        User maria = account("demo.maria", "Maria Santos", Role.USER, UserType.FACULTY, now);
        User pedro = account("demo.pedro", "Pedro Reyes", Role.USER, UserType.STUDENT, now);
        User ana = account("demo.ana", "Ana Lim", Role.USER, UserType.STUDENT, now);
        account("demo.rica", "Rica Garcia", Role.USER, UserType.PERSONNEL, now); // no vehicle yet: shows the "register" screen

        Vehicle juansBike = vehicle(juan, VehicleType.MOTORCYCLE, "MOTO101", "Honda", "Click 125", "Red",
                VehicleStatus.APPROVED, manager, now);
        Vehicle mariasCar = vehicle(maria, VehicleType.CAR, "CAR2001", "Toyota", "Vios", "White",
                VehicleStatus.APPROVED, manager, now);
        Vehicle pedrosTrike = vehicle(pedro, VehicleType.TRICYCLE, "TRI301", "Bajaj", "RE", "Green",
                VehicleStatus.APPROVED, manager, now);
        vehicle(ana, VehicleType.CAR, "CAR2002", "Honda", "City", "Blue", VehicleStatus.PENDING, null, now); // waiting for approval

        // Juan can enter the gate right now (reservation is already running).
        reservation(juan, juansBike, now.minusMinutes(5), now.plusHours(2), ReservationStatus.ACTIVE, now);
        // Later today and tomorrow.
        reservation(pedro, pedrosTrike, now.plusHours(3), now.plusHours(5), ReservationStatus.ACTIVE, now);
        reservation(maria, mariasCar, now.plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0),
                now.plusDays(1).withHour(11).withMinute(0).withSecond(0).withNano(0), ReservationStatus.ACTIVE, now);

        // History: Pedro visited yesterday; Maria missed a reservation and has one warning.
        LocalDateTime yStart = now.minusDays(1).withHour(8).withMinute(0).withSecond(0).withNano(0);
        Reservation pedroYesterday = reservation(pedro, pedrosTrike, yStart, yStart.plusHours(3),
                ReservationStatus.COMPLETED, yStart.minusDays(1));
        AccessLog visit = new AccessLog();
        visit.setVehicle(pedrosTrike);
        visit.setIdentifierRead(pedrosTrike.getIdentifier());
        visit.setVehicleType(VehicleType.TRICYCLE);
        visit.setReservation(pedroYesterday);
        visit.setEntryTime(yStart.plusMinutes(4));
        visit.setExitTime(yStart.plusHours(2).plusMinutes(50));
        visit.setRecordedBy(manager.getId());
        logs.save(visit);

        LocalDateTime mStart = now.minusDays(2).withHour(13).withMinute(0).withSecond(0).withNano(0);
        Reservation missed = reservation(maria, mariasCar, mStart, mStart.plusHours(2),
                ReservationStatus.NO_SHOW, mStart.minusDays(1));
        maria.setWarningCount(1);
        users.save(maria);
        Penalty penalty = new Penalty();
        penalty.setUser(maria);
        penalty.setReservation(missed);
        penalty.setType(PenaltyType.NO_SHOW);
        penalty.setDescription("Did not arrive for the reservation two days ago. Warning 1.");
        penalty.setCreatedAt(mStart.plusHours(2));
        penalties.save(penalty);

        log.warn("""

                ==================================================================
                 DEMO DATA created. Every demo account uses the password: {}
                   demo.manager  (parking manager)
                   demo.juan     (student, motorcycle, reservation running now)
                   demo.maria    (faculty, car in the faculty area, 1 warning)
                   demo.pedro    (student, tricycle)
                   demo.ana      (student, car waiting for approval)
                   demo.rica     (personnel, no vehicle yet)
                 Do not use DEMO_SEED_DATA outside a demo.
                ==================================================================
                """, DEMO_PASSWORD);
    }

    private User account(String username, String fullName, Role role, UserType type, LocalDateTime now) {
        User u = new User();
        u.setUsername(username);
        u.setFullName(fullName);
        u.setPasswordHash(encoder.encode(DEMO_PASSWORD));
        u.setRole(role);
        u.setUserType(type);
        u.setActive(true);
        u.setMustChangePassword(false);
        u.setCreatedAt(now);
        return users.save(u);
    }

    private Vehicle vehicle(User owner, VehicleType type, String plate, String make, String model, String color,
                            VehicleStatus status, User reviewer, LocalDateTime now) {
        Vehicle v = new Vehicle();
        v.setOwner(owner);
        v.setVehicleType(type);
        v.setIdentifier(plate);
        v.setMake(make);
        v.setModel(model);
        v.setColor(color);
        v.setStatus(status);
        v.setCreatedAt(now);
        if (reviewer != null) {
            v.setReviewedBy(reviewer);
            v.setReviewedAt(now);
        }
        return vehicles.save(v);
    }

    private Reservation reservation(User user, Vehicle vehicle, LocalDateTime start, LocalDateTime end,
                                    ReservationStatus status, LocalDateTime createdAt) {
        Reservation r = new Reservation();
        r.setUser(user);
        r.setVehicle(vehicle);
        r.setVehicleType(vehicle.getVehicleType());
        // Same rule as the real system: faculty-type owners of a car use the faculty area.
        boolean faculty = ParkingArea.isFacultyType(user.getUserType()) && vehicle.getVehicleType() == VehicleType.CAR;
        r.setArea(faculty ? ParkingArea.FACULTY : ParkingArea.GENERAL);
        r.setStartTime(start);
        r.setEndTime(end);
        r.setStatus(status);
        r.setCreatedAt(createdAt);
        return reservations.save(r);
    }
}
