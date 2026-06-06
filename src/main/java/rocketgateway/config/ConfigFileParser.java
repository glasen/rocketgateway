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

            RocketGatewayConfig parsed = gson.fromJson(json, RocketGatewayConfig.class);

            if (parsed == null) {
                this.errorMessage = "Configuration file is empty or could not be parsed.";
                return true;
            }

            String missingSection = findMissingSection(parsed);
            if (missingSection != null) {
                this.errorMessage = String.format("Missing required configuration section: \"%s\"", missingSection);
                return true;
            }

            // Only replace the active config once the new one is known to be valid. This keeps the
            // previously loaded configuration intact when a reload provides a broken file.
            this.config = parsed;
            return false;
        } catch (Exception e) {
            this.errorMessage = e.toString();
            return true;
        }
    }

    /**
     * Checks that all mandatory top-level sections are present.
     *
     * @param config Parsed configuration to validate
     * @return Name of the first missing section, or null when all required sections are present.
     */
    private String findMissingSection(RocketGatewayConfig config) {
        if (config.smtp() == null) {
            return "smtp";
        }
        if (config.rocketchat() == null) {
            return "rocketchat";
        }
        if (config.spam() == null) {
            return "spam";
        }
        if (config.tls() == null) {
            return "tls";
        }
        return null;
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
        return this.config.spam().getSpam();
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

        if (this.config.emailChannels() != null) {
            for (EmailChannels map : this.config.emailChannels()) {
                emailChannelMap.put(map.address(), map.channel());
            }
        }
        return emailChannelMap;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
