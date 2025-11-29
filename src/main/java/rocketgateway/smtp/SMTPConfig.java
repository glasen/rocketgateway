package rocketgateway.smtp;

public record SMTPConfig(int smtpPort, String smtpUsername, String smtpPassword, boolean requireAuth,
                         boolean enableTLS) {}
