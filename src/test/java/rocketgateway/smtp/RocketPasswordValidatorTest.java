package rocketgateway.smtp;

import org.junit.jupiter.api.Test;
import org.subethamail.smtp.auth.LoginFailedException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RocketPasswordValidatorTest {
    private final RocketPasswordValidator validator = new RocketPasswordValidator("user", "pass");

    @Test
    void acceptsCorrectCredentials() {
        assertDoesNotThrow(() -> validator.login("user", "pass", null));
    }

    @Test
    void rejectsWrongCredentials() {
        assertThrows(LoginFailedException.class, () -> validator.login("user", "nope", null));
        assertThrows(LoginFailedException.class, () -> validator.login("other", "pass", null));
        assertThrows(LoginFailedException.class, () -> validator.login(null, null, null));
    }

    @Test
    void rejectsEverythingWhenNothingIsConfigured() {
        RocketPasswordValidator unconfigured = new RocketPasswordValidator(null, null);

        assertThrows(LoginFailedException.class, () -> unconfigured.login("user", "pass", null));
        assertThrows(LoginFailedException.class, () -> unconfigured.login(null, null, null));
    }
}
