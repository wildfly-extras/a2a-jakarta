package org.wildfly.a2a.jakarta.jsonrpc.compat03;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import org.a2aproject.sdk.compat03.spec.CancelTaskRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.DeleteTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.GetAuthenticatedExtendedCardRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.GetTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.GetTaskRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.ListTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.SendMessageRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.SendStreamingMessageRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.SetTaskPushNotificationConfigRequest_v0_3;
import org.a2aproject.sdk.compat03.spec.TaskResubscriptionRequest_v0_3;
import org.wildfly.a2a.jakarta.common.A2AJsonRpcMethodProvider;

@ApplicationScoped
public class JsonRpcMethodProvider_v0_3 implements A2AJsonRpcMethodProvider {

    @Override
    public Set<String> getStreamingMethodNames() {
        return Set.of(
                SendStreamingMessageRequest_v0_3.METHOD,
                TaskResubscriptionRequest_v0_3.METHOD);
    }

    @Override
    public Set<String> getNonStreamingMethodNames() {
        return Set.of(
                SendMessageRequest_v0_3.METHOD,
                GetTaskRequest_v0_3.METHOD,
                CancelTaskRequest_v0_3.METHOD,
                SetTaskPushNotificationConfigRequest_v0_3.METHOD,
                GetTaskPushNotificationConfigRequest_v0_3.METHOD,
                ListTaskPushNotificationConfigRequest_v0_3.METHOD,
                DeleteTaskPushNotificationConfigRequest_v0_3.METHOD,
                GetAuthenticatedExtendedCardRequest_v0_3.METHOD);
    }
}
