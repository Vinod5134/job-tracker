package com.vinod.jobtracker.gmail;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailRecordRepository extends JpaRepository<EmailRecord, Long> {

    boolean existsByUserIdAndGmailMessageId(Long userId, String gmailMessageId);

    List<EmailRecord> findTop50ByUserIdAndStateInOrderByReceivedAtDesc(Long userId, Collection<EmailRecord.State> states);

    Optional<EmailRecord> findByIdAndUserId(Long id, Long userId);

    long countByUserIdAndState(Long userId, EmailRecord.State state);

    void deleteByUserId(Long userId);
}
