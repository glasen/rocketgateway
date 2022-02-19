package rocketgateway.config.objects;

@SuppressWarnings("ALL")
public class SMTP {
    private int smtpPort;
    private boolean requireAuth;
    private String smtpUsername;
    private String smtpPassword;

    public int getSmtpPort() {
        return smtpPort;
    }

    public boolean requireAuth() {
        return requireAuth;
    }

    public String getSmtpUsername() {
        return smtpUsername;
    }

    public String getSmtpPassword() {
        return smtpPassword;
    }
}
