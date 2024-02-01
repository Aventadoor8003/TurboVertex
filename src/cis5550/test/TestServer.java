package cis5550.test;

import cis5550.webserver.Server;

public class TestServer {
    public static void main(String[] args) throws Exception {
        Server.securePort(443);
        Server.get("/", (req, res) -> {
            return "Hello World - this is Haochen Gao";
        });

    }
}
