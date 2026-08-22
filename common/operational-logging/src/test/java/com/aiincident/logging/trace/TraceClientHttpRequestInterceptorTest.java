package com.aiincident.logging.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class TraceClientHttpRequestInterceptorTest {

    private final TraceClientHttpRequestInterceptor interceptor = new TraceClientHttpRequestInterceptor();

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void injectsExistingMdcTraceIdIntoRequestHeaders() throws IOException {
        TraceContext.setTraceId("trace-ctx-123");
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.POST, java.net.URI.create("http://localhost:8080/test"));

        interceptor.intercept(request, new byte[0], (req, body) -> new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK));

        assertThat(request.getHeaders().getFirst(TraceConstants.TRACE_HEADER)).isEqualTo("trace-ctx-123");
    }

    @Test
    void generatesTraceIdWhenMdcIsEmpty() throws IOException {
        MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, java.net.URI.create("http://localhost:8080/test"));

        interceptor.intercept(request, new byte[0], (req, body) -> new MockClientHttpResponse(new byte[0], org.springframework.http.HttpStatus.OK));

        String injectedTraceId = request.getHeaders().getFirst(TraceConstants.TRACE_HEADER);
        assertThat(injectedTraceId).isNotBlank();
    }
}
