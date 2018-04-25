package com.example.socketserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.HandlerMapping;
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter;
import reactor.core.publisher.Flux;

import java.util.Collections;

@Configuration
@Slf4j
public class StockSocketConfiguration {

    private final StockService stockService;
    private final ObjectMapper mapper;

    public StockSocketConfiguration(StockService stockService, ObjectMapper mapper) {
        this.stockService = stockService;
        this.mapper = mapper;
    }

    @Bean
    WebSocketHandlerAdapter socketHandlerAdapter() {
        return new WebSocketHandlerAdapter();
    }

    @Bean
        // Browsers don't support headers for WS://
    WebSocketHandler webSocketHandler() {
        return session -> {
            final String clientId =
                    session.getHandshakeInfo().getHeaders().getFirst("client-id");

            if (StringUtil.isNullOrEmpty(clientId)) {
                return session
                        .send(
                                Flux.just("{'msg': 'No ClientID'}")
                                        .map(session::textMessage)
                        ).and(s -> session.close(CloseStatus.NOT_ACCEPTABLE));
            }

            return session.send(
                    stockService.getOrCreateClientSink(clientId)
                            .map(this::toJson)
                            .map(session::textMessage))
                    .and(session.receive()
                            .map(WebSocketMessage::getPayloadAsText)
                            .doFinally(sig -> {
                                log.info("Terminating session due to:" + sig.toString());
                                stockService.removeClientSink(clientId);
                                session.close();
                            }));
        };

    }

    String toJson(Stock stock) {
        try {
            return mapper.writeValueAsString(stock);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "{'msg':'JSON Conversion Error!'}";
        }
    }

    @Bean
    HandlerMapping simpleUrlHandlerMapping() {
        SimpleUrlHandlerMapping simpleUrlHandlerMapping = new SimpleUrlHandlerMapping();
        simpleUrlHandlerMapping.setUrlMap(Collections.singletonMap("/ws/feed",
                webSocketHandler()));
        simpleUrlHandlerMapping.setOrder(10);
        return simpleUrlHandlerMapping;

    }

}
