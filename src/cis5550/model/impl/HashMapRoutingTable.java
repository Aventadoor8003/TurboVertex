package cis5550.model.impl;

import cis5550.model.RoutingTable;
import cis5550.tools.Logger;
import cis5550.webserver.Route;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class HashMapRoutingTable implements RoutingTable {

    private static final Logger logger = Logger.getLogger(HashMapRoutingTable.class);

    private final Map<RouteKey, Route> table = new HashMap<>();


    @Override
    public Route findMatch(String path, String method) {
        for (var entry : table.entrySet()) {
            if (matchPath(entry.getKey().path(), path) && entry.getKey().method().equals(method)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public void add(String path, String method, Route route) {
        table.put(new RouteKey(path, method), route);
    }

    @Override
    public String findPattern(String path, String method) {
        for (var entry : table.entrySet()) {
            if (matchPath(entry.getKey().path(), path) && entry.getKey().method().equals(method)) {
                logger.debug("Found pattern: " + entry.getKey().path() + " for path: " + path);
                return entry.getKey().path();
            }
        }
        logger.debug("No pattern found for path: " + path);
        return null;
    }

    private boolean matchPath(String pattern, String path) {
        String[] patternParts = pattern.split("/");
        String[] pathParts = path.split("/");

        if (patternParts.length != pathParts.length) {
            return false;
        }

        for (int i = 0; i < patternParts.length; i++) {
            if (patternParts[i].startsWith(":")) {
                continue;
            }

            if (!patternParts[i].equals(pathParts[i])) {
                return false;
            }
        }

        return true;
    }

}

record RouteKey(String path, String method) {
    @Override
    public int hashCode() {
        return (path + method).hashCode();
    }
}