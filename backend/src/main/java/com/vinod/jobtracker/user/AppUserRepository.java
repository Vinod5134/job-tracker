package com.vinod.jobtracker.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByGoogleId(String googleId);

    Optional<AppUser> findByEmailIgnoreCase(String email);
}
