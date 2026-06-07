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

    public static Session getSession() {
        // Create dummy smtp-server entries. MimeMessage needs them.
        Properties props = new Properties();
        props.put("mail.host", "smtp.dummydomain.com");
        props.put("mail.transport.protocol", "smtp");
        return Session.getInstance(props, null);
    }
}
