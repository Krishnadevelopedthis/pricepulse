import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/**
 * TCP: connection-oriented, reliable, ordered byte stream (Socket + ServerSocket).
 * Flow: start server -> accept connection -> receive message -> send response -> close.
 * This is a diagnostic/teaching module. PricePulse's REST API does not use raw sockets: HTTP itself runs over TCP.
 */
public class TcpDemo {
    public static void main(String[] args) throws Exception {
        try (ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) { // port 0 = any free port
            int port = server.getLocalPort();
            System.out.println("[server] listening on 127.0.0.1:" + port);

            Thread serverThread = new Thread(() -> {
                try (Socket conn = server.accept();  // blocks until a client connects
                     BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                     PrintWriter out = new PrintWriter(new OutputStreamWriter(conn.getOutputStream(), StandardCharsets.UTF_8), true)) {
                    System.out.println("[server] accepted connection from " + conn.getInetAddress().getHostAddress());
                    String msg = in.readLine();
                    System.out.println("[server] received: " + msg);
                    out.println("ACK price-check request: " + msg.toUpperCase());
                    System.out.println("[server] response sent, closing connection");
                } catch (IOException e) {
                    System.err.println("[server] error: " + e.getMessage());
                }
            }, "tcp-server");
            serverThread.start();

            try (Socket client = new Socket()) {
                client.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 2000);
                client.setSoTimeout(2000);
                PrintWriter out = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                out.println("check product 42");
                System.out.println("[client] got: " + in.readLine());
            }
            serverThread.join();
        }
        System.out.println("TCP demo complete.");
    }
}
