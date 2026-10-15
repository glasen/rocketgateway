package rocketgateway.rocketchat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HelpersTest {
    @Test
    void escapeMentionsHandlesNullAndPlainText() {
        assertEquals("", Helpers.escapeMentions(null));
        assertEquals("plain", Helpers.escapeMentions("plain"));
    }

    @Test
    void escapeMentionsBreaksEveryMention() {
        assertEquals("@​all and @​here", Helpers.escapeMentions("@all and @here"));
    }

    @Test
    void safeStringUsesDefault() {
        assertEquals("x", Helpers.safeString(null, "x"));
        assertEquals("v", Helpers.safeString("v", "x"));
    }
}
