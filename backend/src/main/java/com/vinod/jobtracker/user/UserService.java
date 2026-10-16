package com.vinod.jobtracker.user;

import com.vinod.jobtracker.common.NotFoundException;
import com.vinod.jobtracker.config.AppProperties;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final AppUserRepository users;
    private final AppProperties props;

    public UserService(AppUserRepository users, AppProperties props) {
        this.users = users;
        this.props = props;
    }

    /**
     * Find the user by Google id (or email), creating them on first sign-in.
     */
    @Transactional
    public AppUser upsertGoogleUser(String googleId, String email, String name, String pictureUrl) {
        AppUser user = users.findByGoogleId(googleId)
                .or(() -> users.findByEmailIgnoreCase(email))
                .orElseGet(() -> {
                    AppUser created = new AppUser();
                    created.setSkills(props.defaultSkills());
                    return created;
                });
        user.setGoogleId(googleId);
        user.setEmail(email);
        user.setName(name);
        user.setPictureUrl(pictureUrl);
        return users.save(user);
    }

    @Transactional(readOnly = true)
    public AppUser get(Long userId) {
        return users.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    @Transactional
    public AppUser updateSkills(Long userId, List<String> skills) {
        AppUser user = get(userId);
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        for (String skill : skills) {
            String trimmed = skill == null ? "" : skill.trim();
            if (!trimmed.isEmpty()) {
                cleaned.add(trimmed);
            }
        }
        user.setSkills(String.join(", ", cleaned));
        return users.save(user);
    }

    public static List<String> skillList(AppUser user) {
        if (user.getSkills() == null || user.getSkills().isBlank()) {
            return List.of();
        }
        return Arrays.stream(user.getSkills().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
