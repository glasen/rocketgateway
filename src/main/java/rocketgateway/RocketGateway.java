package rocketgateway;

import eu.tneitzel.argparse4j.inf.Namespace;
import rocketgateway.config.CommandLineParser;
import rocketgateway.config.ConfigFileParser;
import rocketgateway.rocketchat.RocketChatAPI;
import rocketgateway.smtp.RocketSMTPServer;
import rocketgateway.smtp.SMTPConfig;
import rocketgateway.smtp.SSLLoader;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;

public class RocketGateway {
    public static void main(String[] args) throws IOException {
        CommandLineParser parser = new CommandLineParser(args);
        Namespace res = parser.getRes();

        String configFile = res.get("configfile");
        ConfigFileParser configFileParser = new ConfigFileParser(configFile);
        boolean error = configFileParser.parse();

        if (error) {
            System.out.println(configFileParser.getErrorMessage());
            System.exit(1);
        }
        
        int smtpPort = configFileParser.getPort();
        String rocketChatURL = configFileParser.getRocketChatURL();

        String botUsername = configFileParser.getBotUser();
        String botPassword = configFileParser.getBotPassword();
        String smtpUsername = configFileParser.getSmtpUsername();
        String smtpPassword = configFileParser.getSmtpPassword();
        boolean requireAuth = configFileParser.getRequireAuth();

        boolean getSpam = configFileParser.getSpam();
        String spamChannel = configFileParser.getSpamChannel();

        Map<String, String> emailChannels = configFileParser.getEmailChannels();

        boolean enableTLS = configFileParser.enableTLS();
        String certificateChainFile = configFileParser.getCertificateChainFile();
        String privateKeyFile = configFileParser.getPrivateKeyFile();
        String privateKeyPassword = configFileParser.getPrivateKeyPassword();
        String trustedCertificate = configFileParser.getTrustedCertificate();
        String[] tlsProtocols = configFileParser.getTLSProtocols();

        if (enableTLS) {
            try {
                SSLLoader.init(certificateChainFile, privateKeyFile, privateKeyPassword, trustedCertificate, tlsProtocols);
            } catch (Exception e) {
                throw new RuntimeException(String.format("SSLLoader error: \"%s\"\n" +
                        "Please check the provided certificate files!", e.getLocalizedMessage()));
            }
        }

        if (botUsername == null || botPassword == null) {
            throw new RuntimeException("You need to provide the credentials for a RocketChat-User!");
        }

        if (requireAuth && (smtpUsername == null || smtpPassword == null)) {
            throw new RuntimeException("You need to provide the credentials for the SMTP-server!");
        }

        RocketChatAPI bot = new RocketChatAPI(botUsername, botPassword, rocketChatURL, emailChannels);

        SMTPConfig smtpConfig = new SMTPConfig(smtpPort, smtpUsername, smtpPassword, requireAuth, enableTLS);
        RocketSMTPServer smtpServer = new RocketSMTPServer(smtpConfig, bot, getSpam, spamChannel);

        bot.login();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down ...");
            smtpServer.stop();
            try {
                bot.logout();
            } catch (IOException ignored) {
            }
        }));


        watchConfigFile(configFile, configFileParser, bot);

        if (bot.getLoginStatus()) {
            bot.init();
            smtpServer.start();
        } else {
            String errorMessage = String.format("Couldn't login with user \"%s\"", botUsername);
            System.out.println(errorMessage);
            bot.logout();
            System.out.println("Exiting!");
        }
    }

    /**
     * Watches the configuration file and reloads the e-mail-to-channel mappings whenever the file
     * changes. This replaces the previous SIGHUP handler, which relied on an internal JDK API.
     *
     * @param configFile       Path to the configuration file to watch.
     * @param configFileParser Parser used to re-read the configuration.
     * @param bot              RocketChat client whose mappings are updated on a successful reload.
     */
    private static void watchConfigFile(String configFile, ConfigFileParser configFileParser, RocketChatAPI bot) {
        Path path = Path.of(configFile).toAbsolutePath();
        Path directory = path.getParent();
        Path fileName = path.getFileName();

        if (directory == null || fileName == null) {
            return;
        }

        Thread watcher = new Thread(() -> {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                directory.register(watchService,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_CREATE);

                while (true) {
                    WatchKey key = watchService.take();

                    boolean configChanged = false;
                    for (WatchEvent<?> event : key.pollEvents()) {
                        Object context = event.context();
                        if (context instanceof Path changed && changed.getFileName().equals(fileName)) {
                            configChanged = true;
                        }
                    }

                    if (configChanged && !configFileParser.parse()) {
                        bot.updateEmailChannels(configFileParser.getEmailChannels());
                    }

                    if (!key.reset()) {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception ignored) {
            }
        }, "config-watcher");

        watcher.setDaemon(true);
        watcher.start();
    }
}
