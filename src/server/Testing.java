package server;

import java.nio.ByteBuffer;

public class Testing {

    public static void main(String[] args) {
        int number = 29375;
        byte[] bytes = ByteBuffer.allocate(4).putInt(number).array();

        StringBuilder message = new StringBuilder();
        for (byte aByte : bytes) {
            String binary = String.format("%8s", Integer.toBinaryString(aByte & 0xFF))
                    .replace(' ', '0');
            message.append(binary);
        }
        System.out.println(message);
    }
}
