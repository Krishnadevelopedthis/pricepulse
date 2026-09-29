import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Network diagnostic using InetAddress. Prints nothing sensitive by default: the machine's own
 * hostname/IP appear only with --verbose. Usage: java InetDiagnostics [host] [--verbose]
 */
public class InetDiagnostics {
    public static void main(String[] args) throws Exception {
        boolean verbose = false;
        String host = "localhost";
        for (String a : args) { if (a.equals("--verbose")) verbose = true; else host = a; }

        InetAddress loop = InetAddress.getLoopbackAddress();
        describe("loopback", loop);
        System.out.println("loopback reachable within 1s: " + loop.isReachable(1000));

        try {
            InetAddress[] all = InetAddress.getAllByName(host);
            System.out.println("resolved '" + host + "' to " + all.length + " address(es)");
            for (InetAddress a : all) describe(host, a);
        } catch (UnknownHostException e) {
            System.out.println("could not resolve '" + host + "': " + e.getMessage());
        }

        InetAddress raw = InetAddress.getByAddress(new byte[]{(byte) 192, (byte) 168, 1, 10});
        describe("literal 192.168.1.10", raw);
        if (verbose) {
            InetAddress me = InetAddress.getLocalHost();
            System.out.println("local host: " + me.getHostName() + " / " + me.getHostAddress());
        } else {
            System.out.println("(local hostname/IP hidden; use --verbose to print them)");
        }
    }

    static void describe(String label, InetAddress a) {
        System.out.printf("  %-22s %-15s %s loopback=%b siteLocal=%b linkLocal=%b multicast=%b%n",
                label, a.getHostAddress(), a instanceof Inet4Address ? "IPv4" : "IPv6",
                a.isLoopbackAddress(), a.isSiteLocalAddress(), a.isLinkLocalAddress(), a.isMulticastAddress());
    }
}
