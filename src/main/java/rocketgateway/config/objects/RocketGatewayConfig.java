package rocketgateway.config.objects;

import java.util.List;

@SuppressWarnings("ALL")
public record RocketGatewayConfig(SMTP smtp, Rocketchat rocketchat, Spam spam, TLS tls, List<EmailChannels> emailChannels) {}
