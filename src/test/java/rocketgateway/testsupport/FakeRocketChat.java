package rocketgateway.testsupport;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal in-process fake of the RocketChat REST endpoints used by the gateway. It records every request, so
 * tests can assert on exactly what was sent. Channels are addressed with "#name" and get the room-id
 * "rid-name", users are addressed with "@name" and get the room-id "dm-name".
 */
public final class FakeRocketChat implements AutoCloseable {
    public static final String BOT_USER = "bot";
    public static final String BOT_PASSWORD = "secret";
    public static final String TOKEN = "token-123";
    public static final String USER_ID = "user-id-123";

    public record Request(String method, String path, Map<String, String> query,
                          Map<String, String> headers, byte[] body) {
        public String bodyString() {
            return new String(body, StandardCharsets.UTF_8);
        }

        public JsonObject bodyJson() {
            return JsonParser.parseString(bodyString()).getAsJsonObject();
        }
    }

    public record User(String username, String email) {
    }

    public record Upload(String roomId, String fileId, String body) {
    }

    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Set<String> channels = ConcurrentHashMap.newKeySet();
    private final List<User> users = new CopyOnWriteArrayList<>();
    private final List<Upload> uploads = new CopyOnWriteArrayList<>();
    private final List<String> confirmedFileIds = new CopyOnWriteArrayList<>();
    private final Map<String, Upload> pendingFiles = new ConcurrentHashMap<>();
    private final AtomicInteger fileCounter = new AtomicInteger();
    private volatile int pageCap = 100;
    private volatile boolean failConfirm = false;

    public FakeRocketChat() throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        this.server.setExecutor(Executors.newCachedThreadPool());
        this.server.createContext("/", this::handle);
        this.server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + this.server.getAddress().getPort();
    }

    public FakeRocketChat addChannel(String name) {
        this.channels.add(name);
        return this;
    }

    public FakeRocketChat addUser(String username, String email) {
        this.users.add(new User(username, email));
        return this;
    }

    /** Server side cap for the "count" parameter, like RocketChat's "Max Record Amount". */
    public FakeRocketChat pageCap(int cap) {
        this.pageCap = cap;
        return this;
    }

    public FakeRocketChat failConfirm(boolean fail) {
        this.failConfirm = fail;
        return this;
    }

    public List<Request> requests() {
        return this.requests;
    }

    public List<Request> requestsTo(String path) {
        return this.requests.stream().filter(r -> r.path().equals(path)).toList();
    }

    public List<JsonObject> posts() {
        return requestsTo("/api/v1/chat.postMessage").stream().map(Request::bodyJson).toList();
    }

    public List<Upload> uploads() {
        return this.uploads;
    }

    public List<String> confirmedFileIds() {
        return this.confirmedFileIds;
    }

    @Override
    public void close() {
        this.server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readAllBytes();
        Map<String, String> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.get(0)));
        Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                parseQuery(exchange.getRequestURI().getRawQuery()), headers, body);
        this.requests.add(request);

        try {
            route(exchange, request);
        } catch (RuntimeException e) {
            respond(exchange, 500, error(e.toString()));
        }
    }

    private void route(HttpExchange exchange, Request request) throws IOException {
        String path = request.path();

        if (path.equals("/api/v1/login")) {
            JsonObject in = request.bodyJson();
            boolean ok = BOT_USER.equals(string(in, "username")) && BOT_PASSWORD.equals(string(in, "password"));

            if (!ok) {
                JsonObject out = new JsonObject();
                out.addProperty("status", "error");
                out.addProperty("message", "Unauthorized");
                respond(exchange, 401, out);
                return;
            }

            JsonObject data = new JsonObject();
            data.addProperty("authToken", TOKEN);
            data.addProperty("userId", USER_ID);
            JsonObject out = new JsonObject();
            out.addProperty("status", "success");
            out.add("data", data);
            respond(exchange, 200, out);
            return;
        }

        if (!TOKEN.equals(request.headers().get("x-auth-token")) || !USER_ID.equals(request.headers().get("x-user-id"))) {
            JsonObject out = new JsonObject();
            out.addProperty("status", "error");
            out.addProperty("message", "You must be logged in to do this.");
            respond(exchange, 401, out);
            return;
        }

        if (path.equals("/api/v1/logout")) {
            respond(exchange, 200, success());
        } else if (path.equals("/api/v1/users.list") && request.method().equals("GET")) {
            respond(exchange, 200, usersPage(request));
        } else if (path.equals("/api/v1/chat.postMessage") && request.method().equals("POST")) {
            postMessage(exchange, request);
        } else if (path.startsWith("/api/v1/rooms.media/") && request.method().equals("POST")) {
            media(exchange, request);
        } else if (path.startsWith("/api/v1/rooms.mediaConfirm/") && request.method().equals("POST")) {
            confirm(exchange, request);
        } else {
            respond(exchange, 404, error("not found: " + path));
        }
    }

    private JsonObject usersPage(Request request) {
        int count = Math.min(Integer.parseInt(request.query().getOrDefault("count", "50")), this.pageCap);
        int offset = Integer.parseInt(request.query().getOrDefault("offset", "0"));
        JsonArray array = new JsonArray();

        for (int i = offset; i < Math.min(offset + count, this.users.size()); i++) {
            User user = this.users.get(i);
            JsonObject json = new JsonObject();
            json.addProperty("_id", "id-" + user.username());
            json.addProperty("username", user.username());

            if (user.email() != null) {
                JsonObject email = new JsonObject();
                email.addProperty("address", user.email());
                JsonArray emails = new JsonArray();
                emails.add(email);
                json.add("emails", emails);
            }
            array.add(json);
        }

        JsonObject out = success();
        out.add("users", array);
        out.addProperty("count", array.size());
        out.addProperty("offset", offset);
        out.addProperty("total", this.users.size());
        return out;
    }

    private void postMessage(HttpExchange exchange, Request request) throws IOException {
        JsonObject in = request.bodyJson();
        String channel = string(in, "channel");
        String roomId = null;

        if (channel != null && channel.startsWith("#") && this.channels.contains(channel.substring(1))) {
            roomId = "rid-" + channel.substring(1);
        } else if (channel != null && channel.startsWith("@")
                && this.users.stream().anyMatch(u -> u.username().equals(channel.substring(1)))) {
            roomId = "dm-" + channel.substring(1);
        }

        if (roomId == null) {
            respond(exchange, 400, error("error-invalid-room"));
            return;
        }

        JsonObject message = new JsonObject();
        message.addProperty("rid", roomId);
        message.addProperty("msg", string(in, "text"));
        JsonObject out = success();
        out.add("message", message);
        respond(exchange, 200, out);
    }

    private void media(HttpExchange exchange, Request request) throws IOException {
        String roomId = request.path().substring("/api/v1/rooms.media/".length());
        String fileId = "file-" + this.fileCounter.incrementAndGet();
        Upload upload = new Upload(roomId, fileId, request.bodyString());
        this.pendingFiles.put(fileId, upload);
        this.uploads.add(upload);

        JsonObject file = new JsonObject();
        file.addProperty("_id", fileId);
        file.addProperty("url", "/file-upload/" + fileId + "/x");
        JsonObject out = success();
        out.add("file", file);
        respond(exchange, 200, out);
    }

    private void confirm(HttpExchange exchange, Request request) throws IOException {
        String[] parts = request.path().substring("/api/v1/rooms.mediaConfirm/".length()).split("/");
        Upload pending = parts.length == 2 ? this.pendingFiles.remove(parts[1]) : null;

        if (this.failConfirm || pending == null || !pending.roomId().equals(parts[0])) {
            respond(exchange, 400, error("error-invalid-file"));
            return;
        }

        this.confirmedFileIds.add(pending.fileId());
        respond(exchange, 200, success());
    }

    private static JsonObject success() {
        JsonObject out = new JsonObject();
        out.addProperty("success", true);
        return out;
    }

    private static JsonObject error(String message) {
        JsonObject out = new JsonObject();
        out.addProperty("success", false);
        out.addProperty("error", message);
        return out;
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> query = new HashMap<>();

        if (raw != null) {
            for (String pair : raw.split("&")) {
                String[] kv = pair.split("=", 2);
                query.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                        kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
            }
        }

        return query;
    }

    private static void respond(HttpExchange exchange, int code, JsonObject json) throws IOException {
        byte[] data = json.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(code, data.length);
        exchange.getResponseBody().write(data);
        exchange.close();
    }
}
