package rocketgateway.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigFileParserTest {
    @TempDir
    Path dir;

    private static final String BASE = """
            {"smtp": {"smtp_port": %s, "require_auth": true, "smtp_username": "u", "smtp_password": "p"},
             "rocketchat": {"rocketchat_url": "%s", "bot_username": "b", "bot_password": "p"},
             "spam": {"get_spam": true, "spam_channel": "spam"},
             "tls": {"enable_tls": %s},
             "email_channels": [{"address": "a@test.local", "channel": "a"}, {"address": null, "channel": "x"}]}
            """;

    private ConfigFileParser parse(String port, String url, String tls) throws Exception {
        Path file = dir.resolve("c.json");
        Files.writeString(file, BASE.formatted(port, url, tls));
        ConfigFileParser parser = new ConfigFileParser(file.toString());
        parser.parse();
        return parser;
    }

    @Test
    void shippedSampleConfigIsValid() {
        ConfigFileParser parser = new ConfigFileParser("src/main/dist/rocketgateway.json");

        assertFalse(parser.parse(), parser.getErrorMessage());
        assertTrue(parser.getRequireAuth(), "the sample must not ship with authentication disabled");
        assertArrayEquals(new String[]{"TLSv1.3", "TLSv1.2"}, parser.getTLSProtocols());
    }

    @Test
    void validConfigIsParsed() throws Exception {
        ConfigFileParser parser = parse("1500", "https://chat.example.org", "false");

        assertNull(parser.getErrorMessage());
        assertEquals(1500, parser.getPort());
        assertEquals(Map.of("a@test.local", "a"), parser.getEmailChannels());
        assertEquals("", parser.getPrivateKeyPassword());
    }

    @Test
    void invalidPortIsRejected() throws Exception {
        assertNotNull(parse("70000", "http://localhost:3000", "false").getErrorMessage());
        assertNotNull(parse("0", "http://localhost:3000", "false").getErrorMessage());
    }

    @Test
    void invalidUrlIsRejected() throws Exception {
        assertNotNull(parse("1500", "ftp://x", "false").getErrorMessage());
        assertNotNull(parse("1500", "", "false").getErrorMessage());
    }

    @Test
    void tlsWithoutFilesIsRejected() throws Exception {
        assertNotNull(parse("1500", "http://localhost:3000", "true").getErrorMessage());
    }

    @Test
    void plainHttpToRemoteHostWarns() throws Exception {
        assertTrue(parse("1500", "http://chat.example.org", "false").getWarnings().stream()
                .anyMatch(w -> w.contains("plain HTTP")));
        assertTrue(parse("1500", "http://localhost:3000", "false").getWarnings().stream()
                .noneMatch(w -> w.contains("plain HTTP")));
    }

    @Test
    void brokenReloadKeepsOldConfiguration() throws Exception {
        ConfigFileParser parser = parse("1500", "https://chat.example.org", "false");
        Files.writeString(dir.resolve("c.json"), "{ not json");

        assertTrue(parser.parse());
        assertEquals(1500, parser.getPort());
        assertEquals(Map.of("a@test.local", "a"), parser.getEmailChannels());
    }

    @Test
    void missingSectionIsRejected() throws Exception {
        Path file = dir.resolve("m.json");
        Files.writeString(file, "{\"smtp\": {\"smtp_port\": 25}}");
        ConfigFileParser parser = new ConfigFileParser(file.toString());

        assertTrue(parser.parse());
        assertTrue(parser.getErrorMessage().contains("rocketchat"));
    }
}
