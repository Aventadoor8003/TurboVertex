package cis5550.model;

import cis5550.tools.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class HttpResponse {
    private final String version;
    private final int statusCode;
    private final String statusMessage;
    private final Map<String, String> headers = new HashMap<>();
    private byte[] body;
    private static final Logger logger = Logger.getLogger(HttpResponse.class);

    public HttpResponse(int statusCode, String statusMessage) {
        this.version = "HTTP/1.1";
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.body = null;
    }

    public HttpResponse(int statusCode, String statusMessage, Map<String, String> headers, byte[] body) {
        this.version = "HTTP/1.1";
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
        this.headers.putAll(headers);
        this.body = body;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append(version).append(" ").append(statusCode).append(" ").append(statusMessage).append("\r\n");
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            builder.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        builder.append("\r\n");
        builder.append(body == null ? "" : Arrays.toString(body));
        return builder.toString();
    }

    public byte[] getHeaderBytes() {
        StringBuilder headerStringBuilder = new StringBuilder();

        headerStringBuilder.append(version)
                .append(" ")
                .append(statusCode)
                .append(" ")
                .append(statusMessage)
                .append("\r\n");

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            headerStringBuilder.append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append("\r\n");
        }
        if (!headers.containsKey("content-length") || !Integer.valueOf(headers.get("content-length")).equals(body == null ? "0" : body.length)) {
            logger.debug("Content length header not set or incorrect");
            headerStringBuilder.append("content-Length: ")
                    .append(body == null ? "0" : body.length)
                    .append("\r\n");
        }

        return headerStringBuilder.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] getBody() {
        return body;
    }

    public void addBytes(byte[] bytes) {
        if(body == null) {
            body = bytes;
        } else {
            byte[] newBody = new byte[body.length + bytes.length];
            System.arraycopy(body, 0, newBody, 0, body.length);
            System.arraycopy(bytes, 0, newBody, body.length, bytes.length);
            body = newBody;
        }
    }

    public void setSessionId(String sessionId) {
        headers.put("Set-Cookie", "SessionID=" + sessionId);
    }
}
