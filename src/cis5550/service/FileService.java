package cis5550.service;

import cis5550.webserver.Request;
import cis5550.webserver.Response;
import cis5550.error.HttpException;
import cis5550.tools.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class FileService {

    private static final Logger logger = Logger.getLogger(FileService.class);

    private static String root;

    private static final Map<String, String> contentTypes = new HashMap<>();

    static {
        contentTypes.put("html", "text/html");
        contentTypes.put("txt", "text/plain");
        contentTypes.put("jpg", "image/jpeg");
        contentTypes.put("jpeg", "image/jpeg");
    }

    public static byte[] getFile(Request request, Response response) throws HttpException {
        if(root == null) {
            logger.error("Fileservice root not set");
            throw new HttpException("Not Found", 404);
        }

        logger.debug("Fileservice handling request: " + request);
        String requestedFilePath = root + request.url();
        File file = new File(requestedFilePath);

        if (!file.exists()) {
            logger.error("File not found: " + requestedFilePath);
            response.status(404, "Not Found");
            return null;
        }

        if (!file.canRead() || request.url().contains("..")) {
            logger.error("File not readable: " + requestedFilePath);
            response.status(403, "Forbidden");
            return null;
        }

        logger.info("Serving file: " + requestedFilePath);
        Map<String, String> headers = new HashMap<>();
        headers.put("content-type", contentTypes.getOrDefault(request.url().substring(request.url().lastIndexOf(".") + 1), "application/octet-stream"));
        headers.put("content-length", String.valueOf(file.length()));
        headers.put("server", "turboVertex");

        try {
            logger.debug("Reading file: " + requestedFilePath);
            byte[] fileContent = Files.readAllBytes(file.toPath());
            response.status(200, "OK");
            //response.bodyAsBytes(fileContent);
            for(Map.Entry<String, String> entry : headers.entrySet()) {
                response.header(entry.getKey(), entry.getValue());
            }
            response.bodyAsBytes(fileContent);
            return null;
        } catch (IOException e) {
            logger.error("Error reading file: " + requestedFilePath);
            throw new HttpException("Internal Server Error", 500);
        }
    }

    public static void setRoot(String root) {
        FileService.root = root;
    }
}
