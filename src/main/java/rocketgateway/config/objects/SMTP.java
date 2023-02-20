package rocketgateway.config.objects;

@SuppressWarnings("ALL")
public record SMTP(int smtpPort, boolean requireAuth, String smtpUsername, String smtpPassword) {
}
