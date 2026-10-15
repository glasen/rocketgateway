package rocketgateway.smtp;

import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.auth.LoginFailedException;
import org.subethamail.smtp.auth.UsernamePasswordValidator;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class RocketPasswordValidator implements UsernamePasswordValidator {
    private final String username;
    private final String password;

    public RocketPasswordValidator(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public void login(String username, String password, MessageContext context) throws LoginFailedException {
        // Evaluate both comparisons without short-circuiting to avoid timing differences.
        boolean userOk = constantTimeEquals(this.username, username);
        boolean passOk = constantTimeEquals(this.password, password);

        if (!(userOk & passOk)) {
            throw new LoginFailedException();
        }
    }

    private static boolean constantTimeEquals(String expected, String given) {
        if (expected == null || given == null) {
            return false;
        }

        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), given.getBytes(StandardCharsets.UTF_8));
    }
}
