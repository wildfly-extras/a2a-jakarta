package org.wildfly.extras.a2a.rest.compat03;

import org.a2aproject.sdk.server.TransportMetadata;
import org.a2aproject.sdk.compat03.spec.TransportProtocol_v0_3;

public class WildFlyRestTransportMetadata_v0_3 implements TransportMetadata {
    @Override
    public String getTransportProtocol() {
        return TransportProtocol_v0_3.HTTP_JSON.asString();
    }
}
