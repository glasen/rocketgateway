package rocketgateway.rocketchat;

import jakarta.mail.Session;

import java.util.Optional;
import java.util.Properties;

public abstract class Helpers {
    public static String safeString(String value) {
        return safeString(value, "");
    }

    public static String safeString(String value, String returnValue) {
        return Optional.ofNullable(value). orElse(returnValue);
    }

    /**
     * Neutralizes RocketChat mentions (@all, @here, @user) in untrusted text by inserting a zero-width
     * space after every "@". The text still reads the same but no longer notifies anybody.
     */
    public static String escapeMentions(String value) {
        return safeString(value).replace("@", "@\u200B");
    }

    public static Session getSession() {
        // Create dummy smtp-server entries. MimeMessage needs them.
        Properties props = new Properties();
        props.put("mail.host", "smtp.dummydomain.com");
        props.put("mail.transport.protocol", "smtp");
        return Session.getInstance(props, null);
    }
}
