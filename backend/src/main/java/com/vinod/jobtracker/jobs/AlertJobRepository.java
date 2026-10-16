package com.vinod.jobtracker.jobs;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface AlertJobRepository extends JpaRepository<AlertJob, Long> {

    boolean existsByUserIdAndUrlHash(Long userId, String urlHash);

    List<AlertJob> findTop150ByUserIdOrderByReceivedAtDesc(Long userId);

    @Transactional
    long deleteByUserIdAndReceivedAtBefore(Long userId, Instant before);

    @Transactional
    long deleteByUserId(Long userId);
}
