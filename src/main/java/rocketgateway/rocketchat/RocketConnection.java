package rocketgateway.rocketchat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class RocketConnection implements AutoCloseable {
    private final String serverURL;
    private HttpURLConnection con;

    /**
     * This class provides all methods which are needed to generate a connection to a RocketChat-server
     * @param serverURL String URL of RocketChat-server
     */
    public RocketConnection(String serverURL) {
        this.serverURL = serverURL;
        this.con = null;
    }

    /**
     * Open a connection to a RocketChat-server with a specific HTTP-method and request type.
     * @param method HTTPMethods POST, GET or PUT
     * @param apiPath Full REST-API-endpoint e.g. "/api/v1/login"
     * @param binary When true use binary mode for sending data.
     * @throws IOException Thrown when something went wrong.
     */
    public void open(HTTPMethods method, String apiPath, boolean binary) throws IOException {
        URL url = URI.create(serverURL + apiPath).toURL();
        this.con = (HttpURLConnection) url.openConnection();

        this.con.setConnectTimeout(5000);
        this.con.setReadTimeout(8000);
        this.con.setRequestMethod(method.name());
        this.con.setRequestProperty("User-Agent", "RocketGateway/1.0");

        if (binary) {
            this.con.setRequestProperty("Content-Type", "multipart/form-data; boundary=envelope-0815");
        } else {
            this.con.setRequestProperty("Accept", "application/json");
            this.con.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        }

        this.con.setUseCaches(false);
        this.con.setDoInput(true);

        // Only POST/PUT need output
        if (method == HTTPMethods.POST || method == HTTPMethods.PUT) {
            this.con.setDoOutput(true);
        }
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
        if (!this.con.getDoOutput()) {
            throw new IllegalStateException("This request does not support output.");
        }

        try (OutputStream os = this.con.getOutputStream()) {
            os.write(data);
            os.flush();
        } catch (IOException e) {
            throw new RuntimeException("Failed to write request body", e);
        }
    }

    /**
     * Get status of connection.
     * @return boolean True when return code is 200. Otherwise false
     */
    public boolean getStatus() {
        try {
            int code = this.con.getResponseCode();
            return code >= 200 && code < 300;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get response in JSON-format from the RocketChat server
     * @return JSONObject
     */
    public JsonObject getResponseJSON() {
        try (InputStream is = getEffectiveInputStream()) {
            byte[] data = is.readAllBytes();
            return JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            JsonObject error = new JsonObject();
            error.addProperty("error", e.getMessage());
            return error;
        }
    }

    private InputStream getEffectiveInputStream() throws IOException {
        int status = this.con.getResponseCode();
        if (status >= 200 && status < 300) {
            return this.con.getInputStream();
        } else {
            return this.con.getErrorStream();
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
