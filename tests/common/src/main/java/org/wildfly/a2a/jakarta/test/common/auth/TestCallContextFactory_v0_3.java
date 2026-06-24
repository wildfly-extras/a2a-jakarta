package org.wildfly.a2a.jakarta.test.common.auth;

import java.util.HashSet;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import io.grpc.stub.StreamObserver;
import org.a2aproject.sdk.compat03.conversion.A2AProtocol_v0_3;
import org.a2aproject.sdk.compat03.transport.grpc.context.GrpcContextKeys_v0_3;
import org.a2aproject.sdk.compat03.transport.grpc.handler.CallContextFactory_v0_3;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.User;

@ApplicationScoped
public class TestCallContextFactory_v0_3 implements CallContextFactory_v0_3 {

    @Override
    public <V> ServerCallContext create(StreamObserver<V> responseObserver) {
        User user = GrpcCallContextHelper.resolveUser();
        Map<String, Object> state = GrpcCallContextHelper.buildState(
                responseObserver,
                GrpcContextKeys_v0_3.METADATA_KEY,
                GrpcContextKeys_v0_3.METHOD_NAME_KEY,
                GrpcContextKeys_v0_3.PEER_INFO_KEY);

        return new ServerCallContext(user, state, new HashSet<>(), A2AProtocol_v0_3.PROTOCOL_VERSION);
    }
}
