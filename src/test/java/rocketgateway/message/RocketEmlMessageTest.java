package rocketgateway.message;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class RocketEmlMessageTest {
    private static RocketEmlMessage parse(String raw) {
        RocketEmlMessage message = new RocketEmlMessage(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        message.parseEML();
        return message;
    }

    private static String multipart(int attachments, int bytesEach) {
        StringBuilder sb = new StringBuilder("""
                From: a@x.org\r
                To: Bob <bob@test.local>, carl@test.local\r
                Cc: dora@test.local\r
                Subject: Hello @all\r
                MIME-Version: 1.0\r
                Content-Type: multipart/mixed; boundary="XX"\r
                \r
                --XX\r
                Content-Type: text/plain; charset=utf-8\r
                \r
                ping @here and @bob\r
                """);
        String encoded = Base64.getMimeEncoder().encodeToString(new byte[bytesEach]);

        for (int i = 0; i < attachments; i++) {
            sb.append("--XX\r\nContent-Type: application/octet-stream; name=\"f").append(i).append(".bin\"\r\n")
                    .append("Content-Disposition: attachment; filename=\"f").append(i).append(".bin\"\r\n")
                    .append("Content-Transfer-Encoding: base64\r\n\r\n").append(encoded).append("\r\n");
        }

        return sb.append("--XX--\r\n").toString();
    }

    @Test
    void parsesRecipientsSenderAndAttachments() {
        RocketEmlMessage message = parse(multipart(2, 10));

        assertEquals(3, message.getRecipients().size());
        assertTrue(message.getRecipients().contains(new RocketEmlAddress("Bob", "bob@test.local")));
        assertEquals(2, message.getAttachments().size());
        assertEquals(0, message.getSkippedAttachments());
        assertTrue(message.getSender().contains("a@x.org"));
    }

    @Test
    void mentionsAreNeutralized() {
        String text = parse(multipart(0, 0)).makeMessage();

        assertFalse(text.contains("@all"), text);
        assertFalse(text.contains("@here"), text);
        assertFalse(text.contains("@bob"), text);
        assertTrue(text.contains("@​all"), text);
    }

    @Test
    void tooManyAttachmentsAreSkipped() {
        RocketEmlMessage message = parse(multipart(12, 10));

        assertEquals(10, message.getAttachments().size());
        assertEquals(2, message.getSkippedAttachments());
    }

    @Test
    void oversizedAttachmentIsSkipped() {
        RocketEmlMessage message = parse(multipart(1, 11 * 1024 * 1024));

        assertEquals(0, message.getAttachments().size());
        assertEquals(1, message.getSkippedAttachments());
    }

    @Test
    void htmlOnlyMailIsConvertedToMarkdown() {
        RocketEmlMessage message = parse("""
                From: a@x.org\r
                To: bob@test.local\r
                Subject: Html\r
                Content-Type: text/html; charset=utf-8\r
                \r
                <p>Hello <b>world</b></p>\r
                """);

        assertTrue(message.makeMessage().contains("**world**"), message.makeMessage());
    }
}
