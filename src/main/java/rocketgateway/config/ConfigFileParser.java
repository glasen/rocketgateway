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
        return this.config.smtp().smtpPort();
    }

    public String getSmtpUsername() {
        return this.config.smtp().smtpUsername();
    }

    public String getSmtpPassword() {
        return this.config.smtp().smtpPassword();
    }

    public Boolean getRequireAuth() {
        return this.config.smtp().requireAuth();
    }

    public String getRocketChatURL() {
        return this.config.rocketchat().rocketchatUrl();
    }

    public String getBotUser() {
        return this.config.rocketchat().botUsername();
    }

    public String getBotPassword() {
        return this.config.rocketchat().botPassword();
    }

    public boolean getSpam() {
        return this.config.spam().enableSpamCatching();
    }

    public String getSpamChannel() {
        return this.config.spam().spamChannel();
    }

    public boolean enableTLS() {
        return this.config.tls().enableTls();
    }

    public String getCertificateChainFile() {
        return this.config.tls().certificatechainFile();
    }

    public String getPrivateKeyFile() {
        return this.config.tls().privatekeyFile();
    }

    public String getPrivateKeyPassword() {
        return this.config.tls().privatekeyPassword();
    }

    public String getTrustedCertificate() {
        return this.config.tls().trustedcertificateFile();
    }

    public String[] getTLSProtocols() {
        return this.config.tls().tlsVersions();
    }

    public Map<String, String> getEmailChannels() {
        Map<String, String> emailChannelMap = new HashMap<>();

        for (EmailChannels map : this.config.emailChannels()) {
            emailChannelMap.put(map.address(), map.channel());
        }
        return emailChannelMap;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
