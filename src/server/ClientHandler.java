package server;

import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;

public class ClientHandler implements Runnable {

    private static final int HEADER_BYTES = 4;
    private static final String ERROR_MESSAGE = "ERROR";

    private Socket socket;
    private Server server;
    private InputStream in;
    private OutputStream out;

    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            this.socket.setKeepAlive(true);
        } catch (SocketException e) {
            System.err.println("[Server] Failed to set Keep Alive True for client.");
        }

        try {
            this.in = this.socket.getInputStream();
            this.out = this.socket.getOutputStream();
        } catch (IOException e) {
            System.err.println("[Server] Failed to get data streams from client socket.");
            e.printStackTrace();
        }

        byte[] greeting = "Hello!".getBytes();
        try {
            this.sendBytes(greeting);
        } catch (IOException e) {
            System.err.println("[Server] Failed to send greeting");
        }

        while (this.socket.isConnected()) {
            // Get input
            byte[] request = ERROR_MESSAGE.getBytes();
            try {
                request = readIncoming();
            } catch (IOException e) {
                System.err.println("[Server] Failed to read input, closing connection...");
                try {
                    this.close();
                    this.server.removeClientHandler(this);
                    return;
                } catch (IOException ioException) {
                    System.err.println("[Server] Failed to close connection.");
                }
            }

            // Handle input
            byte[] response = this.handleInput(request);

            // Send output
            try {
                this.sendBytes(response);
            } catch (IOException e) {
                System.err.println("[Server] Failed to send response, closing connection...");
                try {
                    this.close();
                    this.server.removeClientHandler(this);
                    return;
                } catch (IOException ioException) {
                    System.err.println("[Server] Failed to close connection.");
                }
            }
        }
    }

    private byte[] handleInput(byte[] data) {
        if (data.length == ERROR_MESSAGE.getBytes().length) {
            if ((new String(data).equals("ERROR"))) {
                return new byte[0];
            }
        }

        // Just echo the data back!
        return (new String(data)).getBytes();
    }

    private void sendBytes(byte[] data) throws IOException {
        byte[] headerBytes = ByteBuffer.allocate(HEADER_BYTES).putInt(data.length).array();

        out.write(headerBytes);
        out.flush();

        out.write(data, 0, data.length);
        out.flush();
    }

    private byte[] readIncoming() throws IOException {
        byte[] headerBytes = new byte[HEADER_BYTES];
        in.read(headerBytes);

        BigInteger bytesToRead = new BigInteger(headerBytes);

        byte[] data = new byte[bytesToRead.intValue()];
        in.read(data);

        return data;
    }

    private byte[] readFileData(File file) {
        FileInputStream fileInputStream;
        int fileLength = (int) file.length();
        byte[] fileData = new byte[fileLength];

        try {
            fileInputStream = new FileInputStream(file);
            fileInputStream.read(fileData);
        } catch (FileNotFoundException e) {
            System.err.printf("File Not Found: %s%n", file.getName());
            fileData = new byte[0];
        } catch (IOException e) {
            System.err.printf("Failed To Read File: %s%n", file.getName());
            fileData = new byte[0];
        }

        return fileData;
    }

    public void close() throws IOException {
        this.socket.close();
    }
}
