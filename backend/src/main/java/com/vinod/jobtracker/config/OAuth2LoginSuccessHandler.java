package com.vinod.jobtracker.config;

import com.vinod.jobtracker.gmail.GmailSyncService;
import com.vinod.jobtracker.user.AppUser;
import com.vinod.jobtracker.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Runs after Google redirects back to the backend.
 * - Normal sign-in: create/update the user, issue our JWT, redirect to the frontend with it.
 * - Connect Gmail: store the (encrypted) refresh token so the backend can read job emails.
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserService userService;
    private final JwtService jwtService;
    private final GmailSyncService gmailSyncService;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final AppProperties props;

    public OAuth2LoginSuccessHandler(UserService userService, JwtService jwtService,
                                     GmailSyncService gmailSyncService,
                                     OAuth2AuthorizedClientService authorizedClientService,
                                     AppProperties props) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.gmailSyncService = gmailSyncService;
        this.authorizedClientService = authorizedClientService;
        this.props = props;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        OAuth2User principal = token.getPrincipal();
        String registrationId = token.getAuthorizedClientRegistrationId();

        AppUser user = userService.upsertGoogleUser(
                principal.getAttribute("sub"),
                principal.getAttribute("email"),
                principal.getAttribute("name"),
                principal.getAttribute("picture"));

        String target;
        if (SecurityConfig.GMAIL_REGISTRATION.equals(registrationId)) {
            OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(registrationId, token.getName());
            if (client != null && client.getRefreshToken() != null) {
                gmailSyncService.connect(user, client.getRefreshToken().getTokenValue());
                target = props.frontendUrl() + "/#/settings?gmail=connected";
            } else {
                target = props.frontendUrl() + "/#/settings?gmail=error";
            }
            // We keep our own encrypted copy, so don't hold tokens in memory
            authorizedClientService.removeAuthorizedClient(registrationId, token.getName());
        } else {
            String jwt = jwtService.issue(user);
            // The token goes in the URL fragment (#), which browsers never send to servers
            target = props.frontendUrl() + "/#/auth?token=" + URLEncoder.encode(jwt, StandardCharsets.UTF_8);
        }

        // The API is stateless: drop the temporary login session
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(target);
    }
}
