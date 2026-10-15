package rocketgateway.smtp;

import org.subethamail.smtp.MessageHandler;
import rocketgateway.message.RocketEmlAddress;
import rocketgateway.message.RocketEmlAttachment;
import rocketgateway.message.RocketEmlMessage;
import rocketgateway.rocketchat.Helpers;
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

        String alias = Helpers.escapeMentions(rocketMessage.getSender());

        for (RocketEmlAddress recipient : rocketMessage.getRecipients()) {
            String address = recipient.address();

            // The room-id is returned per message, so concurrent mails can never mix up their rooms.
            String roomId = this.bot.sendMessageToEmailAddress(message, address, alias);

            if (roomId != null) {
                if (!attachments.isEmpty()) {
                    boolean uploadFailed = false;

                    for (RocketEmlAttachment attachment : attachments) {
                        try {
                            String boundary = RocketEmlAttachment.newBoundary();
                            byte[] attachmentData = attachment.getUploadData(boundary);
                            this.bot.uploadFileToRoom(attachmentData, boundary, roomId);
                        } catch (IOException | RuntimeException e) {
                            uploadFailed = true;
                        }
                    }

                    if (uploadFailed) {
                        this.bot.sendMessageToEmailAddress("_Couldn't upload attachments!_", address, null);
                    }
                }

                if (rocketMessage.getSkippedAttachments() > 0) {
                    this.bot.sendMessageToEmailAddress(String.format("_%d attachment(s) skipped (too many or too large)._",
                            rocketMessage.getSkippedAttachments()), address, null);
                }
            } else if (this.getSpam && this.spamChannel != null) {
                this.bot.sendMessageToChannel(message, this.spamChannel, alias);
            }
        }

        return "OK";
    }

    @Override
    public void done() {
    }
}
