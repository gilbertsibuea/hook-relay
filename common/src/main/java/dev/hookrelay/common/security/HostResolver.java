package dev.hookrelay.common.security;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Resolves hostnames for SSRF checks. Injected so tests can stub DNS.
 */
@FunctionalInterface
public interface HostResolver {

    InetAddress[] resolve(String host) throws UnknownHostException;
}
