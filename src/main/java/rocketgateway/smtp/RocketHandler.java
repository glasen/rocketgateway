package rocketgateway.smtp;

import org.subethamail.smtp.MessageHandler;
import rocketgateway.message.RocketEmlAddress;
import rocketgateway.message.RocketEmlAttachment;
import rocketgateway.message.RocketEmlMessage;
import rocketgateway.rocketchat.RocketChatAPI;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class RocketHandler implements MessageHandler {
    private final RocketChatAPI bot;
    private final boolean getSpam;
    private final String spamChannel;

    public RocketHandler(RocketChatAPI bot, boolean getSpam, String spamChannel) {
        this.bot = bot;
        this.getSpam = getSpam;
        this.spamChannel = spamChannel;
    }

    @Override
    public void from(String from) {
    }

    @Override
    public void recipient(String recipient) {
    }

    @Override
    public String data(InputStream data) {
        RocketEmlMessage rocketMessage = new RocketEmlMessage(data);
        rocketMessage.parseEML();
        String message = rocketMessage.makeMessage();
        List<RocketEmlAttachment> attachments = rocketMessage.getAttachments();

        for (RocketEmlAddress recipient : rocketMessage.getRecipients()) {
            String address = recipient.getAddress();
            String alias = rocketMessage.getSender();

            boolean status = this.bot.sendMessageToEmailAddress(message, address, alias);

            if (status) {
                String roomId = bot.getLastRoomId();

                if (!roomId.isEmpty()) {
                    for (RocketEmlAttachment attachment : attachments) {
                        try {
                            byte[] attachmentData = attachment.getUploadData();
                            this.bot.uploadFileToRoom(attachmentData, roomId);
                        } catch (IOException e) {
                            this.bot.sendMessageToEmailAddress("_Couldn't upload attachments!_", address, null);
                        }
                    }
                } else {
                    this.bot.sendMessageToEmailAddress("_Couldn't upload attachments!_", address, null);
                }
            } else {
                if (this.getSpam) {
                    this.bot.sendMessageToChannel(message, this.spamChannel, alias);
                }
            }
        }

        return "OK";
    }

    @Override
    public void done() {
    }
}
