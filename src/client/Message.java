package client;

/**
 * Represents a message following the defined protocol for the CS255 programming assignment.
 */
public class Message {

    /**
     * The status code for the message.
     *
     * 200 = OK
     * 400 = Bad request
     */
    public final int statusCode;

    /**
     * Your current location in the server.
     */
    public final String location;

    /**
     * The content type of the content.
     *
     * Possible values are:
     * console/text -- a String to be printed to the console.
     * console/command -- a command for the server.
     * file/<extension> -- a File to be saved.
     * None -- nothing to do.
     */
    public final String contentType;

    /**
     * The content of the message.
     *
     * May be an empty byte array of length 0.
     */
    public final byte[] content;

    /**
     * A message to send to the server, or received from the server.
     *
     * @param statusCode -- client always sends OK.
     * @param location -- client always sends location server is aware of.
     * @param contentType -- the content type of the content.
     * @param content -- the content of the message, may be an empty byte array of size 0.
     */
    public Message(int statusCode, String location, String contentType, byte[] content) {
        this.statusCode = statusCode;
        this.location = location;
        this.contentType = contentType;
        this.content = content;
    }
}
