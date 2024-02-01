package cis5550.model.impl;

import cis5550.webserver.Session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionImpl implements Session {

    private final String id;
    private final long creationTime;
    private long lastAccessedTime;
    private int maxActiveInterval;
    private final Map<String, Object> attributes;
    private boolean isValid;
    public SessionImpl(String id, long creationTime, long lastAccessedTime, int maxActiveInterval) {
        this.id = id;
        this.creationTime = creationTime;
        this.lastAccessedTime = lastAccessedTime;
        this.maxActiveInterval = maxActiveInterval;
        this.attributes = new ConcurrentHashMap<>();
        this.isValid = true;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public long creationTime() {
        return creationTime;
    }

    @Override
    public long lastAccessedTime() {
        return lastAccessedTime;
    }

    @Override
    public void maxActiveInterval(int seconds) {
        maxActiveInterval = seconds;
    }

    @Override
    public void invalidate() {

    }

    @Override
    public Object attribute(String name) {
        return attributes.getOrDefault(name, null);
    }

    @Override
    public void attribute(String name, Object value) {
        attributes.put(name, value);
    }

    @Override
    public void updateLastAccessedTime() {
        lastAccessedTime = System.currentTimeMillis();
    }

    @Override
    public int getMaxActiveInterval() {
        return maxActiveInterval;
    }
}
