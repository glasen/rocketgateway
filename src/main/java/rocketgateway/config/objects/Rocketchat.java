package rocketgateway.config.objects;

@SuppressWarnings("ALL")
public class Rocketchat {
    private String rocketchatUrl;
    private String botUsername;
    private String botPassword;

    public String getRocketchatUrl() {
        return rocketchatUrl;
    }

    public String getBotUsername() {
        return botUsername;
    }

    public String getBotPassword() {
        return botPassword;
    }
}
