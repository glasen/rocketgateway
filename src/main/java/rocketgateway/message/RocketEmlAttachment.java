package rocketgateway.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class RocketEmlAttachment {

    private final String filename;
    private final String mimeType;
    private final byte[] content;

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
     * Generates a unique multipart boundary for a single upload. Using a fresh random boundary per
     * upload avoids any chance of the boundary marker colliding with the attachment content.
     * @return String with a unique multipart boundary
     */
    public static String newBoundary() {
        return "envelope-" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Generates byte array with boundary envelope. This is needed because RocketChat only accepts this type
     * of data as an attachment.
     * @param boundary String Multipart boundary to delimit the part. Must match the Content-Type header.
     * @return Byte array with RocketChat attachment
     * @throws IOException Thrown when anything goes wrong. Normally this should not happen!
     */
    public byte[] getUploadData(String boundary) throws IOException {
        byte[] boundaryBytes = ("\r\n--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] endBoundaryBytes = ("\r\n--" + boundary + "--").getBytes(StandardCharsets.UTF_8);

        try (ByteArrayOutputStream stream = new ByteArrayOutputStream()) {
            String headerTemplate = """
                Content-Disposition: form-data; name="file"; filename="%s"\r
                Content-Type: %s\r
                \r
                """;
            stream.write(boundaryBytes);
            String header = String.format(headerTemplate, filename, mimeType);
            byte[] headerBytes = header.getBytes(StandardCharsets.UTF_8);
            stream.write(headerBytes);
            stream.write(content);
            stream.write(endBoundaryBytes);
            stream.flush();

            return stream.toByteArray();
        }
    }
}
