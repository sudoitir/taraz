package io.github.sudoitir.taraz.adapters.driving.rest.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlation ({@code X-Correlation-ID}, ADR-0043/0056): echoes the incoming id or generates one, sets
 * it on every response — errors included, since a filter wraps the whole chain — and binds it to MDC
 * for logs. Renamed from {@code FlowIdFilter}/{@code X-Flow-ID} by ADR-0056 to the more widely
 * recognized correlation-header convention.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(RestHeaders.X_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        response.setHeader(RestHeaders.X_CORRELATION_ID, correlationId);
        MDC.put(RestHeaders.CORRELATION_ID_MDC_KEY, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RestHeaders.CORRELATION_ID_MDC_KEY);
        }
    }
}
