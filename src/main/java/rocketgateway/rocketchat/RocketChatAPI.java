package rocketgateway.rocketchat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RocketChatAPI {
    // Minimum time between two server-side user refreshes triggered by unknown addresses.
    private static final long REFRESH_INTERVAL_MS = 30_000;
    private static final int USERS_PAGE_SIZE = 100;

    private final LoginData loginData;
    private final String username;
    private final String serverURL;
    private boolean loginStatus;

    // Mappings from the config file (e-mail-address -> channel or user). They take precedence.
    private volatile Map<String, String> configuredTargets;
    // Mappings discovered on the server (e-mail-address -> "@username").
    private final Map<String, String> discoveredTargets;
    private final Set<String> knownUsernames;
    private long lastRefresh;

    /**
     * Rudimentary RocketChat-API class. Contains only methods which are needed to send messages and attachments.
     *
     * @param username       String Username of bot-user
     * @param password       String Password of bot-user
     * @param serverURL      String URL of RocketChat-server
     * @param emailsChannels Map with e-mail-address to channel mapping. See ini-file for more details.
     */
    public RocketChatAPI(String username, String password, String serverURL, Map<String, String> emailsChannels) {
        this.username = username;
        this.loginData = new LoginData(username, password);
        this.loginStatus = false;
        this.serverURL = serverURL;

        // These maps are shared between the SMTP threads and the config-watcher thread.
        this.configuredTargets = normalizeConfig(emailsChannels);
        this.discoveredTargets = new ConcurrentHashMap<>();
        this.knownUsernames = ConcurrentHashMap.newKeySet();
        this.lastRefresh = 0;
    }

    /**
     * Log into RocketChat.
     */
    public void login() {
        try (RocketConnection rocketConnection = new RocketConnection(this.serverURL)) {
            rocketConnection.open(HTTPMethods.POST, getApiPath("login"), null);
            rocketConnection.writeJsonData(this.loginData.get());
            JsonObject json = rocketConnection.getResponseJSON();
            boolean status = rocketConnection.getStatus();

            if (status && json.get("status").getAsString().equals("success")) {
                JsonObject data = json.getAsJsonObject("data");

                String authToken = data.get("authToken").getAsString();
                String userId = data.get("userId").getAsString();
                this.loginData.setTokens(authToken, userId);
                this.loginStatus = true;
            }
        } catch (Exception e) {
            this.loginStatus = false;
        }
    }

    /**
     * Initializes connection by loading the users (and their e-mail-addresses) from the server.
     */
    public void init() {
        String userMessage = String.format("-> Messages will be sent by user \"%s\"", this.username);
        System.out.println(userMessage);
        updateUsers();
    }

    /**
     * Send a message to a specific e-mail-address.
     *
     * @param message String message to send
     * @param address String E-mail-address of the user to send the message.
     * @param alias   String sender alias name.
     * @return String Internal id of the room the message was sent to, or null if it could not be sent.
     */
    public String sendMessageToEmailAddress(String message, String address, String alias) {
        String key = address.toLowerCase(Locale.ROOT);
        String target = lookupTarget(key);

        // Only hit the server to refresh the user data when the address is not yet known.
        if (target == null) {
            refreshIfAllowed();
            target = lookupTarget(key);
        }

        if (target == null) {
            return null;
        }

        return postMessage(message, target, alias);
    }

    /**
     * Send a message to a specific channel (or user).
     *
     * @param message     String message to send
     * @param channelName String Name of the channel. A leading "@" selects a user, "#" a channel.
     * @param alias       String sender alias name.
     * @return String Internal id of the room the message was sent to, or null if it could not be sent.
     */
    public String sendMessageToChannel(String message, String channelName, String alias) {
        if (channelName == null || channelName.isBlank()) {
            return null;
        }

        return postMessage(message, toTarget(channelName.strip()), alias);
    }

    /**
     * Post a message with chat.postMessage. The "channel" parameter accepts "#channelname" for channels and
     * private groups and "@username" for direct messages, so no room-id lookups are needed. The server returns
     * the room-id (message.rid) of the room which really received the message.
     */
    private String postMessage(String message, String target, String alias) {
        try (RocketConnection rocketConnection = new RocketConnection(this.serverURL)) {
            rocketConnection.open(HTTPMethods.POST, getApiPath("chat.postMessage"), null);
            rocketConnection.setAuthHeader(this.loginData);

            JsonObject jsonData = new JsonObject();
            jsonData.addProperty("channel", target);
            jsonData.addProperty("text", message);

            if (!Helpers.safeString(alias).isEmpty()) {
                jsonData.addProperty("alias", alias);
            }

            rocketConnection.writeJsonData(jsonData.toString());

            JsonObject json = rocketConnection.getResponseJSON();

            if (checkStatus(rocketConnection, json)) {
                try {
                    // Keep the room-id local to this call. Concurrent messages must never share it.
                    return json.getAsJsonObject("message").get("rid").getAsString();
                } catch (Exception e) {
                    return null;
                }
            }

            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Upload a file to a specific room with the two-step rooms.media/rooms.mediaConfirm flow. The old
     * rooms.upload endpoint was removed in RocketChat 8.0.0.
     *
     * @param outData  byte[] Attachment data
     * @param boundary String Multipart boundary used to build outData
     * @param roomId   String Internal id of room to upload data
     */
    public void uploadFileToRoom(byte[] outData, String boundary, String roomId) {
        if (roomId == null || roomId.isEmpty()) {
            return;
        }

        try {
            String fileId;

            try (RocketConnection rocketConnection = new RocketConnection(this.serverURL)) {
                rocketConnection.open(HTTPMethods.POST, getApiPath("rooms.media") + "/" + roomId, boundary);
                rocketConnection.setAuthHeader(this.loginData);
                rocketConnection.writeBinaryData(outData);
                JsonObject json = rocketConnection.getResponseJSON();

                if (!checkStatus(rocketConnection, json)) {
                    throw new IOException("rooms.media failed");
                }

                fileId = json.getAsJsonObject("file").get("_id").getAsString();
            }

            try (RocketConnection rocketConnection = new RocketConnection(this.serverURL)) {
                rocketConnection.open(HTTPMethods.POST,
                        getApiPath("rooms.mediaConfirm") + "/" + roomId + "/" + fileId, null);
                rocketConnection.setAuthHeader(this.loginData);
                rocketConnection.writeJsonData("{}");
                JsonObject json = rocketConnection.getResponseJSON();

                if (!checkStatus(rocketConnection, json)) {
                    throw new IOException("rooms.mediaConfirm failed");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Log out RocketChat connection
     *
     * @throws IOException Thrown when something went wrong when logging out
     */
    public void logout() throws IOException {
        try (RocketConnection rocketConnection = new RocketConnection(this.serverURL)) {
            if (this.loginStatus) {
                rocketConnection.open(HTTPMethods.POST, getApiPath("logout"), null);
                rocketConnection.setAuthHeader(this.loginData);

                /* The logout endpoint takes no parameters, but the request still declares
                   Content-Type: application/json. Sending an empty body makes RocketChat's JSON body
                   parser reject the request, so write a valid empty JSON object.
                */
                rocketConnection.writeJsonData("{}");

                boolean status = rocketConnection.getStatus();

                String statusString;

                try {
                    JsonObject json = rocketConnection.getResponseJSON();
                    statusString = json.get("status").getAsString();
                } catch (Exception e) {
                    // Workaround for missing return-value of RocketChat. Newer versions don't return
                    // a JSON on logout. Always assume that the logout was successful.

                    statusString = "success";
                }

                if (status && statusString.equals("success")) {
                    this.loginData.setTokens("", "");
                    this.loginStatus = false;
                }
            }
        }

    }

    /**
     * Get login status. True if login was successful.
     *
     * @return boolean Status of login.
     */
    public boolean getLoginStatus() {
        return loginStatus;
    }

    /**
     * Updates the e-mail-address mappings from the config file.
     *
     * @param newEmailChannels Map with e-mail-address/channel-name
     */
    public void updateEmailChannels(Map<String, String> newEmailChannels) {
        this.configuredTargets = normalizeConfig(newEmailChannels);
        updateUsers();
    }

    private static Map<String, String> normalizeConfig(Map<String, String> source) {
        Map<String, String> result = new ConcurrentHashMap<>();

        for (Map.Entry<String, String> entry : source.entrySet()) {
            result.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }

        return result;
    }

    /**
     * Looks up the target for an e-mail-address. Configured mappings win over discovered ones.
     */
    private String lookupTarget(String key) {
        String configured = this.configuredTargets.get(key);

        if (configured != null) {
            return toTarget(configured.strip());
        }

        return this.discoveredTargets.get(key);
    }

    /**
     * Converts a configured name into a value for the "channel" parameter. Names with a "#" or "@" prefix are
     * used as they are. Known usernames become direct messages, everything else is a channel.
     */
    private String toTarget(String name) {
        if (name.startsWith("#") || name.startsWith("@")) {
            return name;
        }

        return (this.knownUsernames.contains(name) ? "@" : "#") + name;
    }

    /**
     * Refreshes the user data, but at most once per REFRESH_INTERVAL_MS. Unknown addresses are
     * attacker-controlled input, so they must not be able to trigger unlimited server requests.
     */
    private synchronized void refreshIfAllowed() {
        if (System.currentTimeMillis() - this.lastRefresh >= REFRESH_INTERVAL_MS) {
            try {
                updateUsers();
            } catch (RuntimeException e) {
                System.err.println("Couldn't refresh RocketChat data: " + e.getMessage());
            }
        }
    }

    /**
     * Reads all users page by page (users.list) to learn the usernames and their e-mail-addresses. E-mail
     * addresses are only returned if the bot-user has the permissions for it (e.g. the admin role).
     */
    private synchronized void updateUsers() {
        this.lastRefresh = System.currentTimeMillis();
        int offset = 0;

        while (true) {
            int received = 0;
            int total;

            try (RocketConnection rocketConnection = new RocketConnection(this.serverURL)) {
                rocketConnection.open(HTTPMethods.GET,
                        getApiPath("users.list") + "?count=" + USERS_PAGE_SIZE + "&offset=" + offset, null);
                rocketConnection.setAuthHeader(this.loginData);
                JsonObject json = rocketConnection.getResponseJSON();

                if (!checkStatus(rocketConnection, json)) {
                    return;
                }

                JsonArray users = json.getAsJsonArray("users");
                total = json.has("total") ? json.get("total").getAsInt() : 0;

                for (JsonElement user : users) {
                    received++;

                    if (user.isJsonObject()) {
                        readUser(user.getAsJsonObject());
                    }
                }
            } catch (IOException e) {
                return;
            }

            // The server may cap "count", so continue with the number of users really received.
            offset += received;

            if (received == 0 || offset >= total) {
                return;
            }
        }
    }

    private void readUser(JsonObject userJson) {
        JsonElement usernameElement = userJson.get("username");

        if (usernameElement == null || usernameElement.isJsonNull() || usernameElement.getAsString().isEmpty()) {
            return;
        }

        String name = usernameElement.getAsString();
        this.knownUsernames.add(name);

        JsonElement emails = userJson.get("emails");

        if (emails != null && emails.isJsonArray()) {
            for (JsonElement email : emails.getAsJsonArray()) {
                if (email.isJsonObject() && email.getAsJsonObject().has("address")) {
                    String address = email.getAsJsonObject().get("address").getAsString();
                    this.discoveredTargets.put(address.toLowerCase(Locale.ROOT), "@" + name);
                }
            }
        }
    }

    /**
     * Generate full REST-API-path from provided end-point
     *
     * @param endPoint String name of end point
     * @return String Full REST-API-path
     */
    private String getApiPath(String endPoint) {
        return "/api/v1/" + endPoint;
    }

    private boolean checkStatus(RocketConnection rocketConnection, JsonObject jsonObject) {
        boolean status = rocketConnection.getStatus();
        JsonElement success = jsonObject.get("success");

        if (success != null) {
            return status && success.getAsBoolean();
        }

        return false;
    }
}
