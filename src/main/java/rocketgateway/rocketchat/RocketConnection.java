package rocketgateway.rocketchat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class RocketConnection {
    private final String serverURL;
    private HttpURLConnection con;
    private URL url;

    /**
     * This class provides all methods which are needed to generate a connection to a RocketChat-server
     * @param serverURL String URL of RocketChat-server
     */
    public RocketConnection(String serverURL) {
        this.serverURL = serverURL;
        this.con = null;
        this.url = null;
    }

    /**
     * Open a connection to a RocketChat-server with a specific HTTP-method and request type.
     * @param method HTTPMethods POST, GET or PUT
     * @param apiPath Full REST-API-endpoint e.g. "/api/v1/login"
     * @param requestType RequestType e.g. RequestType.JSON or RequestType.BINARY
     * @throws IOException Thrown when something went wrong.
     */
    public void open(HTTPMethods method, String apiPath, RequestType requestType) throws IOException {
        this.url = new URL(serverURL + apiPath);
        this.con = (HttpURLConnection) url.openConnection();

        switch (requestType) {
            case BINARY -> this.con.setRequestProperty("Content-Type", "multipart/form-data; boundary=envelope-0815");
            case JSON -> {
                con.setRequestProperty("Accept", "application/json");
                con.setRequestProperty("Content-Type", "application/json");
            }
        }

        this.con.setUseCaches(false);
        this.con.setDoInput(true);
        this.con.setDoOutput(true);

        con.setRequestMethod(method.name());
        con.setDoOutput(true);
    }

    /**
     * Set needed auth-token-header
     * @param loginData LoginData-instance
     */
    public void setAuthHeader(LoginData loginData) {
        if (this.con != null) {
            this.con.setRequestProperty("X-Auth-Token", loginData.getAuthToken());
            this.con.setRequestProperty("X-User-Id", loginData.getUserId());
        }
    }

    /**
     * Write JSON-string into RocketChat connection
     * @param jsonString String to write in JSON-format
     */
    public void writeJsonData(String jsonString) {
        byte[] data = jsonString.getBytes();
        this.writeBinaryData(data);
    }

    /**
     * Write binary data into RocketChat connection
     * @param data Data to write
     */
    public void writeBinaryData(byte[] data) {
        try (OutputStream os = this.con.getOutputStream()) {
            os.write(data);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get status of connection.
     * @return boolean True when return code is 200. Otherwise false
     */
    public boolean getStatus() {
        try {
            return this.con.getResponseCode() == 200;
        } catch (IOException ignored) {
            return false;
        }
    }

    /**
     * Get response in JSON-format from the RocketChat server
     * @return JSONObject
     */
    public JsonObject getResponseJSON() {
        try {
            byte[] data = con.getInputStream().readAllBytes();
            String responseData = new String(data);
            return JsonParser.parseString(responseData).getAsJsonObject();
        } catch (IOException ignored) {
            return new JsonObject();
        }
    }

    /**
     * Close RocketChat connection
     */
    public void close() {
        if (this.con != null) {
            this.con.disconnect();
        }
    }

}
