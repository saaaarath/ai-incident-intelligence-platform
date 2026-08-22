package com.aiincident.logging.trace;

import java.util.UUID;
import org.slf4j.MDC;

public final class TraceContext {

    private TraceContext() {
    }

    public static String getTraceId() {
        return MDC.get(TraceConstants.MDC_TRACE_ID_KEY);
    }

    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isBlank()) {
            MDC.put(TraceConstants.MDC_TRACE_ID_KEY, traceId);
        } else {
            MDC.remove(TraceConstants.MDC_TRACE_ID_KEY);
        }
    }

    public static String getOrCreateTraceId() {
        String traceId = getTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
            setTraceId(traceId);
        }
        return traceId;
    }

    public static void clear() {
        MDC.remove(TraceConstants.MDC_TRACE_ID_KEY);
    }
}
