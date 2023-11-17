package rocketgateway.rocketchat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RocketChatAPI {
    private final LoginData loginData;
    private final String username;
    private final String serverURL;
    private boolean loginStatus;
    private final Map<String, String> channelMap;
    private final Map<String, String> eMailUserMap;
    private String lastRoomId;

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

        this.channelMap = new HashMap<>();
        this.eMailUserMap = new HashMap<>(emailsChannels);

        this.lastRoomId = "";
    }

    /**
     * Log into RocketChat.
     */
    public void login() {
        try (RocketConnection rocketConnection = new RocketConnection(this.serverURL)) {
            rocketConnection.open(HTTPMethods.GET, "/api/v1/login", RequestType.JSON);
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
     * Initializes connection by updating e-mail, room and user-data.
     */
    public void init() {
        String userMessage = String.format("-> Messages will be sent by user \"%s\"", this.username);
        System.out.println(userMessage);
        updateAll();
    }

    /**
     * Send a message to a specific e-mail-address.
     *
     * @param message String message to send
     * @param address String E-mail-address of the user to send the message.
     * @param alias   String sender alias name.
     * @return boolean Returns true if the message was successfully sent.
     */
    public boolean sendMessageToEmailAddress(String message, String address, String alias) {
        updateAll();

        // Get channelName for the provided e-mail address.
        String channelName = this.eMailUserMap.get(address);

        if (channelName != null) {
            return sendMessageToChannel(message, channelName, alias);
        } else {
            return false;
        }
    }

    /**
     * Send a message to a specific channel. Basically all rooms are channels.
     *
     * @param message     String message to send
     * @param channelName String Name of channel to send the message.
     * @param alias       String sender alias name.
     * @return boolean Returns true if the message was successfully sent.
     */
    public boolean sendMessageToChannel(String message, String channelName, String alias) {
        updateAll();
        boolean sendStatus = false;

        try (RocketConnection rocketConnection = new RocketConnection(this.serverURL)) {
            rocketConnection.open(HTTPMethods.GET, getApiPath("chat.postMessage"), RequestType.JSON);
            rocketConnection.setAuthHeader(this.loginData);

            JsonObject jsonData = new JsonObject();

            jsonData.addProperty("text", message);

            if (!Optional.ofNullable(alias).orElse("").isEmpty()) {
                jsonData.addProperty("alias", alias);
            }

            /* Get room-id for channel name. All rooms and channels have a unique id. The room-id is the only
               way to send a message by the same method to a user or a channel.
            */
            String roomId = channelMap.get(channelName);

            if (roomId != null) {
                jsonData.addProperty("roomId", roomId);
            }

            rocketConnection.writeJsonData(jsonData.toString());

            JsonObject json = rocketConnection.getResponseJSON();
            boolean status = rocketConnection.getStatus();

            if (status) {
                try {
                    JsonObject messageObj = json.getAsJsonObject("message");
                    lastRoomId = messageObj.get("rid").getAsString();
                } catch (Exception e) {
                    lastRoomId = "";
                }
            } else {
                lastRoomId = "";
            }

            if (status && json.get("success").getAsBoolean()) {
                sendStatus = true;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return sendStatus;
    }

    /**
     * Upload a file to a specific room/channel
     *
     * @param outData byte[] Attachment data
     * @param roomId  String Internal id of room to upload data
     */
    public void uploadFileToRoom(byte[] outData, String roomId) {
        try (RocketConnection rocketConnection = new RocketConnection(this.serverURL)) {
            String apiPath = getApiPath("rooms.upload");

            if (roomId != null) {
                String fullApiPath = apiPath + "/" + roomId;
                rocketConnection.open(HTTPMethods.POST, fullApiPath, RequestType.BINARY);
                rocketConnection.setAuthHeader(this.loginData);
                rocketConnection.writeBinaryData(outData);
                rocketConnection.getResponseJSON();
                rocketConnection.close();
                this.lastRoomId = "";
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
                rocketConnection.open(HTTPMethods.POST, "/api/v1/logout", RequestType.JSON);
                rocketConnection.setAuthHeader(this.loginData);
                JsonObject json = rocketConnection.getResponseJSON();
                boolean status = rocketConnection.getStatus();

                if (status && json.get("status").getAsString().equals("success")) {
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
     * Get id of the room the last message was sent.
     *
     * @return String Internal id of the room
     */
    public String getLastRoomId() {
        return lastRoomId;
    }

    /**
     * Updates e-mail-channel-map
     *
     * @param newEmailChannels Map with e-mail-address/channel-name
     */
    public void updateEmailChannels(Map<String, String> newEmailChannels) {
        this.eMailUserMap.clear();
        this.eMailUserMap.putAll(newEmailChannels);
        updateAll();
    }

    /**
     * Updates channel, room and user information
     */
    private void updateAll() {
        getChannels();
        getRooms();
        getUsers();
    }

    /**
     * Get mappings of channel names to internal room-id.
     */
    public void getChannels() {
        try (RocketConnection rocketConnection = new RocketConnection(this.serverURL)) {
            rocketConnection.open(HTTPMethods.GET, getApiPath("channels.list"), RequestType.JSON);
            rocketConnection.setAuthHeader(this.loginData);
            JsonObject json = rocketConnection.getResponseJSON();

            if (checkStatus(rocketConnection, json)) {
                JsonArray channels = json.getAsJsonArray("channels");
                for (JsonElement channel : channels) {
                    if (channel.isJsonObject()) {
                        String name = channel.getAsJsonObject().get("name").getAsString();
                        String id = channel.getAsJsonObject().get("_id").getAsString();
                        channelMap.put(name, id);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Get mappings of room names to internal room-id.
     */
    public void getRooms() {
        try (RocketConnection rocketConnection = new RocketConnection(this.serverURL)) {
            rocketConnection.open(HTTPMethods.GET, getApiPath("rooms.get"), RequestType.JSON);
            rocketConnection.setAuthHeader(this.loginData);
            JsonObject json = rocketConnection.getResponseJSON();

            if (checkStatus(rocketConnection, json)) {
                JsonArray rooms = json.getAsJsonArray("update");

                for (JsonElement room : rooms) {
                    if (room.isJsonObject()) {
                        JsonElement nameObject = room.getAsJsonObject().get("name");
                        JsonElement idObject = room.getAsJsonObject().get("_id");

                        if (nameObject != null & idObject != null) {
                            String name = nameObject.getAsString();
                            String id = idObject.getAsString();
                            channelMap.put(name, id);
                        }
                    }
                }
            }
        } catch (IOException ignore) {
        }
    }

    /**
     * Get mappings of e-mail-addresses to username
     */
    public void getUsers() {
        try (RocketConnection rocketConnection = new RocketConnection(this.serverURL)) {
            rocketConnection.open(HTTPMethods.GET, getApiPath("users.list"), RequestType.JSON);
            rocketConnection.setAuthHeader(this.loginData);
            JsonObject json = rocketConnection.getResponseJSON();

            if (checkStatus(rocketConnection, json)) {
                JsonArray users = json.getAsJsonArray("users");

                for (JsonElement user : users) {
                    if (user.isJsonObject()) {
                        JsonObject userJson = user.getAsJsonObject();
                        String username = Optional.ofNullable(userJson.get("username"))
                                .orElse(new JsonPrimitive(""))
                                .getAsString();

                        if (username.isEmpty()) {
                            continue;
                        }

                        String id = userJson.get("_id").getAsString();
                        channelMap.put(username, id);

                        JsonArray emails = Optional.ofNullable(userJson.getAsJsonArray("emails"))
                                .orElse(new JsonArray());

                        for (JsonElement email : emails) {
                            if (email.isJsonObject()) {
                                String address = email.getAsJsonObject().get("address").getAsString();
                                eMailUserMap.put(address, username);
                            }
                        }
                    }
                }
            }
        } catch (IOException ignore) {
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

    private boolean checkStatus(RocketConnection rocketConnection, JsonObject jsonObject)  {
        boolean status = rocketConnection.getStatus();
        JsonElement success = jsonObject.get("success");

        if (success != null) {
               return status & success.getAsBoolean();
        }

        return false;
    }
}
