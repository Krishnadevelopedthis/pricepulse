package com.pricepulse.util;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/** SSRF guard: the server only fetches URLs that resolve to public addresses. */
public final class UrlSafety {
    private UrlSafety() {}

    public static void assertPublic(URI uri) {
        try {
            for (InetAddress a : InetAddress.getAllByName(uri.getHost())) {
                if (isNonPublic(a)) {
                    throw new InvalidPriceException("url", "URL points to a private or local address.");
                }
            }
        } catch (UnknownHostException e) {
            throw new InvalidPriceException("url", "Host could not be resolved.");
        }
    }

    public static boolean isNonPublic(InetAddress a) {
        if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress()
                || a.isSiteLocalAddress() || a.isMulticastAddress()) {
            return true;
        }
        byte[] b = a.getAddress();
        if (a instanceof Inet4Address) {
            int b0 = b[0] & 0xff, b1 = b[1] & 0xff;
            return b0 == 0 || (b0 == 100 && b1 >= 64 && b1 <= 127); // "this" network, carrier-grade NAT
        }
        if (a instanceof Inet6Address) {
            return (b[0] & 0xfe) == 0xfc; // unique local fc00::/7
        }
        return false;
    }
}
