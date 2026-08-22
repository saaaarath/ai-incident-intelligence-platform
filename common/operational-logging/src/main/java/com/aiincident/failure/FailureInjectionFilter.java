package com.aiincident.failure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class FailureInjectionFilter extends OncePerRequestFilter {

    private final FailureInjectionService failureInjectionService;

    public FailureInjectionFilter(FailureInjectionService failureInjectionService) {
        this.failureInjectionService = failureInjectionService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path != null && path.startsWith("/internal")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            failureInjectionService.maybeInjectFailure();
        } catch (SimulatedServiceUnavailableException | SimulatedDatabaseException exception) {
            writeErrorResponse(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, exception.getMessage());
            return;
        } catch (SimulatedErrorSpikeException exception) {
            writeErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, exception.getMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        String json = "{\"error\":\"" + escapeJson(message) + "\"}";
        response.getWriter().write(json);
    }

    private static String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\"", "\\\"");
    }
}
