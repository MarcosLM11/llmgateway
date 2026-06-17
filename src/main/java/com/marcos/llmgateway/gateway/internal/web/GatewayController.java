package com.marcos.llmgateway.gateway.internal.web;

import com.marcos.llmgateway.gateway.RoutingStrategy;
import com.marcos.llmgateway.gateway.internal.ChatService;
import com.marcos.llmgateway.gateway.internal.exceptions.InvalidStrategyException;
import com.marcos.llmgateway.gateway.internal.web.dto.OpenAiChatRequestDTO;
import com.marcos.llmgateway.gateway.internal.web.dto.OpenAiChatResponseDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import static com.marcos.llmgateway.gateway.internal.web.OpenAiChatMapper.toDTO;
import static com.marcos.llmgateway.gateway.internal.web.OpenAiChatMapper.toDomain;

@RestController
@RequestMapping("/v1")
public class GatewayController {

    private final ChatService chatService;

    public GatewayController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat/completions")
    public OpenAiChatResponseDTO openAiChat(
            @RequestBody OpenAiChatRequestDTO request,
            @RequestHeader(value="X-Gateway-Strategy",required = false) String strategy
    ) {
        var domainRequest = toDomain(request, resolveStrategy(strategy));
        var domainResponse = chatService.chat(domainRequest);
        return toDTO(domainResponse);
    }

    private RoutingStrategy resolveStrategy(String strategy) {
        if (strategy == null || strategy.isBlank()) {
            return RoutingStrategy.SEQUENTIAL_FALLBACK;
        }
        try {
            return RoutingStrategy.valueOf(strategy.trim().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new InvalidStrategyException("Invalid strategy header value: " + strategy);
        }
    }
}
