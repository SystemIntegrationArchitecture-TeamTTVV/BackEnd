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
@Order(-1) // Run early, but after RemoveCachedBodyFilter
public class RemoveDuplicateCorsFilter implements GlobalFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        
        // Only process WebSocket related paths
        if (path.contains("/ws/") || path.contains("/ws?") || path.contains("/api/common/ws")) {
            String origin = request.getHeaders().getFirst(HttpHeaders.ORIGIN);
            ServerHttpResponse originalResponse = exchange.getResponse();
            
            // Wrap response to handle CORS headers for WebSocket
            ServerHttpResponseDecorator decoratedResponse = new ServerHttpResponseDecorator(originalResponse) {
                @Override
                public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends org.springframework.core.io.buffer.DataBuffer> body) {
                    // Remove any CORS headers from backend first
                    HttpHeaders headers = getHeaders();
                    
                    // Remove backend CORS headers
                    headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
                    headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS);
                    headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS);
                    headers.remove(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS);
                    headers.remove(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS);
                    headers.remove(HttpHeaders.ACCESS_CONTROL_MAX_AGE);
                    
                    // Set CORS headers from Gateway (always set, not add)
                    if (origin != null && (origin.contains("localhost") || origin.contains("127.0.0.1") || origin.contains(":"))) {
                        log.debug("🔧 Setting CORS headers for WebSocket path: {} with origin: {}", path, origin);
                        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
                        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
                        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, PATCH, OPTIONS");
                        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*");
                        headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "*");
                        headers.set(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");
                    } else {
                        // Fallback: allow all origins if no origin header
                        log.debug("🔧 Setting CORS headers with wildcard for WebSocket path: {}", path);
                        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, PUT, DELETE, PATCH, OPTIONS");
                        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "*");
                    }
                    
                    return super.writeWith(body);
                }
            };
            
            return chain.filter(exchange.mutate().response(decoratedResponse).build());
        }
        
        return chain.filter(exchange);
    }
}
