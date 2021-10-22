package rocketgateway.config.objects;

import java.util.List;

public class RocketGatewayConfig {
    private SMTP smtp;
    private Rocketchat rocketchat;
    private Spam spam;
    private TLS tls;
    private List<EmailChannels> emailChannels;

    public SMTP getSmtp() {
        return smtp;
    }

    public Rocketchat getRocketchat() {
        return rocketchat;
    }

    public Spam getSpam() {
        return spam;
    }

    public TLS getTls() {
        return tls;
    }

    public List<EmailChannels> getEmailChannels() {
        return emailChannels;
    }
}
