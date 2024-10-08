package rocketgateway;

import eu.tneitzel.argparse4j.inf.Namespace;
import rocketgateway.config.CommandLineParser;
import rocketgateway.config.ConfigFileParser;
import rocketgateway.rocketchat.RocketChatAPI;
import rocketgateway.smtp.RocketSMTPServer;
import rocketgateway.smtp.SSLLoader;
import sun.misc.Signal;

import java.io.IOException;
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

        if (botUsername == null | botPassword == null) {
            throw new RuntimeException("You need to provide the credentials for a RocketChat-User!");
        }

        if (requireAuth & (smtpUsername == null | smtpPassword == null)) {
            throw new RuntimeException("You need to provide the credentials for the SMTP-server!");
        }

        RocketChatAPI bot = new RocketChatAPI(botUsername, botPassword, rocketChatURL, emailChannels);
        RocketSMTPServer smtpServer = new RocketSMTPServer(smtpPort, smtpUsername, smtpPassword,
                bot, requireAuth, enableTLS, getSpam, spamChannel);

        bot.login();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down ...");
            smtpServer.stop();
            try {
                bot.logout();
            } catch (IOException ignored) {
            }
        }));


        Signal.handle(new Signal("HUP"), signal -> {
            configFileParser.parse();
            Map<String, String> newEmailChannels = configFileParser.getEmailChannels();
            bot.updateEmailChannels(newEmailChannels);
        });

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
}
