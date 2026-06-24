package org.wildfly.a2a.jakarta.test.common.auth;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import io.grpc.stub.StreamObserver;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.User;
import org.a2aproject.sdk.server.extensions.A2AExtensions;
import org.a2aproject.sdk.transport.grpc.context.GrpcContextKeys;
import org.a2aproject.sdk.transport.grpc.handler.CallContextFactory;

@ApplicationScoped
public class TestCallContextFactory implements CallContextFactory {

    @Override
    public <V> ServerCallContext create(StreamObserver<V> responseObserver) {
        User user = GrpcCallContextHelper.resolveUser();
        Map<String, Object> state = GrpcCallContextHelper.buildState(
                responseObserver,
                GrpcContextKeys.METADATA_KEY,
                GrpcContextKeys.GRPC_METHOD_NAME_KEY,
                GrpcContextKeys.PEER_INFO_KEY);

        String requestedVersion = null;
        try {
            requestedVersion = GrpcContextKeys.VERSION_HEADER_KEY.get();
        } catch (Exception e) {
            // Context key not available in this call
        }

        Set<String> requestedExtensions = new HashSet<>();
        try {
            String extensionsHeader = GrpcContextKeys.EXTENSIONS_HEADER_KEY.get();
            if (extensionsHeader != null) {
                requestedExtensions = A2AExtensions.getRequestedExtensions(List.of(extensionsHeader));
            }
        } catch (Exception e) {
            // Context key not available in this call
        }

        return new ServerCallContext(user, state, requestedExtensions, requestedVersion);
    }
}
