package org.nonprofit.archive;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class InfraiClient {
    private final HttpClient http;
    private final ArchiveConfig config;

    public InfraiClient(ArchiveConfig config) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), config);
    }

    InfraiClient(HttpClient http, ArchiveConfig config) {
        this.http = http;
        this.config = config;
    }

    // infrai.storage.bucket.create
    public void createBucket(String name) throws IOException, InterruptedException {
        call("POST", "/v1/storage/bucket/create", Map.of("name", name));
    }

    // infrai.rtc.room.create
    public Map<String, Object> createRoom(String name) throws IOException, InterruptedException {
        return call("POST", "/v1/rtc/room/create", Map.of(
                "name", name,
                "max_participants", 24,
                "empty_timeout_s", 900,
                "region", "auto"));
    }

    // infrai.rtc.token.issue
    public Map<String, Object> issueRoomToken(String room, String identity, String displayName)
            throws IOException, InterruptedException {
        return call("POST", "/v1/rtc/token/issue", Map.of(
                "room", room,
                "identity", identity,
                "display_name", displayName,
                "ttl_s", 3600,
                "can_publish", true,
                "can_subscribe", true));
    }

    // infrai.storage.object.presign
    public String presignPut(String bucket, String key, String contentType, long maxBytes, String requestId)
            throws IOException, InterruptedException {
        Map<String, Object> data = call("POST", "/v1/storage/object/presign/" + segment(bucket) + "/" + path(key),
                Map.of(
                        "op", "put",
                        "expires_seconds", 900,
                        "content_type", contentType,
                        "max_bytes", maxBytes,
                        "idempotency_key", requestId));
        return requiredString(data, "url");
    }

    private Map<String, Object> call(String method, String path, Map<String, Object> body)
            throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(URI.create(config.baseUrl() + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(Json.write(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> envelope = Json.object(Json.parse(response.body()));
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                if (response.statusCode() == 429 && attempt < 3) {
                    Thread.sleep(retryDelayMillis(response, attempt));
                    continue;
                }
                Map<String, Object> error = Json.object(envelope.get("error"));
                throw new InfraiException(String.valueOf(error.get("code")), error, response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new IOException("Infrai transport response " + response.statusCode());
            }
            return Json.object(envelope.get("data"));
        }
        throw new IllegalStateException("retry loop exhausted");
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> {
                    try { return Math.max(1L, Long.parseLong(value)) * 1000L; }
                    catch (NumberFormatException ignored) { return 250L * (1L << attempt); }
                })
                .orElse(250L * (1L << attempt));
    }

    private static String requiredString(Map<String, Object> data, String name) {
        Object value = data.get(name);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalStateException("Response data is missing " + name);
        }
        return text;
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String path(String value) {
        StringBuilder encoded = new StringBuilder();
        for (String part : value.split("/", -1)) {
            if (encoded.length() > 0) encoded.append('/');
            encoded.append(segment(part));
        }
        return encoded.toString();
    }

    public static final class InfraiException extends IOException {
        private static final long serialVersionUID = 1L;
        private final String code;
        private final Map<String, Object> details;
        private final int status;

        InfraiException(String code, Map<String, Object> details, int status) {
            super("Infrai rejected the request: " + code);
            this.code = code;
            this.details = Map.copyOf(details);
            this.status = status;
        }

        public String code() { return code; }
        public Map<String, Object> details() { return details; }
        public int status() { return status; }
    }

    static final class Json {
        static String write(Object value) {
            if (value == null) return "null";
            if (value instanceof String text) return quote(text);
            if (value instanceof Number || value instanceof Boolean) return value.toString();
            if (value instanceof Map<?, ?> map) {
                StringBuilder out = new StringBuilder("{");
                boolean first = true;
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (!first) out.append(',');
                    first = false;
                    out.append(quote(String.valueOf(entry.getKey()))).append(':').append(write(entry.getValue()));
                }
                return out.append('}').toString();
            }
            if (value instanceof Iterable<?> values) {
                StringBuilder out = new StringBuilder("[");
                boolean first = true;
                for (Object item : values) {
                    if (!first) out.append(',');
                    first = false;
                    out.append(write(item));
                }
                return out.append(']').toString();
            }
            throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass());
        }

        static Object parse(String source) throws IOException {
            Parser parser = new Parser(source);
            Object value = parser.value();
            parser.space();
            if (!parser.end()) throw new IOException("Trailing JSON content");
            return value;
        }

        @SuppressWarnings("unchecked")
        static Map<String, Object> object(Object value) throws IOException {
            if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
            throw new IOException("Expected JSON object");
        }

        private static String quote(String text) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : text.toCharArray()) {
                switch (c) {
                    case '\"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '\b' -> out.append("\\b");
                    case '\f' -> out.append("\\f");
                    case '\n' -> out.append("\\n");
                    case '\r' -> out.append("\\r");
                    case '\t' -> out.append("\\t");
                    default -> {
                        if (c < 32) out.append(String.format("\\u%04x", (int) c));
                        else out.append(c);
                    }
                }
            }
            return out.append('\"').toString();
        }

        private static final class Parser {
            private final String source;
            private int at;
            Parser(String source) { this.source = source; }
            boolean end() { return at == source.length(); }
            void space() { while (!end() && Character.isWhitespace(source.charAt(at))) at++; }
            Object value() throws IOException {
                space();
                if (end()) throw new IOException("Empty JSON response");
                return switch (source.charAt(at)) {
                    case '{' -> objectValue();
                    case '[' -> arrayValue();
                    case '\"' -> stringValue();
                    case 't' -> literal("true", true);
                    case 'f' -> literal("false", false);
                    case 'n' -> literal("null", null);
                    default -> numberValue();
                };
            }
            Object literal(String token, Object value) throws IOException {
                if (!source.startsWith(token, at)) throw new IOException("Invalid JSON literal");
                at += token.length();
                return value;
            }
            Map<String, Object> objectValue() throws IOException {
                at++;
                Map<String, Object> map = new LinkedHashMap<>();
                space();
                if (take('}')) return map;
                do {
                    space();
                    String key = stringValue();
                    space();
                    expect(':');
                    map.put(key, value());
                    space();
                } while (take(','));
                expect('}');
                return map;
            }
            List<Object> arrayValue() throws IOException {
                at++;
                List<Object> list = new ArrayList<>();
                space();
                if (take(']')) return list;
                do { list.add(value()); space(); } while (take(','));
                expect(']');
                return list;
            }
            String stringValue() throws IOException {
                expect('\"');
                StringBuilder out = new StringBuilder();
                while (!end()) {
                    char c = source.charAt(at++);
                    if (c == '\"') return out.toString();
                    if (c != '\\') { out.append(c); continue; }
                    if (end()) throw new IOException("Invalid JSON escape");
                    char escaped = source.charAt(at++);
                    switch (escaped) {
                        case '\"', '\\', '/' -> out.append(escaped);
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (at + 4 > source.length()) throw new IOException("Invalid unicode escape");
                            out.append((char) Integer.parseInt(source.substring(at, at + 4), 16));
                            at += 4;
                        }
                        default -> throw new IOException("Invalid JSON escape");
                    }
                }
                throw new IOException("Unclosed JSON string");
            }
            Number numberValue() throws IOException {
                int start = at;
                while (!end() && "-+0123456789.eE".indexOf(source.charAt(at)) >= 0) at++;
                try {
                    String value = source.substring(start, at);
                    return value.contains(".") || value.contains("e") || value.contains("E")
                            ? Double.parseDouble(value) : Long.parseLong(value);
                } catch (NumberFormatException ex) { throw new IOException("Invalid JSON number", ex); }
            }
            boolean take(char expected) {
                if (!end() && source.charAt(at) == expected) { at++; return true; }
                return false;
            }
            void expect(char expected) throws IOException {
                if (!take(expected)) throw new IOException("Expected " + expected);
            }
        }
    }
}
