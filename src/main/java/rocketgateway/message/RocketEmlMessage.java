package rocketgateway.message;

import jakarta.activation.DataSource;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import rocketgateway.apache_commons_email.MimeMessageParser;

import java.io.InputStream;
import java.util.*;

public class RocketEmlMessage {
    private final InputStream data;
    private final Set<RocketEmlAddress> recipients;
    private final List<RocketEmlAttachment> attachments;
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
    }

    /**
     * Parses the EML-message and extracts the needed data from it.
     */
    public void parseEML() {
        // Create dummy smtp-server entries. MimeMessage needs them.
        Properties props = System.getProperties();
        props.put("mail.host", "smtp.dummydomain.com");
        props.put("mail.transport.protocol", "smtp");
        Session mailSession = Session.getDefaultInstance(props, null);

        try {
            MimeMessage message = new MimeMessage(mailSession, this.data);
            MimeMessageParser mimeMessageParser = new MimeMessageParser(message);
            mimeMessageParser.parse();

            // Return 1970-01-01 if date from e-mail cannot be extracted.
            this.date = Optional.ofNullable(message.getSentDate()).orElse(new Date(0)).toString();

            this.sender = Optional.ofNullable(mimeMessageParser.getFrom()).orElse("");
            this.subject = Optional.ofNullable(mimeMessageParser.getSubject()).orElse("");

            // Parse email-addresses
            getAddresses(mimeMessageParser.getTo());
            getAddresses(mimeMessageParser.getCc());
            getAddresses(mimeMessageParser.getBcc());

            /* Check if the e-mail has a plain/text body. The normal text body is preferred because RocketChat
               cannot show HTML-content.
             */
            if (mimeMessageParser.hasPlainContent()) {
                this.body = Optional.ofNullable(mimeMessageParser.getPlainContent()).orElse("").strip().replaceAll("\r\n", "\n");
            }

            // If there is no plain body check if there is a html body.
            if (this.body.isEmpty() & mimeMessageParser.hasHtmlContent()) {
                String htmlContent = mimeMessageParser.getHtmlContent();

                // Strip html body of all tags.
                Document doc = Jsoup.parse(htmlContent);
                this.body = doc.text().strip().replaceAll("\r\n", "\n");

                // Make an attachment from the original html body so the user can see the original message.
                this.attachments.add(new RocketEmlAttachment(this.subject+".html", "text/html", htmlContent.getBytes()));
            }

            // Check if there are attachments
            if (mimeMessageParser.hasAttachments()) {
                try {
                    for (DataSource attachment : mimeMessageParser.getAttachmentList()) {
                        String filename = Optional.ofNullable(attachment.getName()).orElse("unknown.dat");
                        String mimeType = Optional.ofNullable(attachment.getContentType()).orElse("application/octet-stream");
                        try (InputStream stream = attachment.getInputStream()) {
                            byte[] content = stream.readAllBytes();

                            // Only add those attachments which have some content.
                            if (content.length > 0) {
                                this.attachments.add(new RocketEmlAttachment(filename, mimeType, content));
                            }
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Extract address data from Address-object. We only need the name and the address.
     * @param addresses List with Address-objects
     */
    private void getAddresses(List<Address> addresses) {
        if (addresses != null) {
            for (Address address : addresses) {
                try {
                    String emailAddress = Optional.ofNullable(((InternetAddress) address).getAddress()).orElse("");
                    String name = Optional.ofNullable(((InternetAddress) address).getPersonal()).orElse("");

                    if (!emailAddress.isEmpty()) {
                        this.recipients.add(new RocketEmlAddress(name, emailAddress));
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Get addresses
     * @return Set with RocketEmlAddress
     */
    public Set<RocketEmlAddress> getRecipients() {
        return recipients;
    }

    /**
     * Get attachments
     * @return List with RocketEMLAttachment
     */
    public List<RocketEmlAttachment> getAttachments() {
        return attachments;
    }

    /**
     * Generates RocketChat-message from stored data. The first lines are Markdown-formatted
     * @return String with message.
     */
    public String makeMessage() {
        String messageTemplate = "**Sender:** %s\n**Date:** %s\n**Subject:** %s\n**Body:**\n%s";
        return String.format(messageTemplate, this.sender, this.date, this.subject, this.body);
    }

    /**
     * Get sender of message.
     * @return String with sender e-mail-address
     */
    public String getSender() {
        return sender;
    }
}
