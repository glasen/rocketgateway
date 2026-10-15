package rocketgateway.message;

import io.github.furstenheim.CopyDown;
import jakarta.activation.DataSource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import rocketgateway.apache_commons_email.MimeMessageParser;
import rocketgateway.rocketchat.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class RocketEmlMessage {
    private static final int MAX_ATTACHMENTS = 10;
    private static final int MAX_ATTACHMENT_BYTES = 10 * 1024 * 1024;
    private final InputStream data;
    private final Set<RocketEmlAddress> recipients;
    private final List<RocketEmlAttachment> attachments;
    private final CopyDown converter;
    private int skippedAttachments;
    private String date;
    private String sender;
    private String subject;
    private String body;

    /**
     * Stores data generated from a real EML-message.
     *
     * @param data InputStream generated from EML-message
     */
    public RocketEmlMessage(InputStream data) {
        this.data = data;
        this.body = "";
        this.recipients = new HashSet<>();
        this.attachments = new ArrayList<>();
        this.converter = new CopyDown();
    }

    /**
     * Parses the EML-message and extracts the needed data from it.
     */
    public void parseEML() {
        try {
            MimeMessage message = new MimeMessage(Helpers.getSession(), this.data);
            MimeMessageParser mimeMessageParser = new MimeMessageParser(message);
            mimeMessageParser.parse();

            // Return 1970-01-01 if date from e-mail cannot be extracted.
            this.date = Optional.ofNullable(message.getSentDate()).orElse(new Date(0)).toString();

            this.sender = Helpers.safeString(mimeMessageParser.getFrom());
            this.subject = Helpers.safeString(mimeMessageParser.getSubject());

            // Parse email-addresses
            getAddresses(mimeMessageParser.getTo());
            getAddresses(mimeMessageParser.getCc());
            getAddresses(mimeMessageParser.getBcc());

            /* Check if the e-mail has a plain/text body. The normal text body is preferred because RocketChat
               cannot show HTML-content.
             */
            if (mimeMessageParser.hasPlainContent()) {
                this.body = Helpers.safeString(mimeMessageParser.getPlainContent()).strip().replaceAll("\r\n", "\n");
            }

            // If there is no plain body check if there is a html body.
            if (this.body.isEmpty() && mimeMessageParser.hasHtmlContent()) {
                String htmlContent = mimeMessageParser.getHtmlContent();
                this.body = converter.convert(htmlContent);
            }

            // Check if there are attachments
            if (mimeMessageParser.hasAttachments()) {
                for (DataSource attachment : mimeMessageParser.getAttachmentList()) {
                    getAttachment(attachment);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void getAttachment(DataSource attachment) throws IOException {
        String filename = Helpers.safeString(attachment.getName(), "unknown.dat");
        String mimeType = Helpers.safeString(attachment.getContentType(), "application/octet-stream");

        if (this.attachments.size() >= MAX_ATTACHMENTS) {
            this.skippedAttachments++;
            return;
        }

        try (InputStream stream = attachment.getInputStream()) {
            // Read at most one byte more than allowed, so oversized attachments never fill the heap.
            byte[] content = stream.readNBytes(MAX_ATTACHMENT_BYTES + 1);

            if (content.length > MAX_ATTACHMENT_BYTES) {
                this.skippedAttachments++;
            } else if (content.length > 0) {
                // Only add those attachments which have some content.
                this.attachments.add(new RocketEmlAttachment(filename, mimeType, content));
            }
        }
    }

    /**
     * Extract address data from Address-object. We only need the name and the address.
     *
     * @param addresses List with Address-objects
     */
    private void getAddresses(List<Address> addresses) {
        if (addresses != null) {
            for (Address address : addresses) {
                try {
                    String emailAddress = Helpers.safeString(((InternetAddress) address).getAddress());
                    String name = Helpers.safeString(((InternetAddress) address).getPersonal());

                    if (!emailAddress.isEmpty()) {
                        this.recipients.add(new RocketEmlAddress(name, emailAddress));
                    }
                } catch (Exception ignored) {
                }
            }
        }
    }

    /**
     * Get addresses
     *
     * @return Set with RocketEmlAddress
     */
    public Set<RocketEmlAddress> getRecipients() {
        return recipients;
    }

    /**
     * Get attachments
     *
     * @return List with RocketEMLAttachment
     */
    public List<RocketEmlAttachment> getAttachments() {
        return attachments;
    }

    /**
     * Generates RocketChat-message from stored data. The first lines are Markdown-formatted
     *
     * @return String with message.
     */
    public String makeMessage() {
        String messageTemplate = "*Date:* %s\n*Subject:* %s\n\n%s";
        return String.format(messageTemplate, this.date,
                Helpers.escapeMentions(this.subject), Helpers.escapeMentions(this.body));
    }

    /**
     * Get the number of attachments which were not taken over because they were too many or too large.
     *
     * @return int Number of skipped attachments
     */
    public int getSkippedAttachments() {
        return skippedAttachments;
    }

    /**
     * Get sender of message.
     *
     * @return String with sender e-mail-address
     */
    public String getSender() {
        return sender;
    }
}
