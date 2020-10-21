package client;

import main.Main;

import java.io.*;
import java.math.BigInteger;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Scanner;

/**
 * Connects to a server using the CS255-FA20 protocol.
 * <p>
 * Input from the user is received through the console using {@link Scanner} and {@link System#in}
 * Allows the user to send multiple messages to the server until they wish to exit.
 */
public class Client implements Runnable {

    /**
     * This may need to change depending on the IDE. But in general, this is where
     * downloaded files will be saved to.
     */
    private static final String SAVE_FOLDER_LOCATION = "src/client/";

    // Number of bytes for headers. Since they all represent an int, they are all exactly 4 bytes.
    private static final int STATUS_CODE_BYTES = 4;
    private static final int LOCATION_LENGTH_BYTES = 4;
    private static final int CONTENT_TYPE_LENGTH_BYTES = 4;
    private static final int CONTENT_LENGTH_BYTES = 4;

    // ## Content types
    // From Server Only
    private static final String UPDATE_ECHO_CONTENT_TYPE = "update/echo"; // String representing is echo is enabled.
    private static final String FILE_CONTENT_TYPE = "file/"; // Content to be saved to a file.
    private static final String NONE_CONTENT_TYPE = "None"; // No action necessary, no content either.
    // From Client Only
    private static final String COMMAND_CONTENT_TYPE = "console/command"; // String representing a command.
    // Shared
    private static final String CONSOLE_TEXT_CONTENT_TYPE = "console/text"; // String to be printed to the console.

    // Console commands
    private static final String HELP_COMMAND = "help"; // Gets the help page.
    private static final String TOGGLE_ECHO_COMMAND = "toggle echo"; // Turns echo on or off.
    private static final String LS_COMMAND = "ls"; // Lists all files in the current server folder location.
    private static final String CD_COMMAND = "cd "; // Changes location of the server folder location.
    private static final String DOWNLOAD_COMMAND = "download "; // Downloads a file from the server
    // All the commands in a nicely packed little array :)
    private static final String[] ALL_COMMANDS = new String[]{
            HELP_COMMAND, TOGGLE_ECHO_COMMAND, LS_COMMAND, CD_COMMAND, DOWNLOAD_COMMAND
    };

    // Status codes
    private static final int OK = 200;
    private static final int BAD_REQUEST = 400;

    // Socket parts
    private final String host; // The server IP address.
    private final int port; // The server port to connect to.
    private Socket socket; // The connection to the server.
    private InputStream in; // data stream coming from server.
    private OutputStream out; // data stream going to server.

    // Location in server
    private String location; // Follows linux model (kind of): e.x. "users/alice/downloads"

    // In echo mode
    private boolean echo = true; // If true, most input typed in the console and sent to the server will be echoed back.

    // Name of file being downloaded
    private String fileName = null; // The server does not send the name of the file.

    /**
     * Creates a Client object to connect to a server.
     * <p>
     * Must call the {@link #run()} method to start the connection.
     * This constructor only prepares for the connection to be started,
     * but does not actually make the connection.
     *
     * @param host the IP address of the server.
     * @param port the port to use for the server.
     */
    public Client(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /**
     * Starts the connection with the server and begins communication.
     */
    @Override
    public void run() {
        // Start the connection and get the data streams
        if (!this.initConnection()) {
            this.close();
            return;
        }

        // Handle the greeting message
        if (!this.handleGreetingMessageAndSetLocation()) {
            this.close();
            return;
        }

        // Send and receive messages until finished
        this.connectionLoop();

        // End the session
        this.close();
    }

    /**
     * Opens a connection to the server and gets the {@link InputStream} and
     * {@link OutputStream} of the connection.
     *
     * @return true if the connection and streams were established successfully, false otherwise.
     */
    private boolean initConnection() {
        try {
            // Establish the connection
            this.socket = new Socket(this.host, this.port);
            // Get the data streams
            this.in = this.socket.getInputStream();
            this.out = this.socket.getOutputStream();
        } catch (IOException e) {
            // There was a problem either making the connection or getting the data streams
            Main.println("[Client] Failed to start Socket and get the data streams.");
            return false;
        }
        return true;
    }

    /**
     * Gets the greeting message from the server and prints it. Also sets
     * the location the server says the client is at.
     */
    private boolean handleGreetingMessageAndSetLocation() {
        // Get the server greeting message. We know the content will always be text that
        // can be printed to the console!
        Message greetingMessage = readIncoming();

        // If the server did not send OK, return early.
        if (greetingMessage.statusCode != OK) {
            Main.println("[Client] Failed to get greeting message from server.");
            return false;
        }

        // Print the greeting.
        Main.println("[Client] Server Greeting:");
        Main.println(new String(greetingMessage.content));

        // Set the location given to us in the greeting message!
        this.location = greetingMessage.location;

        // Everything went OK!
        return true;
    }

    /**
     * Handles the user's input to send messages to the server
     * and handles the responses until the user is done.
     * <p>
     * A call to this method should immediately be followed by {@link Client#close()}.
     */
    private void connectionLoop() {
        // Create console Scanner!
        Scanner scanner = new Scanner(System.in);

        // Prompt for input
        String input;
        Main.println("[Client] type \"exit\" to end the connection.");

        // I call this in two different places, so I made it a method that way
        // I only have a single place to change if I need to!
        this.printInputPrompt();

        // Fancy input handler loop. I set a variable AND perform a boolean expression, neat toast!
        while (!(input = scanner.nextLine()).equals("exit")) {
            // Create message to send to server from input
            Message message = createMessageFromInput(input);

            // Send message and get the response
            Message response;
            try {
                response = sendMessage(message);
            } catch (IOException e) {
                Main.println("[Client] Failed to send message to server.");
                return;
            }

            // Handle the server's response
            this.handleResponse(response);

            // Prompt for input again! Remember that getting the input with the scanner
            // happens inside the while-loop parenthesis! One less step to do here!
            this.printInputPrompt();
        }
    }

    /**
     * Prints the input prompt to the console for the user.
     */
    private void printInputPrompt() {
        Main.print(String.format("[Client] %s > ", this.location));
    }

    /**
     * Creates a {@link Message} from the user's input.
     *
     * @param input the input from the user.
     * @return a message to send to the server.
     */
    private Message createMessageFromInput(String input) {
        // Message to send to server
        Message message = null;

        // Check if input is a command and split the parts if true
        String commandVariable = "";
        String[] parts;
        String baseInput = input;

        // Loop through all the possible commands and check if the input matches any of them!
        for (String command : ALL_COMMANDS) {
            // Try splitting the input into its parts
            parts = this.splitCommand(baseInput, command);

            // If the first part is different from the base input, the input was a command!
            if (!parts[0].equals(baseInput)) {
                input = parts[0]; // Reusing the input variable!
                commandVariable = parts[1];
                break; // We can leave the for-loop now since the input can't match two different commands.
            }
        }

        // Handle commands
        // Some commands ignore the echo mode, such as the "help" command.
        boolean ignoreEcho = false;
        switch (input) {
            // Tell the server to change the echo mode
            case TOGGLE_ECHO_COMMAND:
                message = new Message(
                        OK,
                        this.location,
                        COMMAND_CONTENT_TYPE,
                        TOGGLE_ECHO_COMMAND.getBytes()
                );
                // This command ignores the echo mode!
                ignoreEcho = true;
                break;
            // Get the help page from the server
            case HELP_COMMAND:
                message = new Message(
                        OK,
                        this.location,
                        COMMAND_CONTENT_TYPE,
                        input.getBytes()
                );
                // This command ignores the echo mode!
                ignoreEcho = true;
                break;
            // List all files/folders in current location
            case LS_COMMAND:
                message = new Message(
                        OK,
                        this.location,
                        COMMAND_CONTENT_TYPE,
                        input.getBytes()
                );
                break;
            // Change the current location in the server
            case CD_COMMAND:
                message = new Message(
                        OK,
                        this.location,
                        COMMAND_CONTENT_TYPE,
                        (CD_COMMAND + commandVariable).getBytes()
                );
                break;
            // Download a file from the server
            case DOWNLOAD_COMMAND:
                // Get just the file name
                this.fileName = commandVariable.substring(commandVariable.lastIndexOf("/"));

                message = new Message(
                        OK,
                        this.location,
                        COMMAND_CONTENT_TYPE,
                        (DOWNLOAD_COMMAND + commandVariable).getBytes()
                );
                break;
        }

        // A command that doesn't ignore echo was used while echo is enabled
        if (!ignoreEcho && this.echo) {
            // Send the input as plain text
            message = new Message(
                    OK,
                    this.location,
                    CONSOLE_TEXT_CONTENT_TYPE,
                    baseInput.getBytes()
            );
        }

        // Fail safe. Just send the input as a command.
        // The server should interpret it as an error and send back a message.
        // This lets both client and server continue without erring out the socket.
        if (message == null) {
            message = new Message(
                    OK,
                    location,
                    COMMAND_CONTENT_TYPE,
                    input.getBytes()
            );
        }

        return message;
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
     * Handle the response from the server.
     *
     * @param message the message from the server.
     */
    private void handleResponse(Message message) {
        // Update location
        this.location = message.location;

        // Sent a bad request
        if (message.statusCode == BAD_REQUEST) {
            Main.println("[Client] Sent a bad request.");
            Main.println(new String(message.content));
            return;
        }

        // Correct contentType since server will include file extension
        String contentType = message.contentType;
        if (contentType.startsWith(FILE_CONTENT_TYPE)) {
            contentType = FILE_CONTENT_TYPE;
        }

        switch (contentType) {
            case UPDATE_ECHO_CONTENT_TYPE:
                String variable = new String(message.content);
                this.echo = variable.equals("true");
                Main.println("Echo: " + this.echo);
                break;
            // Text for console
            case CONSOLE_TEXT_CONTENT_TYPE:
                String content = new String(message.content);
                Main.println(content);
                break;
            // Nothing to do
            case NONE_CONTENT_TYPE:
                break;
            // File to save
            case FILE_CONTENT_TYPE:
                this.saveFile(this.fileName, message.content);
                this.fileName = null;
                break;
            // Unknown content-type
            default:
                Main.println(String.format("[Client] Unknown content-type: \"%s\"", message.contentType));
        }
    }

    // Read and Send

    /**
     * Sends a {@link server.Message} through the {@link Socket}.
     *
     * @param message the Message to send.
     * @throws IOException if there was an error while writing to the Socket's {@link OutputStream}.
     */
    private Message sendMessage(Message message) throws IOException {
        // Data bytes
        byte[] statusCodeBytes = this.convertIntToBytes(message.statusCode, STATUS_CODE_BYTES);
        byte[] locationBytes = message.location.getBytes();
        byte[] contentTypeBytes = message.contentType.getBytes();

        // Header bytes
        byte[] locationLengthBytes = this.convertIntToBytes(locationBytes.length, LOCATION_LENGTH_BYTES);
        byte[] contentTypeLengthBytes = this.convertIntToBytes(contentTypeBytes.length, CONTENT_TYPE_LENGTH_BYTES);
        byte[] contentLengthBytes = this.convertIntToBytes(message.content.length, CONTENT_LENGTH_BYTES);

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

        // Get the response from the server
        return this.readIncoming();
    }

    /**
     * Waits to receive data from the {@link Socket}
     * and parses it into a {@link server.Message} object once received.
     *
     * @return a Message from the Socket.
     */
    private Message readIncoming() {
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
     *
     * @param num           the int to convert.
     * @param numberOfBytes the number of bytes to convert the num into.
     * @return an array of bytes representing the number.
     */
    private byte[] convertIntToBytes(int num, int numberOfBytes) {
        return ByteBuffer.allocate(numberOfBytes).putInt(num).array();
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
    private byte[] readBytesIn(int amountOfBytes) {
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
            System.err.println("[readBytesIn] Failed to read from Input Stream.");
            e.printStackTrace();
            return new byte[0];
        }

        // Return the bytes
        return bytesIn;
    }

    /**
     * Saves the byte array into a file.
     * <p>
     * Will create the file at the static path set in the class.
     *
     * @param fileName the name of the file to save into.
     * @param content  the data to save in the file.
     */
    private void saveFile(String fileName, byte[] content) {
        // Create the File object
        File file = new File(SAVE_FOLDER_LOCATION + fileName);

        // Create a FileOutputStream
        FileOutputStream fileOutputStream;
        try {
            fileOutputStream = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            Main.println("[Client] Failed to create output stream using file: " + file.getName());
            e.printStackTrace();
            return;
        }

        // Write the content to the file
        try {
            fileOutputStream.write(content);
        } catch (IOException e) {
            Main.println("Failed to write to file with name: " + file.getName());
            e.printStackTrace();
        }

        // Close the file
        try {
            fileOutputStream.flush();
            fileOutputStream.close();
        } catch (IOException e) {
            Main.println("Failed to close the file with name: " + file.getName());
            e.printStackTrace();
        }
    }

    // Close connection

    /**
     * Close the connection.
     */
    public void close() {
        Main.println("[Client] Closing connection.");
        try {
            this.socket.close();
        } catch (IOException e) {
            Main.println("[Client] Failed to close socket.");
            e.printStackTrace();
        }
    }
}
