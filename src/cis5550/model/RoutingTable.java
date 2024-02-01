package cis5550.model;

import cis5550.webserver.Route;

import java.util.Optional;

public interface RoutingTable {
    Route findMatch(String path, String method);
    void add(String path, String method, Route route);
    //boolean remove(String path, String method);
    //boolean contains(String path, String method);
    String findPattern(String path, String method);
}
