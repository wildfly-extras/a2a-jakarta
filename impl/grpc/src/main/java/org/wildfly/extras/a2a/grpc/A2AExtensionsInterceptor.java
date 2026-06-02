package org.wildfly.extras.a2a.grpc;

import org.a2aproject.sdk.common.A2AHeaders;
import org.a2aproject.sdk.transport.grpc.context.GrpcContextKeys;

import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;

/**
 * gRPC server interceptor that extracts A2A protocol headers from request
 * metadata and stores them in the gRPC {@link Context} for use by
 * {@link org.a2aproject.sdk.transport.grpc.handler.GrpcHandler}.
 *
 * <p>WildFly's gRPC subsystem discovers {@link ServerInterceptor} implementations
 * in the deployment and applies them automatically.
 */
public class A2AExtensionsInterceptor implements ServerInterceptor {

    private static final Metadata.Key<String> VERSION_KEY = Metadata.Key.of(
            A2AHeaders.A2A_VERSION.toLowerCase(), Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> EXTENSIONS_KEY = Metadata.Key.of(
            A2AHeaders.A2A_EXTENSIONS.toLowerCase(), Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata metadata, ServerCallHandler<ReqT, RespT> next) {
        String version = metadata.get(VERSION_KEY);
        String extensions = metadata.get(EXTENSIONS_KEY);

        Context context = Context.current()
                .withValue(GrpcContextKeys.METADATA_KEY, metadata)
                .withValue(GrpcContextKeys.GRPC_METHOD_NAME_KEY, call.getMethodDescriptor().getFullMethodName())
                .withValue(GrpcContextKeys.METHOD_NAME_KEY,
                        GrpcContextKeys.METHOD_MAPPING.get(call.getMethodDescriptor().getBareMethodName()));

        if (version != null) {
            context = context.withValue(GrpcContextKeys.VERSION_HEADER_KEY, version);
        }
        if (extensions != null) {
            context = context.withValue(GrpcContextKeys.EXTENSIONS_HEADER_KEY, extensions);
        }

        return Contexts.interceptCall(context, call, metadata, next);
    }
}
