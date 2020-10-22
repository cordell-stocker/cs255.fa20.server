package server;

import main.Main;

import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;

public class ClientHandler implements Runnable {

    private static final int STATUS_CODE_BYTES = 4;
    private static final int LOCATION_LENGTH_BYTES = 4;
    private static final int CONTENT_TYPE_LENGTH_BYTES = 4;
    private static final int CONTENT_LENGTH_BYTES = 4;

    private static final String CONSOLE_TEXT_CONTENT_TYPE = "console/text";
    private static final String COMMAND_CONTENT_TYPE = "console/command";
    private static final String FILE_CONTENT_TYPE = "file/";
    private static final String NONE_CONTENT_TYPE = "None";
    private static final String UPDATE_ECHO_CONTENT_TYPE = "update/echo";

    private static final String HELP_COMMAND = "help";
    private static final String TOGGLE_ECHO_COMMAND = "toggle echo";
    private static final String LS_COMMAND = "ls";
    private static final String CD_COMMAND = "cd ";
    private static final String DOWNLOAD_COMMAND = "download ";
    private static final String[] ALL_COMMANDS = new String[]{
            HELP_COMMAND, TOGGLE_ECHO_COMMAND, LS_COMMAND, CD_COMMAND, DOWNLOAD_COMMAND
    };

    private static final String[] VALID_FILES = new String[]{"snek.png", "todo.txt", "dog.jpg", "cat.jpg"};

    private static final int OK = 200;
    private static final int BAD_REQUEST = 400;

    private static final String BASE_FOLDER = "users";

    private final Socket socket;
    private final Server server;
    private InputStream in;
    private OutputStream out;
    private boolean echo = true;
    private String location = BASE_FOLDER;

    /**
     * Prepares the communication with the client.
     *
     * Must call {@link ClientHandler#run()} to start the communication.
     *
     * @param socket the {@link Socket} connected to the client.
     * @param server the parent server that controls all ClientHandlers.
     */
    public ClientHandler(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;
    }

    /**
     * Handles the communication with the client.
     *
     * Continues handling requests until the client closes the connection.
     */
    @Override
    public void run() {
        // Set KeepAlive: true
        if (!this.setKeepAlive(true)) {
            this.close();
            return;
        }

        // Get the Input and Output Streams
        if (!this.getAndSetDataStreams()) {
            this.close();
            return;
        }

        // Send the server Greeting and the base location.
        if (!this.sendGreetingMessage()) {
            this.close();
            return;
        }

        // Handle requests
        this.connectionLoop();

        this.close();
    }

    /**
     * Sets the keepAlive status of the socket connection.
     *
     * @param value the value to set the keepAlive status to.
     * @return true if operation was successful, false otherwise.
     */
    private boolean setKeepAlive(boolean value) {
        try {
            this.socket.setKeepAlive(value);
        } catch (SocketException e) {
            Main.println("[Server] Failed to set Keep Alive True for client.");
            return false;
        }
        return true;
    }

    /**
     * Gets the data streams from the socket and saves them in their class level variables.
     *
     * @return true if the data streams were retrieved and set, false otherwise.
     */
    private boolean getAndSetDataStreams() {
        try {
            this.in = this.socket.getInputStream();
            this.out = this.socket.getOutputStream();
        } catch (IOException e) {
            Main.println("[Server] Failed to get data streams from client socket.");
            return false;
        }
        return true;
    }

    /**
     * Sends the server greeting message to the client.
     *
     * @return true if the message was sent, false otherwise.
     */
    private boolean sendGreetingMessage() {
        try {
            byte[] greetingBytes = (String.format("Hello and welcome to the server!!\n" +
                            "By default the server is in Echo mode. This means anything you\n" +
                            "send to the server will just get send right back to you!\n" +
                            "Well, most of the time... To learn how to disable this, send\n" +
                            "a message with content-type as \"%s\" and a String in the content as \"%s\".\n" +
                            "This will get you the help page that lists all available commands\n" +
                            "and what they do!",
                    COMMAND_CONTENT_TYPE,
                    HELP_COMMAND
            )).getBytes();
            Message greetingMessage = new Message(
                    OK,
                    this.location,
                    CONSOLE_TEXT_CONTENT_TYPE,
                    greetingBytes
            );
            this.sendMessage(greetingMessage);
        } catch (IOException e) {
            Main.println("[Server] Failed to send greeting");
            return false;
        }
        return true;
    }

    /**
     * The main loop of the connection.
     * <p>
     * Receives messages from the client and sends a response,
     * then waits for the next message from the client and repeats.
     */
    private void connectionLoop() {
        // The client terminates the connection when they are done.
        while (this.socket.isConnected()) {
            // Read request
            Message request;
            try {
                request = readIncoming();
            } catch (IOException e) {
                Main.println("[Server] Failed to read incoming message.");
                return;
            }

            // Handle request
            Message response = this.handleRequest(request);

            // Send response
            try {
                this.sendMessage(response);
            } catch (IOException e) {
                Main.println("[Server] Failed to send response message.");
                return;
            }
        }
    }

    /**
     * Creates a return message based on the client's request.
     *
     * @param request the message from the client.
     * @return a message to send to the client.
     */
    private Message handleRequest(Message request) {
        if (this.echo) {
            return this.handleEchoMessage(request);
        }

        // If not in echo mode, only commands can be used
        // So check to make sure they sent a command
        if (request.contentType.equals(COMMAND_CONTENT_TYPE)) {
            return this.handleCommands(request);
        }

        // Fail safe. Send Bad Request.
        return new Message(
                BAD_REQUEST,
                request.location,
                CONSOLE_TEXT_CONTENT_TYPE,
                "Unknown request.".getBytes()
        );
    }

    /**
     * Creates a message to return based on the request when echo is enabled.
     *
     * @param request the message from the client.
     * @return a message to send to the client.
     */
    private Message handleEchoMessage(Message request) {
        Message response;

        // Client tried sending a command while in echo
        if (request.contentType.equals(COMMAND_CONTENT_TYPE)) {
            String content = new String(request.content);
            switch (content) {
                // Help command ignores echo
                case HELP_COMMAND:
                    response = this.createHelpMessage();
                    break;
                // Toggle echo command ingores echo
                case TOGGLE_ECHO_COMMAND:
                    this.echo = !this.echo;
                    response = new Message(
                            OK,
                            this.location,
                            UPDATE_ECHO_CONTENT_TYPE,
                            ("" + this.echo).getBytes()
                    );
                    break;
                // All other commands do NOT ignore echo
                default:
                    response = this.createEchoMessage(request);
            }
        }
        // They didn't try to send a command
        else {
            response = this.createEchoMessage(request);
        }
        return response;
    }

    /**
     * Create a message the just repeats back the information in the request.
     *
     * @param request the message from the client.
     * @return a message that copies the information from the client's message.
     */
    private Message createEchoMessage(Message request) {
        return new Message(
                OK,
                this.location,
                CONSOLE_TEXT_CONTENT_TYPE,
                request.content
        );
    }

    private Message handleCommands(Message request) {
        // Check if input is a command and split the parts if true
        String[] parts;
        String baseInput = new String(request.content);
        String command = baseInput;
        String commandVariable = "";

        // Loop through all the possible commands and check if the input matches any of them!
        for (String testCommand : ALL_COMMANDS) {
            // Try splitting the input into its parts
            parts = this.splitCommand(baseInput, testCommand);

            // If the first part is different from the base input, the input was a command!
            if (!parts[0].equals(baseInput)) {
                command = parts[0]; // Reusing the input variable!
                commandVariable = parts[1];
                break; // We can leave the for-loop now since the input can't match two different commands.
            }
        }

        switch (command) {
            case TOGGLE_ECHO_COMMAND:
                this.echo = !this.echo;
                return new Message(
                        OK,
                        this.location,
                        UPDATE_ECHO_CONTENT_TYPE,
                        ("" + this.echo).getBytes());
            case HELP_COMMAND:
                return createHelpMessage();
            case LS_COMMAND:
                return new Message(
                        OK,
                        this.location,
                        CONSOLE_TEXT_CONTENT_TYPE,
                        this.getLs(request.location).getBytes()
                );
            case CD_COMMAND:
                boolean pass = this.cdLocation(commandVariable);
                if (pass) {
                    return new Message(
                            OK,
                            this.location,
                            NONE_CONTENT_TYPE,
                            new byte[0]
                    );
                } else {
                    return new Message(
                            BAD_REQUEST,
                            this.location,
                            CONSOLE_TEXT_CONTENT_TYPE,
                            String.format("\"%s\" is not a valid location", commandVariable).getBytes()
                    );
                }
            case DOWNLOAD_COMMAND:
                return handleDownload(commandVariable);
            default:
                String content = String.format("\"%s\" is not a recognized command.", command);
                return new Message(
                        BAD_REQUEST,
                        request.location,
                        CONSOLE_TEXT_CONTENT_TYPE,
                        content.getBytes()
                );
        }
    }

    /**
     * Separates a command and its variable if the input starts with the command.
     * <p>
     * If the given input does not match the specified command, then an array
     * of "[input, null]" is returned.
     *
     * @param input   the input from the user.
     * @param command the command to check.
     * @return the separated parts in the format: [command, commandVariable].
     */
    private String[] splitCommand(String input, String command) {
        // Default return value if the input was not the given command
        String[] values = new String[]{input, null};

        // Check if the input starts with the command
        if (input.startsWith(command)) {
            values[0] = command; // command
            values[1] = input.substring(command.length()); // commandVariable
        }

        return values;
    }

    /**
     * Creates a Help message containing information on all available commands.
     *
     * @return a message containing information about commands.
     */
    private Message createHelpMessage() {
        String content = String.format("Help Page!\n\n" +
                        "Commands -------- Send a message with \"%s\" as the content-type header, and\n" +
                        "                  the command as a string in the content payload.\n" +
                        "help ------------ Gets the help page. Returns text for the console.\n" +
                        "toggle echo ----- Toggles echo mode. Returns text for the console.\n" +
                        "ls -------------- Lists files and folders of the current location. Returns text for the console\n" +
                        "cd <path> ------- Changes the folder you are currently in. ex. \"cd alice\"\n" +
                        "download <file> - Downloads the specified file. ex. \"download snek.png\"\n" +
                        "\n" +
                        "Hope that helps!",
                COMMAND_CONTENT_TYPE
        );
        return new Message(
                OK,
                this.location,
                CONSOLE_TEXT_CONTENT_TYPE,
                content.getBytes()
        );
    }

    /**
     * Packs the requested file in a message.
     *
     * Returns a Bad Request message if the file cannot be found.
     *
     * @param requestedFilePath the file to put in the message.
     * @return a message with the requested file as the content.
     */
    private Message handleDownload(String requestedFilePath) {
        try {
            String filePath = "src/" + this.location + "/" + requestedFilePath;
            String ext = filePath.substring(filePath.lastIndexOf('.') + 1);

            String fileName = requestedFilePath.substring(requestedFilePath.lastIndexOf("/"));
            boolean isValidFile = false;
            for (String validFile : VALID_FILES) {
                if (fileName.equals(validFile)) {
                    isValidFile = true;
                    break;
                }
            }
            if (!isValidFile) {
                return new Message(
                        BAD_REQUEST,
                        this.location,
                        CONSOLE_TEXT_CONTENT_TYPE,
                        "Requested an invalid file option".getBytes()
                );
            }

            File file = new File(filePath);
            if (!file.exists()) {
                throw new FileNotFoundException();
            }
            byte[] content = this.readFileData(file);
            return new Message(
                    OK,
                    this.location,
                    FILE_CONTENT_TYPE + ext,
                    content
            );
        } catch (FileNotFoundException e) {
            return new Message(
                    BAD_REQUEST,
                    this.location,
                    CONSOLE_TEXT_CONTENT_TYPE,
                    String.format("Could not find file with path: %s", requestedFilePath).getBytes()
            );
        }
    }

    /**
     * Returns a String of the contained files and folders of the given location.
     * <p>
     * Example output: "[File] file1\n[Folder] folder1"
     *
     * @param location the directory to search.
     * @return a newline delimited list of the contained files and folders.
     */
    private String getLs(String location) {
        StringBuilder sb = new StringBuilder();
        File currentFile = new File("src/" + location);
        File[] files = currentFile.listFiles();

        if (files == null || files.length == 0) {
            return "";
        }
        for (int i = 0; i < files.length - 1; i++) {
            if (files[i].isDirectory()) {
                sb.append("[Folder] ").append(files[i].getName()).append("\n");
            } else {
                sb.append("[File] ").append(files[i].getName()).append("\n");
            }
        }
        int i = files.length - 1;
        if (files[i].isDirectory()) {
            sb.append("[Folder] ").append(files[i].getName());
        } else {
            sb.append("[File] ").append(files[i].getName());
        }
        return sb.toString();
    }

    /**
     * Changes the current location of the client.
     *
     * @param path the desired.
     * @return true if the location changed, false otherwise.
     */
    private boolean cdLocation(String path) {
        try {
            StringBuilder newLocation = new StringBuilder(this.location);

            String[] parts = path.split("/");

            if (parts[0].length() == 0) {
                return true;
            }

            for (String part : parts) {
                if (part.equals("..")) {
                    newLocation = new StringBuilder(newLocation.substring(0, newLocation.lastIndexOf("/")));
                } else {
                    String testPath = "src/" + newLocation + "/" + part;
                    File newFile = new File(testPath);
                    if (!newFile.exists()) {
                        return false;
                    }
                    newLocation.append("/").append(part);
                }
            }
            this.location = newLocation.toString();
            return true;
        }
        // This is really only catching if lastIndexOf returns a -1
        catch (RuntimeException ex) {
            Main.println(ex.getMessage());
            return false;
        }
    }

    // Read and Send

    /**
     * Sends a {@link Message} through the {@link Socket}.
     *
     * @param message the Message to send.
     * @throws IOException if there was an error while writing to the Socket's {@link OutputStream}.
     */
    private void sendMessage(Message message) throws IOException {
        // Data bytes
        byte[] statusCodeBytes = this.convertIntToBytes(message.statusCode);
        byte[] locationBytes = message.location.getBytes();
        byte[] contentTypeBytes = message.contentType.getBytes();

        // Header bytes
        byte[] locationLengthBytes = this.convertIntToBytes(locationBytes.length);
        byte[] contentTypeLengthBytes = this.convertIntToBytes(contentTypeBytes.length);
        byte[] contentLengthBytes = this.convertIntToBytes(message.content.length);

        // Send headers
        out.write(statusCodeBytes);
        out.write(locationLengthBytes);
        out.write(contentTypeLengthBytes);
        out.write(contentLengthBytes);

        // Send data
        out.write(locationBytes);
        out.write(contentTypeBytes);
        out.write(message.content);

        // Force buffer to send payload
        out.flush();
    }

    /**
     * Waits to receive data from the {@link Socket}
     * and parses it into a {@link Message} object once received.
     *
     * @return a Message from the Socket.
     */
    private Message readIncoming() throws IOException {
        // Read in the Header sizes
        byte[] statusCodeBytes = this.readBytesIn(STATUS_CODE_BYTES);
        byte[] locationLengthBytes = this.readBytesIn(LOCATION_LENGTH_BYTES);
        byte[] contentTypeLengthBytes = this.readBytesIn(CONTENT_TYPE_LENGTH_BYTES);
        byte[] contentLengthBytes = this.readBytesIn(CONTENT_LENGTH_BYTES);

        // Convert bytes to ints
        int statusCode = this.convertBytesToInt(statusCodeBytes);
        int locationLength = this.convertBytesToInt(locationLengthBytes);
        int contentTypeLength = this.convertBytesToInt(contentTypeLengthBytes);
        int contentLength = this.convertBytesToInt(contentLengthBytes);

        // Read in data
        byte[] locationBytes = this.readBytesIn(locationLength);
        byte[] contentTypeBytes = this.readBytesIn(contentTypeLength);
        byte[] contentBytes = this.readBytesIn(contentLength);

        // Convert bytes to Strings, except data (which may be a file)
        String location = new String(locationBytes);
        String contentType = new String(contentTypeBytes);

        // Create Message object
        return new Message(statusCode, location, contentType, contentBytes);
    }

    // Utility

    /**
     * Converts a byte array of size 4 into an int.
     * <p>
     * If the array is not of size 4 a {@link RuntimeException}
     * will be thrown.
     *
     * @param bytes the byte array representing an int.
     * @return the int represented by the given byte array.
     */
    private int convertBytesToInt(byte[] bytes) {
        // Must pass in array of length 4
        if (bytes.length != 4) {
            throw new RuntimeException("[convertBytesToInt] passed in byte array not of size 4.");
        }

        // Convert bytes to BigInteger
        BigInteger bigInteger = new BigInteger(bytes);

        // Convert BigInteger to int
        return bigInteger.intValue();
    }

    /**
     * Converts an int into an array of bytes.
     * <p>
     * Uses a default number of "4" bytes to represent an int.
     *
     * @param num the int to convert.
     * @return an array of bytes representing the number.
     */
    private byte[] convertIntToBytes(int num) {
        final int defaultBytesInInt = 4;
        return ByteBuffer.allocate(defaultBytesInInt).putInt(num).array();
    }

    /**
     * Reads in data from the {@link InputStream} of the given {@link Socket}.
     * <p>
     * If the InputStream can not be successfully retrieved from the Socket or
     * there is an error while reading from the InputStream, an empty byte array
     * will be returned and the error printed to the console.
     *
     * @param amountOfBytes the number of bytes to read.
     * @return a byte array containing the data read in from the socket.
     */
    private byte[] readBytesIn(int amountOfBytes) throws IOException {
        // Return empty array if no data needs to be read in.
        if (amountOfBytes <= 0) {
            return new byte[0];
        }

        // Create byte array
        byte[] bytesIn = new byte[amountOfBytes];

        // Read from the InputStream
        try {
            // Read in the bytes to fill the array
            int bytesReadIn = in.read(bytesIn);

            if (bytesReadIn != amountOfBytes) {
                throw new IOException("Missing bytes.");
            }
        } catch (IOException e) {
            throw new IOException("[readBytesIn] Failed to read from Input Stream.");
        }

        // Return the bytes
        return bytesIn;
    }

    /**
     * Reads in the data stored in a {@link File} and returns it
     * as a byte[].
     * <p>
     * If the files is not found OR there is a problem reading the file,
     * an error message will be printed to the console and an empty array
     * will be returned.
     *
     * @param file the File to read in.
     * @return a byte array containing the data read in from the File
     */
    private byte[] readFileData(File file) {
        // Create variables
        FileInputStream fileInputStream;
        int fileLength = (int) file.length(); // Number of bytes in the file
        byte[] fileData = new byte[fileLength]; // The final data byte array

        try {
            // Create the FileInputStream
            fileInputStream = new FileInputStream(file);

            // Read in the file to fill in the array
            int bytesReadIn = fileInputStream.read(fileData);
            if (bytesReadIn != fileLength) {
                throw new IOException("Missing bytes");
            }
        } catch (FileNotFoundException e) {
            // File was not found
            System.err.printf("[readFileData] File Not Found: %s%n", file.getName());
            return new byte[0];
        } catch (IOException e) {
            // Failed to read the file
            System.err.printf("[readFileData] Failed To Read File: %s%n", file.getName());
            e.printStackTrace();
            return new byte[0];
        }

        return fileData;
    }

    // Close connection

    /**
     * Closes the {@link Socket}.
     */
    public void close() {
        Main.println("[Server] closing connection using port: " + this.socket.getPort());
        try {
            this.socket.close();
            this.server.removeClientHandler(this);
        } catch (IOException ioException) {
            System.err.println("[Server] Failed to close connection.");
        }
        Thread.currentThread().interrupt();
    }

}