package com.vinod.jobtracker.gmail;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GmailConnectionRepository extends JpaRepository<GmailConnection, Long> {

    Optional<GmailConnection> findByUserId(Long userId);

    List<GmailConnection> findByActiveTrue();
}
