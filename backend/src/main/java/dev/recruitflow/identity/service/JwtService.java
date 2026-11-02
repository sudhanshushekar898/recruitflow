package dev.recruitflow.identity.service;

import com.nimbusds.jose.jwk.RSAKey;
import dev.recruitflow.identity.config.PasswordChangeGuardClaim;
import dev.recruitflow.identity.config.SecurityProperties;
import dev.recruitflow.identity.domain.Role;
import dev.recruitflow.identity.domain.User;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
class JwtService {

    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final Clock clock;
    private final String keyId;

    JwtService(JwtEncoder encoder, SecurityProperties properties, Clock clock, RSAKey key) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
        this.keyId = key.getKeyID();
    }

    long accessTokenTtlSeconds() {
        return properties.jwt().accessTokenTtl().toSeconds();
    }

    String issueAccessToken(User user) {
        Instant now = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(user.getId().toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.jwt().accessTokenTtl()))
                .claim("roles", user.getRoles().stream().map(Role::name).sorted().toList());
        if (user.isMustChangePassword()) {
            claims.claim(PasswordChangeGuardClaim.NAME, true);
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).keyId(keyId).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
