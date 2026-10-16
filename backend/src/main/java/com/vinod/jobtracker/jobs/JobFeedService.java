package com.vinod.jobtracker.jobs;

import com.vinod.jobtracker.application.JobApplication;
import com.vinod.jobtracker.application.JobApplicationRepository;
import com.vinod.jobtracker.user.UserService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The "Find jobs" feed: jobs from the user's LinkedIn, Naukri and Indeed alert emails,
 * scored against the user's skills.
 */
@Service
public class JobFeedService {

    private final UserService userService;
    private final JobApplicationRepository applications;

    public JobFeedService(UserService userService, JobApplicationRepository applications) {
        this.userService = userService;
        this.applications = applications;
    }

    public record JobResult(String id, String source, String title, String company, String location, String url,
                            String receivedAt, int matchScore, List<String> matchedSkills, boolean fresherFriendly,
                            boolean saved) {
    }

    /** Best skill match first, then newest. */
    @Transactional(readOnly = true)
    public List<JobResult> feed(Long userId, List<AlertJob> alerts) {
        List<String> skills = UserService.skillList(userService.get(userId));
        Set<String> savedUrls = applications.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(JobApplication::getJobUrl).filter(Objects::nonNull).collect(Collectors.toSet());
        List<JobResult> results = new ArrayList<>();
        for (AlertJob job : alerts) {
            String text = job.getTitle() + " " + Objects.requireNonNullElse(job.getCompany(), "");
            SkillMatcher.Match match = SkillMatcher.match(skills, text);
            results.add(new JobResult(String.valueOf(job.getId()), job.getSource().name(), job.getTitle(),
                    Objects.requireNonNullElse(job.getCompany(), ""), Objects.requireNonNullElse(job.getLocation(), ""),
                    job.getUrl(), job.getReceivedAt().toString(), match.score(), match.matched(),
                    SkillMatcher.fresherFriendly(job.getTitle()), savedUrls.contains(job.getUrl())));
        }
        results.sort(Comparator.comparingInt(JobResult::matchScore).reversed()
                .thenComparing(JobResult::receivedAt, Comparator.reverseOrder()));
        return results;
    }
}
