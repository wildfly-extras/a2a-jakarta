package org.wildfly.a2a.jakarta.rest;

import java.util.Set;

import jakarta.enterprise.context.ApplicationScoped;

import org.wildfly.a2a.jakarta.common.A2AVersionProvider;

@ApplicationScoped
public class RestVersionProvider_v1_0 implements A2AVersionProvider {

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public boolean isDefaultVersion() {
        return false;
    }

    @Override
    public String getInternalPathPrefix() {
        return "/a2a_rest_v1.0";
    }

    @Override
    public String getRestBasePath() {
        return "/";
    }

    @Override
    public Set<String> getRestPathPrefixes() {
        return Set.of("tasks", "message:", "card", "extendedAgentCard");
    }
}
