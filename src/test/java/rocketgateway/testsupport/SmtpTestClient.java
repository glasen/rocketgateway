package rocketgateway.testsupport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Tiny raw-socket SMTP client for tests. */
public final class SmtpTestClient {
    private SmtpTestClient() {
    }

    public static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    /**
     * Sends one mail. Returns the reply of the first step which did not succeed, or the reply to the
     * end of the DATA section. Pass null credentials to skip authentication.
     */
    public static String send(int port, String user, String password, String from, String to, String rawMessage)
            throws IOException {
        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            socket.setSoTimeout(15_000);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            Writer out = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);

            String reply = read(in);
            if (!reply.startsWith("220")) {
                return reply;
            }

            reply = command(in, out, "EHLO test.local");
            if (!reply.startsWith("250")) {
                return reply;
            }

            if (user != null) {
                String token = Base64.getEncoder().encodeToString(("\0" + user + "\0" + password).getBytes(StandardCharsets.UTF_8));
                reply = command(in, out, "AUTH PLAIN " + token);
                if (!reply.startsWith("235")) {
                    return reply;
                }
            }

            reply = command(in, out, "MAIL FROM:<" + from + ">");
            if (!reply.startsWith("250")) {
                return reply;
            }

            reply = command(in, out, "RCPT TO:<" + to + ">");
            if (!reply.startsWith("250")) {
                return reply;
            }

            reply = command(in, out, "DATA");
            if (!reply.startsWith("354")) {
                return reply;
            }

            out.write(rawMessage.replace("\r\n.", "\r\n..") + "\r\n.\r\n");
            out.flush();
            reply = read(in);

            command(in, out, "QUIT");
            return reply;
        }
    }

    private static String command(BufferedReader in, Writer out, String command) throws IOException {
        out.write(command + "\r\n");
        out.flush();
        return read(in);
    }

    /** Reads a (possibly multi-line) reply and returns its last line. */
    private static String read(BufferedReader in) throws IOException {
        String line;
        do {
            line = in.readLine();
            if (line == null) {
                throw new IOException("Connection closed");
            }
        } while (line.length() > 3 && line.charAt(3) == '-');
        return line;
    }
}
