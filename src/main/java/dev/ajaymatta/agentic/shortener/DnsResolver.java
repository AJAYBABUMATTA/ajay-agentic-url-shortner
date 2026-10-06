package dev.ajaymatta.agentic.shortener;

import java.net.InetAddress;
import java.net.UnknownHostException;

public interface DnsResolver {
    InetAddress[] resolve(String host) throws UnknownHostException;
}
