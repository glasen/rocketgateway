package rocketgateway.rocketchat;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rocketgateway.testsupport.FakeRocketChat;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static rocketgateway.testsupport.FakeRocketChat.*;

class RocketChatAPITest {
    private FakeRocketChat chat;

    @BeforeEach
    void start() throws Exception {
        chat = new FakeRocketChat().addChannel("general").addChannel("ops");
    }

    @AfterEach
    void stop() {
        chat.close();
    }

    private RocketChatAPI api(Map<String, String> mappings) {
        return new RocketChatAPI(BOT_USER, BOT_PASSWORD, chat.url(), mappings);
    }

    private RocketChatAPI loggedIn(Map<String, String> mappings) {
        RocketChatAPI api = api(mappings);
        api.login();
        assertTrue(api.getLoginStatus());
        api.init();
        return api;
    }

    @Test
    void loginSucceedsWithCorrectCredentials() {
        RocketChatAPI api = api(Map.of());
        api.login();

        assertTrue(api.getLoginStatus());
    }

    @Test
    void loginFailsWithWrongPassword() {
        RocketChatAPI api = new RocketChatAPI(BOT_USER, "wrong", chat.url(), Map.of());
        api.login();

        assertFalse(api.getLoginStatus());
    }

    @Test
    void logoutClearsLoginStatusAndSendsEmptyJsonBody() throws Exception {
        RocketChatAPI api = loggedIn(Map.of());
        api.logout();

        assertFalse(api.getLoginStatus());
        assertEquals("{}", chat.requestsTo("/api/v1/logout").get(0).bodyString());
    }

    @Test
    void configuredBareNameIsPostedToChannelByName() {
        RocketChatAPI api = loggedIn(Map.of("a@test.local", "general"));

        assertEquals("rid-general", api.sendMessageToEmailAddress("hi", "a@test.local", "alias"));

        JsonObject post = chat.posts().get(0);
        assertEquals("#general", post.get("channel").getAsString());
        assertFalse(post.has("roomId"), "roomId must not be sent");
        assertEquals("hi", post.get("text").getAsString());
        assertEquals("alias", post.get("alias").getAsString());
    }

    @Test
    void explicitPrefixesAreKept() {
        chat.addUser("bob", null);
        RocketChatAPI api = loggedIn(Map.of("a@test.local", "@bob", "b@test.local", "#ops"));

        assertEquals("dm-bob", api.sendMessageToEmailAddress("x", "a@test.local", null));
        assertEquals("rid-ops", api.sendMessageToEmailAddress("x", "b@test.local", null));
        assertFalse(chat.posts().get(0).has("alias"));
    }

    @Test
    void emailLookupIsCaseInsensitive() {
        RocketChatAPI api = loggedIn(Map.of("A@Test.Local", "general"));

        assertEquals("rid-general", api.sendMessageToEmailAddress("x", "a@TEST.local", null));
    }

    @Test
    void bareNameOfKnownUserBecomesDirectMessage() {
        chat.addUser("bob", "bob@test.local");
        RocketChatAPI api = loggedIn(Map.of("a@test.local", "bob"));

        assertEquals("dm-bob", api.sendMessageToEmailAddress("x", "a@test.local", null));
        assertEquals("@bob", chat.posts().get(0).get("channel").getAsString());
    }

    @Test
    void usersAreDiscoveredByEmailAcrossAllPages() {
        for (int i = 1; i <= 5; i++) {
            chat.addUser("u" + i, "u" + i + "@test.local");
        }
        chat.pageCap(2);
        RocketChatAPI api = loggedIn(Map.of());

        assertEquals("dm-u5", api.sendMessageToEmailAddress("x", "u5@test.local", null));
        assertEquals(List.of("0", "2", "4"),
                chat.requestsTo("/api/v1/users.list").stream().map(r -> r.query().get("offset")).toList());
    }

    @Test
    void configuredMappingWinsOverDiscoveredOne() {
        chat.addUser("bob", "bob@test.local");
        RocketChatAPI api = loggedIn(Map.of("bob@test.local", "ops"));

        assertEquals("rid-ops", api.sendMessageToEmailAddress("x", "bob@test.local", null));
    }

    @Test
    void unknownAddressesCannotTriggerRepeatedRefreshes() {
        RocketChatAPI api = loggedIn(Map.of());
        int before = chat.requestsTo("/api/v1/users.list").size();

        for (int i = 0; i < 20; i++) {
            assertNull(api.sendMessageToEmailAddress("x", "nobody" + i + "@test.local", null));
        }

        assertEquals(before, chat.requestsTo("/api/v1/users.list").size());
        assertTrue(chat.posts().isEmpty());
    }

    @Test
    void unknownChannelReturnsNull() {
        RocketChatAPI api = loggedIn(Map.of("a@test.local", "does-not-exist"));

        assertNull(api.sendMessageToEmailAddress("x", "a@test.local", null));
        assertNull(api.sendMessageToChannel("x", null, null));
        assertNull(api.sendMessageToChannel("x", " ", null));
    }

    @Test
    void authenticationHeadersAreSent() {
        RocketChatAPI api = loggedIn(Map.of("a@test.local", "general"));
        api.sendMessageToEmailAddress("x", "a@test.local", null);

        FakeRocketChat.Request post = chat.requestsTo("/api/v1/chat.postMessage").get(0);
        assertEquals(TOKEN, post.headers().get("x-auth-token"));
        assertEquals(USER_ID, post.headers().get("x-user-id"));
    }

    @Test
    void uploadUsesMediaThenConfirm() {
        RocketChatAPI api = loggedIn(Map.of());
        api.uploadFileToRoom("payload".getBytes(), "B", "rid-general");

        assertEquals(1, chat.uploads().size());
        assertEquals("rid-general", chat.uploads().get(0).roomId());
        assertEquals("payload", chat.uploads().get(0).body());
        assertEquals(List.of(chat.uploads().get(0).fileId()), chat.confirmedFileIds());
        assertTrue(chat.requestsTo("/api/v1/rooms.media/rid-general").get(0).headers().get("content-type")
                .startsWith("multipart/form-data; boundary=B"));
        assertTrue(chat.requests().stream().noneMatch(r -> r.path().startsWith("/api/v1/rooms.upload")));
    }

    @Test
    void failedConfirmIsReported() {
        chat.failConfirm(true);
        RocketChatAPI api = loggedIn(Map.of());

        assertThrows(RuntimeException.class, () -> api.uploadFileToRoom(new byte[]{1}, "B", "rid-general"));
    }

    @Test
    void updatedMappingsReplaceOldOnes() {
        RocketChatAPI api = loggedIn(Map.of("a@test.local", "general"));
        api.updateEmailChannels(Map.of("a@test.local", "ops"));

        assertEquals("rid-ops", api.sendMessageToEmailAddress("x", "a@test.local", null));
    }
}
