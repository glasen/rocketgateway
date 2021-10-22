package rocketgateway.smtp;

import org.subethamail.smtp.AuthenticationHandlerFactory;
import org.subethamail.smtp.auth.EasyAuthenticationHandlerFactory;
import org.subethamail.smtp.server.SMTPServer;
import rocketgateway.rocketchat.RocketChatAPI;

import javax.net.ssl.SSLContext;


public class RocketSMTPServer {
    private final int smtpPort;
    private final SMTPServer smtpServer;
    private final boolean getSpam;
    private final String spamChannel;
    private final boolean enableTLS;
    private final boolean requireAuth;
    private boolean brokenTLS;

    public RocketSMTPServer(int smtpPort, String username, String password, RocketChatAPI bot,
                            boolean requireAuth, boolean enableTLS,
                            boolean getSpam, String spamChannel) {
        this.smtpPort = smtpPort;
        this.requireAuth = requireAuth;
        this.enableTLS = enableTLS;
        this.getSpam = getSpam;
        this.spamChannel = spamChannel;
        this.brokenTLS = false;

        RocketMessageHandler messageFactory = new RocketMessageHandler(bot, getSpam, spamChannel) ;
        RocketPasswordValidator validator = new RocketPasswordValidator(username, password);
        AuthenticationHandlerFactory authHandlerFactory = new EasyAuthenticationHandlerFactory(validator);
        SMTPServer.Builder smtpServerBuilder = SMTPServer
                .port(this.smtpPort)
                .requireAuth(requireAuth)
                .authenticationHandlerFactory(authHandlerFactory)
                .messageHandlerFactory(messageFactory);

        try {
            if (enableTLS) {
                SSLContext sslContext = SSLLoader.getSslContext();
                smtpServerBuilder.enableTLS().
                        startTlsSocketFactory(sslContext);
            }
        } catch (Exception e) {
            this.brokenTLS = true;
        }

        this.smtpServer = smtpServerBuilder.build();
    }

    public void start() {
        try {
            if (!brokenTLS)  {
                smtpServer.start();
                String statusMessage = String.format("-> Starting SMTP-Server on port %d.\n", this.smtpPort);

                if (this.requireAuth) {
                    statusMessage += "-> SMTP-authentication is enabled\n";
                }

                if (this.enableTLS) {
                    statusMessage += "-> TLS is enabled\n";
                }

                if (this.getSpam) {
                    statusMessage += String.format("-> SPAM-catching is enabled. Sending SPAM messages to channel \"%s\" \n", this.spamChannel);
                }


                System.out.print(statusMessage);
            } else {
                throw new RuntimeException("TLS-configuration error. Something is wrong!\n" +
                        "Please check the ini-file or the provided certificate files!");
            }
        } catch (Exception e) {
            System.out.println("Server couldn't be started: " + e.getMessage());
        }
    }

    public void stop() {
        this.smtpServer.stop();
    }

}
