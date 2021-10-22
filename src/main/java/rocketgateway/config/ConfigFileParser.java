package rocketgateway.config;

import com.google.gson.*;
import rocketgateway.config.objects.EmailChannels;
import rocketgateway.config.objects.RocketGatewayConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class ConfigFileParser {
    private final String configFile;
    private RocketGatewayConfig config;
    private String errorMessage;

    public ConfigFileParser(String configFile) {
        this.configFile = configFile;
    }

    public boolean parse() {
        try {
            String json = Files.readString(Path.of(this.configFile).toAbsolutePath());
            Gson gson = new GsonBuilder()
                    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                    .create();

            this.config = gson.fromJson(json, RocketGatewayConfig.class);
            return false;
        } catch (Exception e) {
            this.errorMessage = e.toString();
            return true;
        }
    }

    public int getPort() {
        return this.config.getSmtp().getSmtpPort();
    }

    public String getSmtpUsername() {
        return this.config.getSmtp().getSmtpUsername();
    }

    public String getSmtpPassword() {
        return this.config.getSmtp().getSmtpPassword();
    }

    public Boolean getRequireAuth() {
        return this.config.getSmtp().requireAuth();
    }

    public String getRocketChatURL() {
        return this.config.getRocketchat().getRocketchatUrl();
    }

    public String getBotUser() {
        return this.config.getRocketchat().getBotUsername();
    }

    public String getBotPassword() {
        return this.config.getRocketchat().getBotPassword();
    }

    public boolean getSpam() {
        return this.config.getSpam().getSpam();
    }

    public String getSpamChannel() {
        return this.config.getSpam().getSpamChannel();
    }

    public boolean enableTLS() {
        return this.config.getTls().enableTls();
    }

    public String getCertificateChainFile() {
        return this.config.getTls().getCertificatechainFile();
    }

    public String getPrivateKeyFile() {
        return this.config.getTls().getPrivatekeyFile();
    }

    public String getPrivateKeyPassword() {
        return this.config.getTls().getPrivatekeyPassword();
    }

    public String getTrustedCertificate() {
        return this.config.getTls().getTrustedcertificateFile();
    }

    public String[] getTLSProtocols() {
        return this.config.getTls().getTlsVersions();
    }

    public Map<String, String> getEmailChannels() {
        Map<String, String> emailChannelMap = new HashMap<>();

        for (EmailChannels map : this.config.getEmailChannels()) {
            emailChannelMap.put(map.getAddress(), map.getChannel());
        }
        return emailChannelMap;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
