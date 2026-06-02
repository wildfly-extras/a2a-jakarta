package org.wildfly.extras.a2a.rest.multiversion;

import org.a2aproject.sdk.server.TransportMetadata;
import org.a2aproject.sdk.spec.TransportProtocol;

public class WildFlyRestTransportMetadata implements TransportMetadata {
    @Override
    public String getTransportProtocol() {
        return TransportProtocol.HTTP_JSON.asString();
    }
}
