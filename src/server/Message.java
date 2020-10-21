package server;

public class Message {

    public final int statusCode;
    public final String location;
    public final String contentType;
    public final byte[] content;

    public Message(int statusCode, String location, String contentType, byte[] content) {
        this.statusCode = statusCode;
        this.location = location;
        this.contentType = contentType;
        this.content = content;
    }
}
