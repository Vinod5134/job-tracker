package com.vinod.jobtracker.activity;

import com.vinod.jobtracker.realtime.LiveUpdateService;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityService {

    private final ActivityRepository activities;
    private final LiveUpdateService live;

    public ActivityService(ActivityRepository activities, LiveUpdateService live) {
        this.activities = activities;
        this.live = live;
    }

    @Transactional
    public void log(Long userId, Long applicationId, String message, Activity.Origin origin) {
        Activity saved = activities.save(new Activity(userId, applicationId, message, origin));
        live.send(userId, LiveUpdateService.ACTIVITY, ActivityResponse.from(saved));
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> recent(Long userId) {
        return activities.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream().map(ActivityResponse::from).toList();
    }

    public record ActivityResponse(Long id, Long applicationId, String message, Activity.Origin origin, Instant createdAt) {

        static ActivityResponse from(Activity a) {
            return new ActivityResponse(a.getId(), a.getApplicationId(), a.getMessage(), a.getOrigin(), a.getCreatedAt());
        }
    }
}
