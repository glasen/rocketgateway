package rocketgateway.smtp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rocketgateway.rocketchat.RocketChatAPI;
import rocketgateway.testsupport.FakeRocketChat;
import rocketgateway.testsupport.SmtpTestClient;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static rocketgateway.testsupport.FakeRocketChat.BOT_PASSWORD;
import static rocketgateway.testsupport.FakeRocketChat.BOT_USER;

/** Starts the real SMTP server and a fake RocketChat and sends real mails through the whole chain. */
class GatewayEndToEndTest {
    private FakeRocketChat chat;
    private RocketSMTPServer smtp;
    private int port;

    @BeforeEach
    void setUp() throws Exception {
        chat = new FakeRocketChat().addChannel("spam");
        for (int i = 0; i < 8; i++) {
            chat.addChannel("c" + i);
        }
    }

    @AfterEach
    void tearDown() {
        if (smtp != null) {
            smtp.stop();
        }
        chat.close();
    }

    private void startGateway(boolean requireAuth, boolean spam) throws Exception {
        Map<String, String> mappings = new HashMap<>();
        for (int i = 0; i < 8; i++) {
            mappings.put("c" + i + "@test.local", "c" + i);
        }

        RocketChatAPI bot = new RocketChatAPI(BOT_USER, BOT_PASSWORD, chat.url(), mappings);
        bot.login();
        assertTrue(bot.getLoginStatus());
        bot.init();

        port = SmtpTestClient.freePort();
        smtp = new RocketSMTPServer(new SMTPConfig(port, "smtpuser", "smtppass", requireAuth, false), bot, spam, "spam");
        smtp.start();
    }

    private static String mail(String to, String text, String attachmentContent) {
        String raw = "From: sender@x.org\r\nTo: " + to + "\r\nSubject: Test @all\r\nMIME-Version: 1.0\r\n"
                + "Content-Type: multipart/mixed; boundary=\"XX\"\r\n\r\n"
                + "--XX\r\nContent-Type: text/plain; charset=utf-8\r\n\r\n" + text + "\r\n";

        if (attachmentContent != null) {
            raw += "--XX\r\nContent-Type: text/plain; name=\"f.txt\"\r\n"
                    + "Content-Disposition: attachment; filename=\"f.txt\"\r\nContent-Transfer-Encoding: base64\r\n\r\n"
                    + Base64.getMimeEncoder().encodeToString(attachmentContent.getBytes()) + "\r\n";
        }

        return raw + "--XX--\r\n";
    }

    @Test
    void mailIsDeliveredToMappedChannelWithAttachment() throws Exception {
        startGateway(false, false);

        String reply = SmtpTestClient.send(port, null, null, "sender@x.org", "c1@test.local",
                mail("c1@test.local", "hello world", "file-content"));

        assertTrue(reply.startsWith("250"), reply);
        assertEquals(1, chat.posts().size());
        assertEquals("#c1", chat.posts().get(0).get("channel").getAsString());
        assertTrue(chat.posts().get(0).get("text").getAsString().contains("hello world"));
        assertEquals(1, chat.uploads().size());
        assertEquals("rid-c1", chat.uploads().get(0).roomId());
        assertTrue(chat.uploads().get(0).body().contains("file-content"));
        assertEquals(1, chat.confirmedFileIds().size());
    }

    @Test
    void mentionsInMailAreNeutralizedInChat() throws Exception {
        startGateway(false, false);
        SmtpTestClient.send(port, null, null, "sender@x.org", "c0@test.local", mail("c0@test.local", "@here", null));

        String text = chat.posts().get(0).get("text").getAsString();
        assertFalse(text.contains("@all") || text.contains("@here"), text);
        assertTrue(chat.posts().get(0).get("alias").getAsString().contains("​"));
    }

    @Test
    void concurrentMailsKeepAttachmentsInTheirOwnRoom() throws Exception {
        startGateway(false, false);
        int mails = 8;
        ExecutorService pool = Executors.newFixedThreadPool(mails);
        List<Future<String>> replies = new ArrayList<>();

        try {
            for (int round = 0; round < 3; round++) {
                for (int i = 0; i < mails; i++) {
                    String name = "c" + i;
                    replies.add(pool.submit(() -> SmtpTestClient.send(port, null, null, "sender@x.org",
                            name + "@test.local", mail(name + "@test.local", "text", "payload-" + name))));
                }
            }

            for (Future<String> reply : replies) {
                assertTrue(reply.get().startsWith("250"), reply.get());
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(mails * 3, chat.uploads().size());
        Pattern pattern = Pattern.compile("payload-(c\\d)");
        for (FakeRocketChat.Upload upload : chat.uploads()) {
            Matcher matcher = pattern.matcher(upload.body());
            assertTrue(matcher.find(), upload.body());
            assertEquals("rid-" + matcher.group(1), upload.roomId(), "attachment ended up in the wrong room");
        }
    }

    @Test
    void mailToUnknownAddressGoesToSpamChannelWhenEnabled() throws Exception {
        startGateway(false, true);

        String reply = SmtpTestClient.send(port, null, null, "sender@x.org", "nobody@test.local",
                mail("nobody@test.local", "spam text", "ignored"));

        assertTrue(reply.startsWith("250"), reply);
        assertEquals(1, chat.posts().size());
        assertEquals("#spam", chat.posts().get(0).get("channel").getAsString());
        assertTrue(chat.uploads().isEmpty());
    }

    @Test
    void mailToUnknownAddressIsDroppedWhenSpamIsDisabled() throws Exception {
        startGateway(false, false);
        SmtpTestClient.send(port, null, null, "sender@x.org", "nobody@test.local", mail("nobody@test.local", "x", null));

        assertTrue(chat.posts().isEmpty());
    }

    @Test
    void authenticationIsEnforced() throws Exception {
        startGateway(true, false);
        String message = mail("c2@test.local", "secret", null);

        String anonymous = SmtpTestClient.send(port, null, null, "sender@x.org", "c2@test.local", message);
        String wrong = SmtpTestClient.send(port, "smtpuser", "bad", "sender@x.org", "c2@test.local", message);
        String good = SmtpTestClient.send(port, "smtpuser", "smtppass", "sender@x.org", "c2@test.local", message);

        assertTrue(anonymous.startsWith("530"), anonymous);
        assertTrue(wrong.startsWith("535"), wrong);
        assertTrue(good.startsWith("250"), good);
        assertEquals(1, chat.posts().size());
    }

    @Test
    void oversizedMailIsRejected() throws Exception {
        startGateway(false, false);
        String huge = "a".repeat(30 * 1024 * 1024);

        String reply = SmtpTestClient.send(port, null, null, "sender@x.org", "c0@test.local", mail("c0@test.local", huge, null));

        assertTrue(reply.startsWith("552"), reply);
        assertTrue(chat.posts().isEmpty());
    }
}
