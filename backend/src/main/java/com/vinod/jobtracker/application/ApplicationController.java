package com.vinod.jobtracker.application;

import com.vinod.jobtracker.application.ApplicationDtos.ApplicationRequest;
import com.vinod.jobtracker.application.ApplicationDtos.ApplicationResponse;
import com.vinod.jobtracker.application.ApplicationDtos.StatusRequest;
import com.vinod.jobtracker.config.CurrentUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ApplicationResponse> list(@AuthenticationPrincipal Jwt jwt,
                                          @RequestParam(required = false) ApplicationStatus status,
                                          @RequestParam(required = false) String q) {
        return service.list(CurrentUser.id(jwt), status, q);
    }

    @GetMapping("/{id}")
    public ApplicationResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return service.get(CurrentUser.id(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApplicationResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ApplicationRequest request) {
        return service.create(CurrentUser.id(jwt), request);
    }

    @PutMapping("/{id}")
    public ApplicationResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                      @Valid @RequestBody ApplicationRequest request) {
        return service.update(CurrentUser.id(jwt), id, request);
    }

    @PatchMapping("/{id}/status")
    public ApplicationResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                            @Valid @RequestBody StatusRequest request) {
        return service.changeStatus(CurrentUser.id(jwt), id, request.status());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        service.delete(CurrentUser.id(jwt), id);
    }
}
