package rocketgateway.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class RocketEmlAttachment {

    private final String filename;
    private final String mimeType;
    private final byte[] content;
    private final byte[] boundaryBytes = ("\r\n--envelope-0815\r\n").getBytes();
    private final byte[] endBoundaryBytes = ("\r\n--envelope-0815--").getBytes();

    /**
     * Data class for attachments
     * @param filename String with filename of the attachment
     * @param mimeType String with mimetype e.g. "application/pdf" or "image/jpg"
     * @param content Byte array with the binary data of the attachment
     */
    public RocketEmlAttachment(String filename, String mimeType, byte[] content) {
        this.filename = filename;
        this.mimeType = mimeType;
        this.content = content; }

    /**
     * Generates byte array with boundary envelope. This is needed because RocketChat only accepts this type
     * of data as an attachment.
     * @return Byte array with RocketChat attachment
     * @throws IOException Thrown when anything goes wrong. Normally this should not happen!
     */
    public byte[] getUploadData() throws IOException {
        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            String headerTemplate = """
                Content-Disposition: form-data; name="file"; filename="%s"\r
                Content-Type: %s\r
                \r
                """;
            stream.write(boundaryBytes);
            String header = String.format(headerTemplate, filename, mimeType);
            byte[] headerBytes = header.getBytes();
            stream.write(headerBytes);
            stream.write(content);
            stream.write(endBoundaryBytes);
            stream.flush();

            return stream.toByteArray();
        }
    }
}
