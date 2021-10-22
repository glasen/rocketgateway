package rocketgateway.rocketchat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RocketChatAPI {
    private final LoginData loginData;
    private final RocketConnection rocketConnection;
    private final String username;
    private boolean loginStatus;
    private final Map<String, String> channelMap;
    private final Map<String, String> eMailUserMap;
    private String lastRoomId;

    /**
     * Rudimentary RocketChat-API class. Contains only methods which are needed to send messages and attachments.
     * @param username String Username of bot-user
     * @param password String Password of bot-user
     * @param serverURL String URL of RocketChat-server
     * @param emailsChannels Map with e-mail-address to channel mapping. See ini-file for more details.
     */
    public RocketChatAPI(String username, String password, String serverURL, Map<String, String> emailsChannels) {
        this.username = username;
        this.loginData = new LoginData(username, password);

        this.rocketConnection = new RocketConnection(serverURL);
        this.loginStatus = false;

        this.channelMap = new HashMap<>();
        this.eMailUserMap = new HashMap<>(emailsChannels);

        this.lastRoomId = "";
    }

    /**
     * Log into RocketChat.
     */
    public void login() {
        try {
            this.rocketConnection.open(HTTPMethods.GET, "/api/v1/login", RequestType.JSON);
            this.rocketConnection.writeJsonData(this.loginData.get());
            JSONObject json = this.rocketConnection.getResponseJSON();
            boolean status = this.rocketConnection.getStatus();

            if (status && json.getString("status").equals("success")) {
                String authToken = json.getJSONObject("data").getString("authToken");
                String userId = json.getJSONObject("data").getString("userId");
                this.loginData.setTokens(authToken, userId);
                this.loginStatus = true;
            }

            this.rocketConnection.close();
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
     * @param message String message to send
     * @param address String E-mail-address of the user to send the message.
     * @param alias String sender alias name.
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
     * @param message String message to send
     * @param channelName String Name of channel to send the message.
     * @param alias String sender alias name.
     * @return boolean Returns true if the message was successfully sent.
     */
    public boolean sendMessageToChannel(String message, String channelName, String alias) {
        updateAll();
        boolean sendStatus = false;

        try {
            this.rocketConnection.open(HTTPMethods.GET, getApiPath("chat.postMessage"), RequestType.JSON);
            this.rocketConnection.setAuthHeader(this.loginData);

            JSONObject jsonData = new JSONObject();

            jsonData.put("text", message);

            if (!Optional.ofNullable(alias).orElse("").isEmpty()) {
                jsonData.put("alias", alias);
            }

            /* Get room-id for channel name. All rooms and channels have a unique id. The room-id is the only
               way to send a message by the same method to a user or a channel.
            */
            String roomId = channelMap.get(channelName);

            if (roomId != null) {
                jsonData.put("roomId", roomId);
            }

            this.rocketConnection.writeJsonData(jsonData.toString());
            JSONObject json = this.rocketConnection.getResponseJSON();
            boolean status = this.rocketConnection.getStatus();

            if (status) {
                try {
                    JSONObject messageObj = json.getJSONObject("message");
                    lastRoomId = messageObj.getString("rid");
                } catch (Exception e) {
                    lastRoomId = "";
                }
            } else {
                lastRoomId = "";
            }

            if (status && json.getBoolean("success")) {
                sendStatus = true;
            }

            this.rocketConnection.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return sendStatus;
    }

    /**
     * Upload a file to a specific room/channel
     * @param outData byte[] Attachment data
     * @param roomId String Internal id of room to upload data
     */
    public void uploadFileToRoom(byte[] outData, String roomId) {
        try {
            String apiPath = getApiPath("rooms.upload");

            if (roomId != null) {
                String fullApiPath = apiPath + "/" + roomId;
                this.rocketConnection.open(HTTPMethods.POST, fullApiPath, RequestType.BINARY);
                this.rocketConnection.setAuthHeader(this.loginData);
                this.rocketConnection.writeBinaryData(outData);
                this.rocketConnection.getResponseJSON();
                this.rocketConnection.close();
                this.lastRoomId = "";
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Log out RocketChat connection
     * @throws IOException Thrown when something went wrong when logging out
     */
    public void logout() throws IOException {
        if (this.loginStatus) {
            this.rocketConnection.open(HTTPMethods.POST, "/api/v1/logout", RequestType.JSON);
            this.rocketConnection.setAuthHeader(this.loginData);
            JSONObject json = this.rocketConnection.getResponseJSON();
            boolean status = this.rocketConnection.getStatus();

            if (status && json.getString("status").equals("success")) {
                this.loginData.setTokens("", "");
                this.loginStatus = false;
            }

            this.rocketConnection.close();
        }
    }

    /**
     * Get login status. True if login was successful.
     * @return boolean Status of login.
     */
    public boolean getLoginStatus() {
        return loginStatus;
    }

    /**
     * Get id of the room the last message was sent.
     * @return String Internal id of the room
     */
    public String getLastRoomId() {
        return lastRoomId;
    }

    /**
     * Updates e-mail-channel-map
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
    private void getChannels() {
        try {
            this.rocketConnection.open(HTTPMethods.GET, getApiPath("channels.list"), RequestType.JSON);
            this.rocketConnection.setAuthHeader(this.loginData);
            JSONObject json = this.rocketConnection.getResponseJSON();
            boolean status = this.rocketConnection.getStatus();

            if (status && json.getBoolean("success")) {
                JSONArray channels = json.getJSONArray("channels");
                for (Object channel : channels) {
                    if (channel instanceof JSONObject) {
                        String name = ((JSONObject) channel).getString("name");
                        String id = ((JSONObject) channel).getString("_id");
                        channelMap.put(name, id);
                    }
                }
            }

            this.rocketConnection.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get mappings of room names to internal room-id.
     */
    private void getRooms() {
        try {
            this.rocketConnection.open(HTTPMethods.GET, getApiPath("rooms.get"), RequestType.JSON);
            this.rocketConnection.setAuthHeader(this.loginData);
            JSONObject json = this.rocketConnection.getResponseJSON();
            boolean status = this.rocketConnection.getStatus();

            if (status && json.getBoolean("success")) {
                JSONArray rooms = json.getJSONArray("update");
                for (Object room : rooms) {
                    if (room instanceof JSONObject) {
                        try {
                            String name = ((JSONObject) room).getString("name");
                            String id = ((JSONObject) room).getString("_id");
                            channelMap.put(name, id);
                        } catch (JSONException ignored) {
                        }
                    }
                }
            }

            this.rocketConnection.close();
        } catch (IOException ignore) {
        }
    }

    /**
     * Get mappings of e-mail-addresses to username
     */
    private void getUsers() {
        try {
            this.rocketConnection.open(HTTPMethods.GET, getApiPath("users.list"), RequestType.JSON);
            this.rocketConnection.setAuthHeader(this.loginData);
            JSONObject json = this.rocketConnection.getResponseJSON();
            boolean status = this.rocketConnection.getStatus();

            if (status && json.getBoolean("success")) {
                JSONArray users = json.getJSONArray("users");
                for (Object user : users) {
                    if (user instanceof JSONObject) {
                        String username = ((JSONObject) user).getString("username");
                        String id = ((JSONObject) user).getString("_id");
                        channelMap.put(username, id);

                        JSONArray emails = ((JSONObject) user).getJSONArray("emails");
                        for (Object email : emails) {
                            if (email instanceof JSONObject) {
                                String address = ((JSONObject) email).getString("address");
                                eMailUserMap.put(address, username);
                            }
                        }
                    }
                }
            }

            this.rocketConnection.close();
        } catch (IOException ignore) {
        }

    }

    /**
     * Generate full REST-API-path from provided end-point
     * @param endPoint String name of end point
     * @return String Full REST-API-path
     */
    private String getApiPath(String endPoint) {
        String apiPath = "/api/v1/";
        return apiPath + endPoint;
    }
}
