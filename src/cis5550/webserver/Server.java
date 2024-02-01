package cis5550.webserver;

import cis5550.error.HttpException;
import cis5550.model.HttpResponse;
import cis5550.model.RoutingTable;
import cis5550.model.impl.HashMapRoutingTable;
import cis5550.model.impl.ResponseImpl;
import cis5550.model.impl.SessionImpl;
import cis5550.service.FileService;
import cis5550.tools.Logger;
import cis5550.utils.ServerUtils;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ServerSocketFactory;
import javax.net.ssl.*;
import java.security.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Server {

    private static int port = 80;
    private static int httpsPort = -1;
    private final static Logger logger = Logger.getLogger(Server.class);
    private final static Set<String> methods = new HashSet<>(Arrays.asList("GET", "HEAD", "POST", "PUT"));
    private static Server server = null;
    private static boolean initialized = false;
    private final RoutingTable routingTable;
    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();


    public Server() {
        this.routingTable = new HashMapRoutingTable();
    }

    public static class staticFiles {
        public static void location(String path) {
            initializeIfNot();
            FileService.setRoot(path);
            logger.info("Static file location set to: " + path);
        }
    }

    public static void get(String path, Route route) {
        initializeIfNot();
        server.routingTable.add(path, "GET", route);
    }

    public static void post(String path, Route route) {
        initializeIfNot();
        server.routingTable.add(path, "POST", route);
    }

    public static void put(String path, Route route) {
        initializeIfNot();
        server.routingTable.add(path, "PUT", route);
    }

    public static void port(int port) {
        Server.port = port;
        logger.info("Server port set to: " + port);
    }

    public static void securePort(int httpsPort) {
        Server.httpsPort = httpsPort;
        logger.info("Server secure port set to: " + httpsPort);
    }

    public static Session getSession(String sessionId) {
        logger.debug("Getting session for: " + sessionId);
        if(sessionId == null) {
            logger.debug("Session id is null");
            return null;
        }
        logger.debug("Got session for " + sessionId + ": " + sessions.get(sessionId));
        return sessions.get(sessionId);
    }

    private static void initializeIfNot() {
        if (!initialized) {
            initialized = true;
            server = new Server();
            new Thread(() -> {
                try {
                    server.run();
                } catch (Exception e) {
                    logger.error("Error while running server", e);
                }
            }).start();
        }
    }

    public void httpLoop(ServerSocket serverSocket) {
        logger.info("Server starting, listening on port: " + port);
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                logger.info("Accepted connection from: " + socket.getInetAddress() + ":" + socket.getPort());

                new Thread(() -> {
                    try {
                        socket.setSoTimeout(10000);
                        handleConnection(socket);
                    } catch (Exception e) {
                        logger.error("Error while handling connection", e);
                    }
                }).start();
            } catch (IOException e) {
                logger.error("Error: occured", e);
            }
        }
    }


    public void run() {
        logger.info("Server starting, listening on port: " + port + ", secure port: " + httpsPort);

        ServerSocket serverSocket = null;
        try {
            String testPassword = "secret";
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream("keystore.jks"), testPassword.toCharArray());
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            keyManagerFactory.init(keyStore, testPassword.toCharArray());
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            ServerSocketFactory factory = sslContext.getServerSocketFactory();
            serverSocket = factory.createServerSocket(httpsPort);
        } catch (IOException e) {
            logger.fatal("Could not listen on port: " + httpsPort);
            return;
        } catch (UnrecoverableKeyException | CertificateException | NoSuchAlgorithmException | KeyStoreException |
                 KeyManagementException e) {
            throw new RuntimeException(e);
        } catch (IllegalArgumentException e) {
            logger.error("Could not listen on port: " + httpsPort);
        }

        ServerSocket httpServerSocket = null;
        logger.debug("Trying to start https server on port: " + port);
        try {
            httpServerSocket = new ServerSocket(port);
            ServerSocket finalHttpServerSocket = httpServerSocket;
            new Thread(() -> httpLoop(finalHttpServerSocket)).start();
        } catch (IOException e) {
            logger.fatal("Could not listen on port: " + port);
            return;
        }

        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> {
            try {
                eliminateSession();
            } catch (Exception e) {
                logger.error("Error in scheduled task", e);
            }
        }, 0, 1, TimeUnit.SECONDS);

        logger.info("Https service started on port: " + port);
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                logger.info("Accepted connection from: " + socket.getInetAddress() + ":" + socket.getPort());

                new Thread(() -> {
                    try {
                        socket.setSoTimeout(10000);
                        handleConnection(socket);
                    } catch (Exception e) {
                        logger.error("Error while handling connection", e);
                    }
                }).start();
            } catch (IOException e) {
                logger.error("Error: occurred", e);
            }
        }

    }


    private static void handleConnection(Socket socket) throws IOException {
        while (true) {
            OutputStream output = socket.getOutputStream();
            Request request = null;
            try {
                request = parseRequest(socket);
                logger.debug("Request parsed: " + request);
            } catch (HttpException e) {
                logger.error("Error parsing request", e);
                sendResponse(new HttpResponse(e.getStatusCode(), e.getMessage()), output);
                socket.close();
                continue;
            }

            if (request == null) {
                socket.close();
                break;
            }

            boolean create = updateSession(request);
            Response response = new ResponseImpl(200, "OK");
            Object handleResult = null;
            try {
                Route route = server.routingTable.findMatch(request.url(), request.requestMethod());
                if (route == null) {
                    route = FileService::getFile;
                    logger.debug("Route not found, set to static file service");
                }
                logger.debug("Route found: " + route.getClass().getName());
                Object result = route.handle(request, response);
                logger.debug("Route handled result: " + result);
                handleResult = result;
                if (result != null) {
                    response.body(result.toString());
                }
            } catch (Exception e) {
                logger.error("Error handling request", e);
                if (!response.getWritten()) {
                    sendResponse(new HttpResponse(500, "Internal Server Error"), output);
                } else {
                    try {
                        socket.close();
                    } catch (IOException closeException) {
                        logger.error("Error closing the socket", closeException);
                    }
                }
                continue;
            }

            HttpResponse httpResponse = response.toHttpResponse(handleResult);
            if(create) {
                httpResponse.setSessionId(request.session().id());
            }
            logger.debug("Http response generated: " + httpResponse);
            sendResponse(httpResponse, output);
            if (request.headers().contains("connection") && request.headers("connection").equals("close")) {
                socket.close();
                break;
            }
        }
    }

    private static void sendResponse(HttpResponse response, OutputStream output) throws IOException {
        // Send status line and headers as text
        logger.debug("Sending response: " + response);
        byte[] headerBytes = response.getHeaderBytes();
        output.write(headerBytes);
        logger.debug("Header sent: " + new String(headerBytes, StandardCharsets.UTF_8));

        // Send an empty line
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));

        // Send the body as binary
        byte[] body = response.getBody();
        if (body != null) {
            output.write(body);
            logger.debug("Body sent: " + new String(body, StandardCharsets.UTF_8));
        }

        output.flush();
    }


    /**
     * Parses the request from the socket
     *
     * @return if connection is closed, return null.
     * otherwise return the request
     * @throws HttpException if the request is malformed
     * @throws IOException   if there is an error reading from the socket
     */
    private static Request parseRequest(Socket socket) throws HttpException, IOException {
        InputStream input = socket.getInputStream();
        byte[] buffer = new byte[8192];

        int bytesRead = input.read(buffer);
        if (bytesRead == -1) {
            return null;
        }

        int headerEndIndex = findHeaderEnd(buffer, bytesRead);
        if (headerEndIndex == -1) {
            throw new HttpException("Incomplete headers", 400);
        }

        String headerPart = new String(buffer, 0, headerEndIndex, StandardCharsets.UTF_8);
        List<String> lines = List.of(headerPart.split("\r\n"));
        logger.debug("Got header: " + headerPart);

        String[] requestLine = lines.get(0).split(" ");
        Map<String, String> headers = new HashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String[] header = lines.get(i).split(": ");
            headers.put(header[0].toLowerCase(), header[1]);
        }

        if (requestLine.length < 3 || !headers.containsKey("host")) {
            throw new HttpException("Bad Request", 400);
        }

        String method = requestLine[0];

        if (!methods.contains(method)) {
            throw new HttpException("Not Implemented", 501);
        }

        String path = requestLine[1];
        String version = requestLine[2];
        if (!version.equals("HTTP/1.1")) {
            throw new HttpException("HTTP Version Not Supported", 505);
        }

        //body
        byte[] bodyBytes = null;
        if (headers.containsKey("content-length")) {
            int contentLength = Integer.parseInt(headers.get("content-length"));
            bodyBytes = new byte[contentLength];

            int bodyStartIndex = headerEndIndex + 4;
            int bytesAlreadyRead = bytesRead - bodyStartIndex;
            System.arraycopy(buffer, bodyStartIndex, bodyBytes, 0, bytesAlreadyRead);

            while (bytesAlreadyRead < contentLength) {
                bytesRead = input.read(bodyBytes, bytesAlreadyRead, contentLength - bytesAlreadyRead);
                if (bytesRead == -1) {
                    throw new HttpException("Unexpected end of input", 400);
                }
                bytesAlreadyRead += bytesRead;
            }
        }

        //path params
        Map<String, String> params = null;
        String pattern = server.routingTable.findPattern(path, method);
        if (pattern != null) {
            params = new HashMap<>();
            String[] patternParts = pattern.split("/");
            String[] pathParts = path.split("/");
            for (int i = 0; i < patternParts.length; i++) {
                if (patternParts[i].startsWith(":")) {
                    params.put(patternParts[i].substring(1), pathParts[i]);
                }
            }
        }

        //Query params
        Map<String, String> queryParams = new HashMap<>();
        if (path.contains("?")) {
            String[] pathParts = path.split("\\?");
            path = pathParts[0];
            logger.debug("query part: " + pathParts[1]);
            String[] queryParts = pathParts[1].split("&");
            logger.debug("query parts array: " + Arrays.toString(queryParts));
            for (String queryPart : queryParts) {
                String[] query = queryPart.split("=");
                String key = headers.get("content-type").equals("application/x-www-form-urlencoded") ? URLDecoder.decode(query[0], StandardCharsets.UTF_8) : query[0];
                String value = query.length > 1 ? URLDecoder.decode(query[1], StandardCharsets.UTF_8) : "";
                queryParams.put(key, value);
            }
        }

        if (bodyBytes != null && headers.get("content-type") != null && headers.get("content-type").equals("application/x-www-form-urlencoded")) {
            String body = new String(bodyBytes, StandardCharsets.UTF_8);
            logger.debug("body content: " + body);
            String[] bodyQueryParts = body.split("&");
            for (String queryPart : bodyQueryParts) {
                String[] query = queryPart.split("=");
                String key = URLDecoder.decode(query[0], StandardCharsets.UTF_8);
                String value = query.length > 1 ? URLDecoder.decode(query[1], StandardCharsets.UTF_8) : "";
                queryParams.put(key, value);
            }
        }

        return new RequestImpl(method, path, version, headers, queryParams, params, (InetSocketAddress) socket.getRemoteSocketAddress(), bodyBytes, server);
    }

    private static int findHeaderEnd(byte[] buffer, int length) {
        for (int i = 0; i < length - 3; i++) {
            if (buffer[i] == '\r' && buffer[i + 1] == '\n' && buffer[i + 2] == '\r' && buffer[i + 3] == '\n') {
                return i;
            }
        }
        return -1;
    }

    private static boolean updateSession(Request request) {
        boolean create = false;
        String sessionId = ServerUtils.parseSessionIdFromCookie(request.headers("cookie"));
        logger.debug("Session id: " + sessionId);

        if (sessionId == null || !sessions.containsKey(sessionId)) {
            sessionId = UUID.randomUUID().toString();
            logger.debug("New session id: " + sessionId);
            create = true;
        }

        Session session = sessions.get(sessionId);

        // Check if the session exists; if it doesn't or has expired, create a new one
        if (session == null || (System.currentTimeMillis() - session.lastAccessedTime() > session.getMaxActiveInterval() * 1000L)) {
            long timestamp = System.currentTimeMillis();
            session = new SessionImpl(sessionId, timestamp, timestamp, 300);
            sessions.put(sessionId, session);
            logger.debug("Creating new session " + sessionId);
            create = true;
        } else {
            session.updateLastAccessedTime();
            logger.debug("Session " + sessionId + " updated");
        }

        request.setSessionId(sessionId);
        return create;
    }


    private static void eliminateSession() {
        long currentTime = System.currentTimeMillis();
        for(var entry : sessions.entrySet()) {
            if(currentTime - entry.getValue().lastAccessedTime() > entry.getValue().getMaxActiveInterval() * 1000L) {
                sessions.remove(entry.getKey());
            }
        }
    }

}