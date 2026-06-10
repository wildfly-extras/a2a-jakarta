package org.wildfly.a2a.jakarta.rest;

import org.a2aproject.sdk.server.TransportMetadata;
import org.a2aproject.sdk.spec.TransportProtocol;

public class WildFlyRestTransportMetadata implements TransportMetadata {
    @Override
    public String getTransportProtocol() {
        return TransportProtocol.HTTP_JSON.asString();
    }
}
