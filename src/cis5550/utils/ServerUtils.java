package cis5550.utils;

import cis5550.tools.Logger;

public class ServerUtils {

    private static final Logger logger = Logger.getLogger(ServerUtils.class);

    public static String parseSessionIdFromCookie(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return null;
        }

        String[] cookies = cookieHeader.split(";\\s*");
        for (String cookie : cookies) {
            String[] nameValuePair = cookie.split("=", 2);
            if (nameValuePair.length == 2 && "SessionID".equalsIgnoreCase(nameValuePair[0].trim())) {
                return nameValuePair[1].trim();
            }
        }
        return null;
    }
}
