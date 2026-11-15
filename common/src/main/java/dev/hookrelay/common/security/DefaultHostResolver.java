package dev.hookrelay.common.security;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class DefaultHostResolver implements HostResolver {

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }
}
