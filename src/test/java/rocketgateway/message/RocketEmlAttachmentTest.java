package rocketgateway.message;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class RocketEmlAttachmentTest {
    @Test
    void uploadDataContainsBoundaryHeadersAndContent() throws Exception {
        RocketEmlAttachment attachment = new RocketEmlAttachment("a.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));
        String data = new String(attachment.getUploadData("B"), StandardCharsets.UTF_8);

        assertTrue(data.contains("--B\r\n"));
        assertTrue(data.contains("Content-Disposition: form-data; name=\"file\"; filename=\"a.txt\"\r\n"));
        assertTrue(data.contains("Content-Type: text/plain\r\n\r\nhello"));
        assertTrue(data.endsWith("\r\n--B--"));
    }

    @Test
    void filenameCannotInjectHeaders() throws Exception {
        RocketEmlAttachment attachment = new RocketEmlAttachment("a\".txt\r\nX-Evil: 1\r\n\r\n", "text/plain", new byte[]{1});
        String data = new String(attachment.getUploadData("B"), StandardCharsets.UTF_8);
        String headers = data.substring(0, data.indexOf("\r\n\r\n"));

        assertFalse(headers.contains("\nX-Evil"), headers);
        assertEquals(1, headers.split("Content-Disposition", -1).length - 1);
        assertEquals(3, headers.split("\r\n", -1).length - 1, "boundary line, disposition, type: " + headers);
    }

    @Test
    void mimeTypeCannotInjectHeaders() throws Exception {
        RocketEmlAttachment attachment = new RocketEmlAttachment("a.txt", "text/plain\r\nX-Evil: 1", new byte[]{1});
        String data = new String(attachment.getUploadData("B"), StandardCharsets.UTF_8);

        assertFalse(data.contains("X-Evil"));
        assertTrue(data.contains("Content-Type: application/octet-stream"));
    }

    @Test
    void mimeTypeParametersAreDropped() throws Exception {
        RocketEmlAttachment attachment = new RocketEmlAttachment("a.txt", "text/plain; charset=utf-8", new byte[]{1});

        assertTrue(new String(attachment.getUploadData("B"), StandardCharsets.UTF_8).contains("Content-Type: text/plain\r\n"));
    }

    @Test
    void emptyFilenameGetsDefault() throws Exception {
        RocketEmlAttachment attachment = new RocketEmlAttachment(" \r\n ", "text/plain", new byte[]{1});

        assertTrue(new String(attachment.getUploadData("B"), StandardCharsets.UTF_8).contains("filename=\"unknown.dat\""));
    }

    @Test
    void boundariesAreUnique() {
        assertNotEquals(RocketEmlAttachment.newBoundary(), RocketEmlAttachment.newBoundary());
    }
}
