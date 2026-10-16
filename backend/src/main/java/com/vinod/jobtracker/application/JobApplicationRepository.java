package com.vinod.jobtracker.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

    List<JobApplication> findByUserIdOrderByUpdatedAtDesc(Long userId);

    Optional<JobApplication> findByIdAndUserId(Long id, Long userId);

    /** Interviews coming up soon that haven't had a reminder yet (all users). */
    List<JobApplication> findByStatusAndInterviewAtBetweenAndInterviewRemindedAtIsNull(
            ApplicationStatus status, Instant from, Instant to);

    /** Applications stuck in one status for a while, with no nudge sent yet (all users). */
    List<JobApplication> findByStatusAndStatusChangedAtBeforeAndFollowUpRemindedAtIsNull(
            ApplicationStatus status, Instant before);
}
