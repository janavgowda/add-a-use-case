package team.projectpulse.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.student.Student;
import team.projectpulse.user.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * FR-SEC-active-account: a token is accepted only while its account is enabled.
 */
@ExtendWith(MockitoExtension.class)
class ActiveAccountJwtValidatorTest {

    @Mock
    UserRepository userRepository;

    @InjectMocks
    ActiveAccountJwtValidator validator;

    private static Jwt tokenWithUserId(Object userId) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("someone");
        if (userId != null) {
            builder.claim("userId", userId);
        }
        return builder.build();
    }

    private static void assertInvalidToken(OAuth2TokenValidatorResult result) {
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).allMatch(error -> error.getErrorCode().equals(OAuth2ErrorCodes.INVALID_TOKEN));
    }

    @Test
    void testEnabledAccountPasses() {
        given(this.userRepository.findById(4)).willReturn(Optional.of(new Student("john", "John", "Smith", "j.smith@abc.edu", "x", true, "student")));

        assertThat(this.validator.validate(tokenWithUserId(4L)).hasErrors()).isFalse();
    }

    @Test
    void testDisabledStudentFails() {
        given(this.userRepository.findById(4)).willReturn(Optional.of(new Student("john", "John", "Smith", "j.smith@abc.edu", "x", false, "student")));

        assertInvalidToken(this.validator.validate(tokenWithUserId(4L)));
    }

    @Test
    void testDisabledInstructorFails() {
        given(this.userRepository.findById(2)).willReturn(Optional.of(new Instructor("bill", "Bill", "Gates", "b.gates@abc.edu", "x", false, "instructor")));

        assertInvalidToken(this.validator.validate(tokenWithUserId(2)));
    }

    @Test
    void testUserIdNamingNoAccountFails() {
        given(this.userRepository.findById(999)).willReturn(Optional.empty());

        assertInvalidToken(this.validator.validate(tokenWithUserId(999)));
    }

    @Test
    void testMissingUserIdClaimFails() {
        assertInvalidToken(this.validator.validate(tokenWithUserId(null)));
        verifyNoInteractions(this.userRepository);
    }

    @Test
    void testNonNumericUserIdClaimFails() {
        assertInvalidToken(this.validator.validate(tokenWithUserId("not-a-number")));
        assertInvalidToken(this.validator.validate(tokenWithUserId(Long.MAX_VALUE)));
        verifyNoInteractions(this.userRepository);
    }

}
