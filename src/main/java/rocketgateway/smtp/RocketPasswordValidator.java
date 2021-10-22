package rocketgateway.smtp;

import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.auth.LoginFailedException;
import org.subethamail.smtp.auth.UsernamePasswordValidator;

public class RocketPasswordValidator implements UsernamePasswordValidator {
    private final String username;
    private final String password;

    public RocketPasswordValidator(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public void login(String username, String password, MessageContext context) throws LoginFailedException {
        if (!this.username.equals(username) & !this.password.equals(password)) {
            throw new LoginFailedException();
        }
    }
}
