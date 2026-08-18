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
 * Correlation (Zalando X-Flow-ID, ADR-0043): echoes the incoming id or generates one, sets it on every
 * response — errors included, since a filter wraps the whole chain — and binds it to MDC for logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class FlowIdFilter extends OncePerRequestFilter {

    static final String MDC_KEY = "flow_id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String flowId = request.getHeader(RestHeaders.X_FLOW_ID);
        if (flowId == null || flowId.isBlank()) {
            flowId = UUID.randomUUID().toString();
        }
        response.setHeader(RestHeaders.X_FLOW_ID, flowId);
        MDC.put(MDC_KEY, flowId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
