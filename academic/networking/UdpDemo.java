import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * UDP: connectionless datagrams (DatagramSocket + DatagramPacket).
 *
 * TCP vs UDP:
 *   TCP - connection setup, reliable, ordered, retransmits, byte stream. Used by HTTP, so by the REST API.
 *   UDP - no connection, no delivery/ordering guarantee, message boundaries preserved, lower overhead.
 * PricePulse needs reliable delivery of prices, so production traffic uses HTTP/TCP; UDP is only demonstrated here.
 */
public class UdpDemo {
    public static void main(String[] args) throws Exception {
        try (DatagramSocket server = new DatagramSocket(0, InetAddress.getLoopbackAddress())) {
            int port = server.getLocalPort();
            System.out.println("[server] UDP listening on 127.0.0.1:" + port);

            Thread serverThread = new Thread(() -> {
                try {
                    byte[] buf = new byte[512];
                    DatagramPacket request = new DatagramPacket(buf, buf.length);
                    server.receive(request);  // blocks for one datagram
                    String msg = new String(request.getData(), 0, request.getLength(), StandardCharsets.UTF_8);
                    System.out.println("[server] received '" + msg + "' from port " + request.getPort());
                    byte[] reply = ("PONG " + msg).getBytes(StandardCharsets.UTF_8);
                    server.send(new DatagramPacket(reply, reply.length, request.getAddress(), request.getPort()));
                } catch (Exception e) {
                    System.err.println("[server] error: " + e.getMessage());
                }
            }, "udp-server");
            serverThread.start();

            try (DatagramSocket client = new DatagramSocket()) {
                client.setSoTimeout(2000); // UDP gives no delivery guarantee, so a client must time out
                byte[] data = "ping-price-service".getBytes(StandardCharsets.UTF_8);
                client.send(new DatagramPacket(data, data.length, InetAddress.getLoopbackAddress(), port));
                DatagramPacket response = new DatagramPacket(new byte[512], 512);
                client.receive(response);
                System.out.println("[client] got: " + new String(response.getData(), 0, response.getLength(), StandardCharsets.UTF_8));
            }
            serverThread.join();
        }
        System.out.println("UDP demo complete.");
    }
}
