package org.wildfly.a2a.jakarta.jsonrpc;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import org.a2aproject.sdk.spec.A2AMethods;
import org.wildfly.a2a.jakarta.common.A2AJsonRpcMethodProvider;

@ApplicationScoped
public class JsonRpcMethodProvider_v1_0 implements A2AJsonRpcMethodProvider {

    @Override
    public Set<String> getStreamingMethodNames() {
        return Set.of(
                A2AMethods.SEND_STREAMING_MESSAGE_METHOD,
                A2AMethods.SUBSCRIBE_TO_TASK_METHOD);
    }

    @Override
    public Set<String> getNonStreamingMethodNames() {
        return Set.of(
                A2AMethods.GET_TASK_METHOD,
                A2AMethods.CANCEL_TASK_METHOD,
                A2AMethods.SEND_MESSAGE_METHOD,
                A2AMethods.LIST_TASK_METHOD,
                A2AMethods.SET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD,
                A2AMethods.GET_TASK_PUSH_NOTIFICATION_CONFIG_METHOD,
                A2AMethods.LIST_TASK_PUSH_NOTIFICATION_CONFIG_METHOD,
                A2AMethods.DELETE_TASK_PUSH_NOTIFICATION_CONFIG_METHOD,
                A2AMethods.GET_EXTENDED_AGENT_CARD_METHOD);
    }
}
