package server;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

public class Server implements Runnable{

    private ServerSocket serverSocket;
    private File usersFolder;
    private List<ClientHandler> clientHandlers = new ArrayList<>();

    public Server(int port, File usersFolder) throws IOException {
        this.serverSocket = new ServerSocket(port);
        this.usersFolder = usersFolder;
    }

    public void run() {
        try {
            while (true) {
                ClientHandler clientHandler = new ClientHandler(this.serverSocket.accept(), this);
                this.clientHandlers.add(clientHandler);
                Thread thread = new Thread(clientHandler);
                thread.setDaemon(true);
                thread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() throws IOException {
        for (ClientHandler clientHandler : this.clientHandlers) {
            clientHandler.close();
        }
        this.clientHandlers.clear();
        this.serverSocket.close();
    }

    public void removeClientHandler(ClientHandler clientHandler) {
        this.clientHandlers.remove(clientHandler);
    }
}
