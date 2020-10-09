package client;

import server.Main;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Scanner;

public class Client implements Runnable{

    public static void main(String[] args) {
    }

    private static final int HEADER_BYTES = 4;
    private static final String ERROR_MESSAGE = "ERROR";

    private Socket socket;
    private InputStream in;
    private OutputStream out;

    public Client(int port) throws IOException {
        this.socket = new Socket("localhost", port);
    }

    @Override
    public void run() {
        try {
            this.in = socket.getInputStream();
            this.out = socket.getOutputStream();

            byte[] greetingBytes = readIncoming();
            Main.println("[Client] Server Greeting: " + new String(greetingBytes));

            Scanner scanner = new Scanner(System.in);
            String input = "";
            System.out.print("[Client] Message to send: ");
            while (!(input = scanner.nextLine()).equals("exit")) {
                sendBytes(input.getBytes());

                byte[] response = this.readIncoming();
                Main.println("[Client] Response from server: " + new String(response));

                System.out.print("[Client] Message to send: ");
            }
            Main.println("[Client] Closing connection.");
            this.socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
}
