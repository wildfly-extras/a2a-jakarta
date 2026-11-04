package org.wildfly.a2a.jakarta.jsonrpc.compat03;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.SecurityContext;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.a2aproject.sdk.server.ServerCallContext;
import org.a2aproject.sdk.server.auth.UnauthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class A2AServerResourceDelegate_v0_3_Test {

    @ParameterizedTest
    @ValueSource(strings = {"123", "true", "[]"})
    void streamingRejectsInvalidMethodAndPreservesId(String method) throws Exception {
        JsonObject error = streamingError(
                "{\"jsonrpc\":\"2.0\",\"id\":\"request-7\",\"method\":" + method + "}");
        assertEquals("request-7", error.get("id").getAsString());
        assertEquals(-32600, error.getAsJsonObject("error").get("code").getAsInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "[]"})
    void streamingRejectsInvalidParamsAndPreservesId(String params) throws Exception {
        JsonObject error = streamingError(
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"message/stream\",\"params\":" + params + "}");
        assertEquals(7, error.get("id").getAsInt());
        assertEquals(-32602, error.getAsJsonObject("error").get("code").getAsInt());
    }

    @Test
    void streamingEntryRejectsNonStreamingMethodAndPreservesId() throws Exception {
        JsonObject error = streamingError(
                "{\"jsonrpc\":\"2.0\",\"id\":\"message/stream\",\"method\":\"tasks/get\",\"params\":{\"id\":\"task\"}}");
        assertEquals("message/stream", error.get("id").getAsString());
        assertEquals(-32601, error.getAsJsonObject("error").get("code").getAsInt());
    }

    private JsonObject streamingError(String body) throws Exception {
        StringWriter output = new StringWriter();
        PrintWriter writer = new PrintWriter(output);
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                HttpServletResponse.class.getClassLoader(), new Class<?>[] { HttpServletResponse.class },
                (proxy, method, args) -> method.getName().equals("getWriter") ? writer : null);
        A2AServerResourceDelegate_v0_3 delegate = new A2AServerResourceDelegate_v0_3(null, null) {
            @Override
            protected ServerCallContext createCallContext(HttpServletRequest request, SecurityContext securityContext) {
                return new ServerCallContext(UnauthenticatedUser.INSTANCE, new HashMap<>(), Set.of(), "0.3");
            }
        };
        delegate.handleStreamingRequests(body, response, null, null);
        return JsonParser.parseString(output.toString()).getAsJsonObject();
    }
}
