package rocketgateway.rocketchat;

import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LoginData {
    private static final Pattern emailPattern = Pattern.compile("^(.+)@(.+)$");
    private final String jsonString;
    private String authToken;
    private String userId;

    /**
     * Data class for the login data needed for RocketChat.
     * @param username String Username of the bot-user
     * @param password String Password of the bot-user
     */
    public LoginData(String username, String password) {
        JSONObject jsonObject = new JSONObject();

        // Check if provided username is an e-mail address. E-mail addresses need a different auth-token format then
        // "normal" usernames.
        Matcher m = emailPattern.matcher(username);
        if (m.matches()) {
            jsonObject.put("user", username);
        } else {
            jsonObject.put("username", username);
        }

        jsonObject.put("password", password);

        this.jsonString = jsonObject.toString();
    }

    /**
     * Get Json with user-data.
     * @return String Json with user-data.
     */
    public String get() {
        return this.jsonString;
    }

    /**
     * Set auth-token for login. Needed auth-token is returned by RocketChat after a successful login.
     * @param authToken String with auth-token from RocketChat
     * @param userId String Internal RocketChat user-id
     */
    public void setTokens(String authToken, String userId) {
        this.authToken = authToken;
        this.userId = userId;
    }

    /**
     * Get stored auth-token. Is needed for every operation e.g. send a message.
     * @return String with auth-token from RocketChat
     */
    public String getAuthToken() {
        return authToken;
    }

    /**
     * Get internal RocketChat user-id
     * @return String Internal RocketChat user-id
     */
    public String getUserId() {
        return userId;
    }
}
