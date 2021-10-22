package rocketgateway.smtp;

import org.subethamail.smtp.MessageContext;
import org.subethamail.smtp.MessageHandlerFactory;
import rocketgateway.rocketchat.RocketChatAPI;


public class RocketMessageHandler implements MessageHandlerFactory {
    private final RocketChatAPI bot;
    private final boolean getSpam;
    private final String spamChannel;

    public RocketMessageHandler(RocketChatAPI bot, boolean getSpam, String spamChannel) {
        this.bot = bot;
        this.getSpam = getSpam;
        this.spamChannel = spamChannel;
    }

    @Override
    public RocketHandler create(MessageContext ctx) {
        return new RocketHandler(this.bot, this.getSpam, this.spamChannel);
    }
}
