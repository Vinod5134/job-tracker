package com.vinod.jobtracker.user;

import com.vinod.jobtracker.config.CurrentUser;
import com.vinod.jobtracker.gmail.GmailStatus;
import com.vinod.jobtracker.gmail.GmailSyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class UserController {

    private final UserService userService;
    private final GmailSyncService gmailSyncService;

    public UserController(UserService userService, GmailSyncService gmailSyncService) {
        this.userService = userService;
        this.gmailSyncService = gmailSyncService;
    }

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        Long userId = CurrentUser.id(jwt);
        return UserResponse.from(userService.get(userId), gmailSyncService.status(userId));
    }

    @PutMapping("/skills")
    public UserResponse updateSkills(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SkillsRequest request) {
        Long userId = CurrentUser.id(jwt);
        return UserResponse.from(userService.updateSkills(userId, request.skills()), gmailSyncService.status(userId));
    }

    public record SkillsRequest(@NotNull @Size(max = 40, message = "Up to 40 skills")
                                List<@Size(max = 40, message = "Keep each skill under 40 characters") String> skills) {
    }

    public record UserResponse(Long id, String name, String email, String pictureUrl,
                               List<String> skills, GmailStatus gmail) {

        static UserResponse from(AppUser user, GmailStatus gmail) {
            return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getPictureUrl(),
                    UserService.skillList(user), gmail);
        }
    }
}
