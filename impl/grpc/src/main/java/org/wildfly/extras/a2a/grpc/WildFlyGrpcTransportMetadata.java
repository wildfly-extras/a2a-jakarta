package org.wildfly.extras.a2a.grpc;

import org.a2aproject.sdk.server.TransportMetadata;
import org.a2aproject.sdk.spec.TransportProtocol;

public class WildFlyGrpcTransportMetadata implements TransportMetadata {
    @Override
    public String getTransportProtocol() {
        return TransportProtocol.GRPC.asString();
    }
}
