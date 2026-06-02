package org.wildfly.extras.a2a.grpc;

import java.util.concurrent.Executor;
import java.util.concurrent.Flow;
import java.util.function.Supplier;

import org.a2aproject.sdk.jsonrpc.common.wrappers.ListTasksResult;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.requesthandlers.RequestHandler;
import org.a2aproject.sdk.spec.A2AError;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.DeleteTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.GetTaskPushNotificationConfigParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsParams;
import org.a2aproject.sdk.spec.ListTaskPushNotificationConfigsResult;
import org.a2aproject.sdk.spec.ListTasksParams;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TaskIdParams;
import org.a2aproject.sdk.spec.TaskPushNotificationConfig;
import org.a2aproject.sdk.spec.TaskQueryParams;
import org.a2aproject.sdk.transport.grpc.handler.CallContextFactory;
import org.a2aproject.sdk.transport.grpc.handler.GrpcHandler;

/**
 * WildFly gRPC Handler that uses static cache for CDI beans.
 *
 * The WildFly gRPC subsystem instantiates this class directly using
 * reflection and the default constructor, bypassing CDI completely.
 *
 * Since CDI is not available on gRPC threads, we use static cache
 * populated during application startup when CDI is available.
 */
public class WildFlyGrpcHandler extends GrpcHandler {

    // Static cache populated during application startup by GrpcBeanInitializer
    private static volatile AgentCard staticAgentCard;
    private static volatile AgentCard staticExtendedAgentCard;
    private static volatile RequestHandler staticRequestHandler;
    private static volatile CallContextFactory staticCallContextFactory;
    private static volatile Executor staticExecutor;
    private static volatile ClassLoader deploymentClassLoader;

    public WildFlyGrpcHandler() {
        // Default constructor - the only one used by WildFly gRPC subsystem
    }

    /**
     * Called by GrpcBeanInitializer during CDI initialization to cache beans
     * for use by gRPC threads where CDI is not available.
     */
    static void setStaticBeans(AgentCard agentCard, AgentCard extendedAgentCard, RequestHandler requestHandler, CallContextFactory callContextFactory, Executor executor, ClassLoader classLoader) {
        staticAgentCard = agentCard;
        staticExtendedAgentCard = extendedAgentCard;
        staticRequestHandler = requestHandler;
        staticCallContextFactory = callContextFactory;
        staticExecutor = executor;
        deploymentClassLoader = classLoader;
    }

    @Override
    protected RequestHandler getRequestHandler() {
        if (staticRequestHandler == null) {
            throw new RuntimeException("RequestHandler not available. ApplicationStartup may not have run yet.");
        }
        // Wrap the RequestHandler to set the deployment classloader as TCCL
        // This is necessary because gRPC threads have the grpc extension module classloader as TCCL,
        // which cannot see the deployment's WEB-INF/lib jars needed by ServiceLoader
        return new ClassLoaderSwitchingRequestHandler(staticRequestHandler, deploymentClassLoader);
    }

    @Override
    protected AgentCard getAgentCard() {
        if (staticAgentCard == null) {
            throw new RuntimeException("AgentCard not available. ApplicationStartup may not have run yet.");
        }
        return staticAgentCard;
    }

    @Override
    protected AgentCard getExtendedAgentCard() {
        return staticExtendedAgentCard; // Can be null if not configured
    }

    @Override
    protected CallContextFactory getCallContextFactory() {
        return staticCallContextFactory; // Can be null if not configured
    }

    @Override
    protected Executor getExecutor() {
        if (staticExecutor == null) {
            throw new RuntimeException("Executor not available. ApplicationStartup may not have run yet.");
        }
        return staticExecutor;
    }

    /**
     * RequestHandler wrapper that sets the deployment classloader as TCCL before delegating.
     * This is necessary because gRPC threads have the grpc extension module classloader,
     * which cannot see deployment WEB-INF/lib jars needed by ServiceLoader.
     */
    private static class ClassLoaderSwitchingRequestHandler implements RequestHandler {
        private final RequestHandler delegate;
        private final ClassLoader deploymentClassLoader;

        ClassLoaderSwitchingRequestHandler(RequestHandler delegate, ClassLoader deploymentClassLoader) {
            this.delegate = delegate;
            this.deploymentClassLoader = deploymentClassLoader;
        }

        private <T> T withDeploymentClassLoader(java.util.function.Supplier<T> supplier) {
            ClassLoader originalTCCL = Thread.currentThread().getContextClassLoader();
            try {
                Thread.currentThread().setContextClassLoader(deploymentClassLoader);
                return supplier.get();
            } finally {
                Thread.currentThread().setContextClassLoader(originalTCCL);
            }
        }

        @Override
        public EventKind onMessageSend(MessageSendParams params, ServerCallContext context) {
            return withDeploymentClassLoader(() -> delegate.onMessageSend(params, context));
        }

        @Override
        public Flow.Publisher<StreamingEventKind> onMessageSendStream(MessageSendParams params, ServerCallContext context) {
            return withDeploymentClassLoader(() -> delegate.onMessageSendStream(params, context));
        }

        @Override
        public Task onGetTask(TaskQueryParams params, ServerCallContext context) {
            return withDeploymentClassLoader(() -> delegate.onGetTask(params, context));
        }

        @Override
        public ListTasksResult onListTasks(ListTasksParams params, ServerCallContext context) {
            return withDeploymentClassLoader(() -> delegate.onListTasks(params, context));
        }

        @Override
        public Task onCancelTask(CancelTaskParams params, ServerCallContext context) {
            return withDeploymentClassLoader(() -> delegate.onCancelTask(params, context));
        }

        @Override
        public Flow.Publisher<StreamingEventKind> onSubscribeToTask(TaskIdParams params, ServerCallContext context) {
            return withDeploymentClassLoader(() -> delegate.onSubscribeToTask(params, context));
        }

        @Override
        public TaskPushNotificationConfig onCreateTaskPushNotificationConfig(TaskPushNotificationConfig config, ServerCallContext context) {
            return withDeploymentClassLoader(() -> delegate.onCreateTaskPushNotificationConfig(config, context));
        }

        @Override
        public TaskPushNotificationConfig onGetTaskPushNotificationConfig(GetTaskPushNotificationConfigParams params, ServerCallContext context) {
            return withDeploymentClassLoader(() -> delegate.onGetTaskPushNotificationConfig(params, context));
        }

        @Override
        public ListTaskPushNotificationConfigsResult onListTaskPushNotificationConfigs(ListTaskPushNotificationConfigsParams params, ServerCallContext context) throws A2AError {
            return withDeploymentClassLoader(() -> delegate.onListTaskPushNotificationConfigs(params, context));
        }

        @Override
        public void onDeleteTaskPushNotificationConfig(DeleteTaskPushNotificationConfigParams params, ServerCallContext context) {
            withDeploymentClassLoader(() -> {
                delegate.onDeleteTaskPushNotificationConfig(params, context);
                return null;
            });
        }

        @Override
        public void validateRequestedTask(String requestedTaskId) throws A2AError {
            withDeploymentClassLoader(() -> {
                delegate.validateRequestedTask(requestedTaskId);
                return null;
            });
        }
    }
}
