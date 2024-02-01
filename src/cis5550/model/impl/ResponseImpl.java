package cis5550.model.impl;

import cis5550.model.HttpResponse;
import cis5550.webserver.Response;
import cis5550.tools.Logger;

import java.util.*;

public class ResponseImpl implements Response {

    Logger logger = Logger.getLogger(ResponseImpl.class);
    private int statusCode = -1;
    private String statusMessage = null;
    private byte[] body;
    public boolean written = false;
    private final Map<String, Set<String>> headers = new HashMap<>();
    private HttpResponse bufferedResponse;
    private boolean halted = false;

    public ResponseImpl(int statusCode, String statusMessage) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
    }

    @Override
    public void body(String body) {
        if(written) {
            return;
        }
        this.body = body.getBytes();
    }

    @Override
    public void bodyAsBytes(byte[] bodyArg) {
        if(written) {
            return;
        }
        this.body = bodyArg;
    }

    @Override
    public void header(String name, String value) {
        if (written) {
            return;
        }
        headers.putIfAbsent(name, new HashSet<>());
        headers.get(name).add(value);
    }

    @Override
    public void type(String contentType) {
        header("Content-Type", contentType);
    }

    @Override
    public void status(int statusCode, String reasonPhrase) {
        if (written) {
            return;
        }
        this.statusCode = statusCode;
        this.statusMessage = reasonPhrase;
    }

    @Override
    public void write(byte[] b) throws Exception {
        if(!written) {
            //Write headers
            written = true;
            Map<String, String> httpHeaders = new HashMap<>();
            for (Map.Entry<String, Set<String>> entry : headers.entrySet()) {
                httpHeaders.put(entry.getKey(), String.join(", ", entry.getValue()));
            }
            bufferedResponse = new HttpResponse(statusCode, statusMessage, httpHeaders, null);
        }

        //Write body
        bufferedResponse.addBytes(b);
    }

    @Override
    public void redirect(String url, int responseCode) {
        if (written) {
            logger.warn("Response already written, ignoring redirect");
            return;
        }
        if (responseCode != 301 && responseCode != 302 && responseCode != 303 && responseCode != 307 && responseCode != 308) {
            throw new IllegalArgumentException("Invalid redirect code: " + responseCode);
        }
        this.statusCode = responseCode;
        this.statusMessage = "Redirected";
        header("Location", url);
    }

    @Override
    public void halt(int statusCode, String reasonPhrase) {
        this.halted = true;
        status(statusCode, reasonPhrase);
    }

    @Override
    public HttpResponse toHttpResponse(Object handleResult) {
        logger.debug("current response: " + this.toString());
        if(bufferedResponse != null) {
            logger.debug("Returning buffered response: " + bufferedResponse);
            return bufferedResponse;
        }
        Map<String, String> httpHeaders = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : headers.entrySet()) {
            httpHeaders.put(entry.getKey(), String.join(", ", entry.getValue()));
        }
        if(!httpHeaders.containsKey("content-length")) {
            httpHeaders.put("content-length", body == null ? "0" : String.valueOf(body.length));
        }

        byte[] msgBody = null;
        if(written) {
            logger.debug("Response already written, ignoring handleResult");
        } else if(handleResult != null) {
            msgBody = handleResult.toString().getBytes();
        } else {
            msgBody = body;
        }

        HttpResponse result = new HttpResponse(statusCode, statusMessage, httpHeaders, msgBody);
        logger.debug("Returning response: " + result);
        return result;
    }

    @Override
    public boolean getWritten() {
        return written;
    }

    @Override
    public String toString() {
        return "ResponseImpl{" +
                "statusCode=" + statusCode +
                ", statusMessage='" + statusMessage + '\'' +
                ", body=" + Arrays.toString(body) +
                ", written=" + written +
                ", headers=" + headers +
                ", bufferedResponse=" + bufferedResponse +
                '}';
    }

    public boolean getHalted() {
        return halted;
    }

}
