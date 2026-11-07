package team.projectpulse.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.user.UserRepository;

/**
 * Accepts a bearer token only while the account it was issued to is still enabled (FR-SEC-active-account).
 *
 * <p>Login already refuses a disabled account, but a token issued before the deactivation would otherwise stay valid
 * until it expires, two hours later. This validator runs on every authenticated request, beside the default signature
 * and expiry checks, and reads the account named by the token's {@code userId} claim. A missing, malformed, or unknown
 * claim fails the token as well, so the check fails closed. The failure surfaces as {@code InvalidBearerTokenException},
 * which the bearer entry point maps to {@code 401}.
 *
 * <p>It costs one primary-key read per request, accepted at course scale (architecture-of-record, Authentication ›
 * Account status). It applies to students and instructors alike.
 */
@Component
public class ActiveAccountJwtValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INACTIVE_ACCOUNT = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN, "The account this token was issued to is deactivated or no longer exists.", null);

    private final UserRepository userRepository;


    public ActiveAccountJwtValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Integer userId = readUserId(jwt.getClaim("userId"));
        if (userId == null) {
            return OAuth2TokenValidatorResult.failure(INACTIVE_ACCOUNT);
        }
        boolean enabled = this.userRepository.findById(userId)
                .map(PeerEvaluationUser::isEnabled)
                .orElse(false);
        return enabled ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(INACTIVE_ACCOUNT);
    }

    private static Integer readUserId(Object claim) {
        if (claim instanceof Integer i) return i;
        if (claim instanceof Long l) return (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) ? l.intValue() : null;
        if (claim instanceof String s) {
            try {
                return Integer.valueOf(s);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

}
