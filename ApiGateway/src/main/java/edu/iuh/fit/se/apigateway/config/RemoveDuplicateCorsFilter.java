package edu.iuh.fit.se.apigateway.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Order(-3) // Must run before auth filter so 401 responses still include CORS headers
public class RemoveDuplicateCorsFilter implements GlobalFilter {

    private void normalizeHeaders(HttpHeaders headers, String origin, String path) {
        // Remove backend CORS headers to prevent duplicate values
        headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
        headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
        headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);
        headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
        headers.remove(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
        headers.remove(HttpHeaders.ACCESS_CONTROL_MAX_AGE);

        // Prevent browser Basic Auth popup on 401 responses.
        headers.remove(HttpHeaders.WWW_AUTHENTICATE);

        // Always set CORS headers explicitly to ensure they're present even for error responses
        if (origin != null) {
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        } else {
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        }

        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, PATCH, OPTIONS");
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*");
        headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "*");
        headers.set(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");

        log.debug("🔧 Normalized response headers for path: {} with origin: {}", path, origin);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
        ServerHttpResponse originalResponse = exchange.getResponse();
        
        // Wrap response to ensure CORS headers are always set, even for error responses.
        // Also run before commit so empty-body responses (setComplete) are handled.
        ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
            @Override
            public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends org.springframework.core.io.buffer.DataBuffer> body) {
                normalizeHeaders(getHeaders(), origin, path);
                return super.writeWith(body);
            }

            @Override
            public Mono<Void> setComplete() {
                normalizeHeaders(getHeaders(), origin, path);
                return super.setComplete();
            }
        };

        originalResponse.beforeCommit(() -> {
            normalizeHeaders(originalResponse.getHeaders(), origin, path);
            return Mono.empty();
        });
        
        return chain.filter(exchange.mutate().response(decoratedResponse).build());
    }
}
