package main;

import client.Client;
import server.Server;

import java.io.File;
import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        int serverPort = 8080;
        Server server = new Server(serverPort, new File("src/users"));
        Thread serverThread = new Thread(server);
        serverThread.setDaemon(true);
        serverThread.start();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Thread clientThread = new Thread(new Client("localhost", serverPort));
        clientThread.setDaemon(true);
        clientThread.start();

        while (serverThread.isAlive() || clientThread.isAlive()) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static synchronized void println(String message) {
        System.out.println(message);
    }

    public static synchronized void print(String message) {
        System.out.print(message);
    }
}
