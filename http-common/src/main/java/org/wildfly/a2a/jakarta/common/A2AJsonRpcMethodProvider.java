package org.wildfly.a2a.jakarta.common;

import java.util.Set;

public interface A2AJsonRpcMethodProvider {

    Set<String> getStreamingMethodNames();

    Set<String> getNonStreamingMethodNames();
}
