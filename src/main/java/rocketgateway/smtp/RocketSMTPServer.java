package rocketgateway.smtp;

import org.subethamail.smtp.AuthenticationHandlerFactory;
import org.subethamail.smtp.auth.EasyAuthenticationHandlerFactory;
import org.subethamail.smtp.server.SMTPServer;
import rocketgateway.rocketchat.RocketChatAPI;

import javax.net.ssl.SSLContext;


public class RocketSMTPServer {
    static final int MAX_MESSAGE_SIZE = 25 * 1024 * 1024;
    private static final int MAX_CONNECTIONS = 50;
    private static final int MAX_RECIPIENTS = 100;
    private static final int CONNECTION_TIMEOUT_MS = 60_000;
    private final int smtpPort;
    private final SMTPServer smtpServer;
    private final boolean getSpam;
    private final String spamChannel;
    private final boolean enableTLS;
    private final boolean requireAuth;
    private boolean brokenTLS;

    public RocketSMTPServer(SMTPConfig smtpConfig, RocketChatAPI bot, boolean getSpam, String spamChannel) {
        this.smtpPort = smtpConfig.smtpPort();
        this.requireAuth = smtpConfig.requireAuth();
        this.enableTLS = smtpConfig.enableTLS();
        this.getSpam = getSpam;
        this.spamChannel = spamChannel;
        this.brokenTLS = false;

        RocketMessageHandler messageFactory = new RocketMessageHandler(bot, getSpam, spamChannel) ;
        RocketPasswordValidator validator = new RocketPasswordValidator(smtpConfig.smtpUsername(), smtpConfig.smtpPassword());

        // Limits protect the gateway against oversized mails and connection floods.
        SMTPServer.Builder smtpServerBuilder = SMTPServer
                .port(this.smtpPort)
                .messageHandlerFactory(messageFactory)
                .maxMessageSize(MAX_MESSAGE_SIZE)
                .maxConnections(MAX_CONNECTIONS)
                .maxRecipients(MAX_RECIPIENTS)
                .connectionTimeoutMs(CONNECTION_TIMEOUT_MS);

        // Only offer AUTH when it is required. Otherwise nobody needs to send credentials at all.
        if (requireAuth) {
            AuthenticationHandlerFactory authHandlerFactory = new EasyAuthenticationHandlerFactory(validator);
            smtpServerBuilder.requireAuth(true).authenticationHandlerFactory(authHandlerFactory);
        }

        try {
            if (enableTLS) {
                SSLContext sslContext = SSLLoader.getSslContext();
                smtpServerBuilder.enableTLS().
                        startTlsSocketFactory(sslContext);

                // Never accept credentials in cleartext: STARTTLS must come before AUTH.
                if (requireAuth) {
                    smtpServerBuilder.requireTLS();
                }
            }
        } catch (Exception e) {
            System.err.println("TLS initialization failed: " + e.getMessage());
            this.brokenTLS = true;
        }

        this.smtpServer = smtpServerBuilder.build();
    }

    public void start() {
        try {
            if (!brokenTLS)  {
                smtpServer.start();
                String statusMessage = generateStatusMessage();


                System.out.print(statusMessage);
            } else {
                throw new RuntimeException("TLS-configuration error. Something is wrong!\n" +
                        "Please check the ini-file or the provided certificate files!");
            }
        } catch (Exception e) {
            System.out.println("Server couldn't be started: " + e.getMessage());
        }
    }

    private String generateStatusMessage() {
        String statusMessage = String.format("-> Starting SMTP-Server on port %d.\n", this.smtpPort);

        if (this.requireAuth) {
            statusMessage += "-> SMTP-authentication is enabled\n";
        } else {
            statusMessage += "-> WARNING: SMTP-authentication is disabled. Anyone who can reach the port can post messages!\n";
        }

        if (this.enableTLS) {
            statusMessage += "-> TLS is enabled\n";
        }

        if (this.getSpam) {
            statusMessage += String.format("-> SPAM-catching is enabled. Sending SPAM messages to channel \"%s\" \n", this.spamChannel);
        }
        return statusMessage;
    }

    public void stop() {
        this.smtpServer.stop();
    }

}
