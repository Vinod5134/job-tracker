package com.vinod.jobtracker.gmail;

import com.vinod.jobtracker.application.ApplicationStatus;
import com.vinod.jobtracker.config.CurrentUser;
import com.vinod.jobtracker.gmail.GmailSyncService.EmailRecordResponse;
import com.vinod.jobtracker.gmail.GmailSyncService.SyncResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.EnumSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Connecting Gmail itself is a browser redirect to /oauth2/authorization/google-gmail.
 * These endpoints cover everything after that.
 */
@RestController
@RequestMapping("/api/gmail")
public class GmailController {

    private final GmailSyncService service;

    public GmailController(GmailSyncService service) {
        this.service = service;
    }

    @GetMapping("/status")
    public GmailStatus status(@AuthenticationPrincipal Jwt jwt) {
        return service.status(CurrentUser.id(jwt));
    }

    @PostMapping("/sync")
    public SyncResult sync(@AuthenticationPrincipal Jwt jwt) {
        return service.sync(CurrentUser.id(jwt));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect(@AuthenticationPrincipal Jwt jwt) {
        service.disconnect(CurrentUser.id(jwt));
    }

    /** view=review (default) for emails waiting on the user, view=all for the full log. */
    @GetMapping("/emails")
    public List<EmailRecordResponse> emails(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(defaultValue = "review") String view) {
        EnumSet<EmailRecord.State> states = "all".equals(view)
                ? EnumSet.allOf(EmailRecord.State.class)
                : EnumSet.of(EmailRecord.State.NEEDS_REVIEW);
        return service.list(CurrentUser.id(jwt), states);
    }

    @PostMapping("/emails/{id}/accept")
    public EmailRecordResponse accept(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                      @Valid @RequestBody AcceptRequest request) {
        return service.accept(CurrentUser.id(jwt), id, request.company(), request.status(), request.role());
    }

    @PostMapping("/emails/{id}/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.dismiss(CurrentUser.id(jwt), id);
    }

    public record AcceptRequest(@NotBlank(message = "Enter the company name") @Size(max = 150) String company,
                                @NotNull(message = "Choose a status") ApplicationStatus status,
                                @Size(max = 150) String role) {
    }
}
