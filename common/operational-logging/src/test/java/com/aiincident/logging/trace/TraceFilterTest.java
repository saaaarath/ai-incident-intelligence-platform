package com.aiincident.logging.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceFilterTest {

    private final TraceFilter traceFilter = new TraceFilter();

    @Test
    void generatesNewTraceIdWhenHeaderIsMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcTraceIdDuringExecution = new AtomicReference<>();

        FilterChain filterChain = (req, res) -> {
            mdcTraceIdDuringExecution.set(TraceContext.getTraceId());
        };

        traceFilter.doFilter(request, response, filterChain);

        String generatedTraceId = response.getHeader(TraceConstants.TRACE_HEADER);
        assertThat(generatedTraceId).isNotBlank();
        assertThat(mdcTraceIdDuringExecution.get()).isEqualTo(generatedTraceId);
        assertThat(TraceContext.getTraceId()).isNull();
    }

    @Test
    void propagatesSuppliedTraceId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceConstants.TRACE_HEADER, "custom-trace-12345");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcTraceIdDuringExecution = new AtomicReference<>();

        FilterChain filterChain = (req, res) -> {
            mdcTraceIdDuringExecution.set(TraceContext.getTraceId());
        };

        traceFilter.doFilter(request, response, filterChain);

        assertThat(response.getHeader(TraceConstants.TRACE_HEADER)).isEqualTo("custom-trace-12345");
        assertThat(mdcTraceIdDuringExecution.get()).isEqualTo("custom-trace-12345");
        assertThat(TraceContext.getTraceId()).isNull();
    }

    @Test
    void generatesNewTraceIdWhenSuppliedHeaderIsBlank() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceConstants.TRACE_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcTraceIdDuringExecution = new AtomicReference<>();

        FilterChain filterChain = (req, res) -> {
            mdcTraceIdDuringExecution.set(TraceContext.getTraceId());
        };

        traceFilter.doFilter(request, response, filterChain);

        String generatedTraceId = response.getHeader(TraceConstants.TRACE_HEADER);
        assertThat(generatedTraceId).isNotBlank();
        assertThat(generatedTraceId.trim()).isEqualTo(generatedTraceId);
        assertThat(mdcTraceIdDuringExecution.get()).isEqualTo(generatedTraceId);
        assertThat(TraceContext.getTraceId()).isNull();
    }

    @Test
    void clearsMdcEvenWhenFilterChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TraceConstants.TRACE_HEADER, "error-trace-999");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain filterChain = (req, res) -> {
            throw new RuntimeException("Simulated filter chain failure");
        };

        assertThatThrownBy(() -> traceFilter.doFilter(request, response, filterChain))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Simulated filter chain failure");

        assertThat(TraceContext.getTraceId()).isNull();
    }
}
