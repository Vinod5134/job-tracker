package com.vinod.jobtracker.config;

import com.vinod.jobtracker.user.AppUser;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues the app's own JWT (HS256) after a successful Google sign-in.
 * The subject is our internal user id.
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final AppProperties props;

    public JwtService(JwtEncoder encoder, AppProperties props) {
        this.encoder = encoder;
        this.props = props;
    }

    public String issue(AppUser user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("job-tracker")
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(props.jwtExpiryHours(), ChronoUnit.HOURS))
                .claim("email", user.getEmail())
                .claim("name", user.getName())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
