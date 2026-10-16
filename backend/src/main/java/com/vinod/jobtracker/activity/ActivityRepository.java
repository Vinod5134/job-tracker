package com.vinod.jobtracker.activity;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    List<Activity> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);
}
