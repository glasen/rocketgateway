package rocketgateway.config;

import com.google.gson.*;
import rocketgateway.config.objects.EmailChannels;
import rocketgateway.config.objects.RocketGatewayConfig;
import rocketgateway.config.objects.TLS;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigFileParser {
    private final String configFile;
    private RocketGatewayConfig config;
    private String errorMessage;
    private final List<String> warnings = new ArrayList<>();

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

            String invalid = validate(parsed);
            if (invalid != null) {
                this.errorMessage = invalid;
                return true;
            }

            this.warnings.clear();
            collectWarnings(parsed);

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
     * Validates values which would otherwise cause obscure failures later on.
     *
     * @return Error message or null when the configuration is valid.
     */
    private String validate(RocketGatewayConfig config) {
        int port = config.smtp().smtpPort();
        if (port < 1 || port > 65535) {
            return "Invalid \"smtp_port\": " + port;
        }

        String url = config.rocketchat().rocketchatUrl();
        if (url == null || url.isBlank()) {
            return "Missing \"rocketchat_url\"";
        }

        try {
            String scheme = URI.create(url).getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                return "\"rocketchat_url\" must start with http:// or https://";
            }
        } catch (IllegalArgumentException e) {
            return "Invalid \"rocketchat_url\": " + e.getMessage();
        }

        if (config.tls().enableTls()) {
            TLS tls = config.tls();
            if (tls.certificatechainFile() == null || tls.privatekeyFile() == null
                    || tls.trustedcertificateFile() == null) {
                return "TLS is enabled but certificate or key files are not configured";
            }
        }

        return null;
    }

    private void collectWarnings(RocketGatewayConfig config) {
        String url = config.rocketchat().rocketchatUrl();
        URI uri = URI.create(url);
        String host = uri.getHost();
        boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "[::1]".equals(host);

        if ("http".equalsIgnoreCase(uri.getScheme()) && !local) {
            this.warnings.add("\"rocketchat_url\" uses plain HTTP. The bot password and tokens are sent unencrypted!");
        }

        if (!config.smtp().requireAuth()) {
            this.warnings.add("\"require_auth\" is false. Anyone who can reach the SMTP port can post messages.");
        }

        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(Path.of(this.configFile).toAbsolutePath());
            if (perms.contains(PosixFilePermission.GROUP_READ) || perms.contains(PosixFilePermission.OTHERS_READ)) {
                this.warnings.add("The configuration file contains passwords and is readable by other users. "
                        + "Consider \"chmod 600\".");
            }
        } catch (Exception ignored) {
            // Not a POSIX file system.
        }
    }

    public List<String> getWarnings() {
        return warnings;
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
        String password = this.config.tls().privatekeyPassword();
        return password == null ? "" : password;
    }

    public String getTrustedCertificate() {
        return this.config.tls().trustedcertificateFile();
    }

    public String[] getTLSProtocols() {
        String[] versions = this.config.tls().tlsVersions();
        return versions == null || versions.length == 0 ? new String[]{"TLSv1.3", "TLSv1.2"} : versions;
    }

    public Map<String, String> getEmailChannels() {
        Map<String, String> emailChannelMap = new HashMap<>();

        if (this.config.emailChannels() != null) {
            for (EmailChannels map : this.config.emailChannels()) {
                if (map != null && map.address() != null && map.channel() != null) {
                    emailChannelMap.put(map.address(), map.channel());
                }
            }
        }
        return emailChannelMap;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
