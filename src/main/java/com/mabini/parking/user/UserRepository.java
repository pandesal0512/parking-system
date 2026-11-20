package com.mabini.parking.user;

import com.mabini.parking.common.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByRole(Role role);

    List<User> findByRoleOrderByCreatedAtDesc(Role role);

    List<User> findByRoleInOrderByCreatedAtDesc(java.util.Collection<Role> roles);
}
