package org.wildfly.a2a.jakarta.jsonrpc.compat03;

import jakarta.enterprise.context.ApplicationScoped;

import org.wildfly.a2a.jakarta.common.A2AVersionProvider;

@ApplicationScoped
public class JsonRpcVersionProvider_v0_3 implements A2AVersionProvider {

    @Override
    public String getVersion() {
        return "0.3";
    }

    @Override
    public boolean isDefaultVersion() {
        return true;
    }

    @Override
    public String getInternalPathPrefix() {
        return "/a2a_jsonrpc_v0.3";
    }

    @Override
    public String getRestBasePath() {
        return null;
    }
}
