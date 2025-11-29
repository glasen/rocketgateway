package rocketgateway.rocketchat;

import java.util.Optional;

public abstract class Helpers {
    public static String safeString(String value) {
        return safeString(value, "");
    }

    public static String safeString(String value, String returnValue) {
        return Optional.ofNullable(value). orElse(returnValue);
    }
}
