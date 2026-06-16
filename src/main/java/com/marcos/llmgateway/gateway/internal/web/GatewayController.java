package com.marcos.llmgateway.gateway.internal.web;

import com.marcos.llmgateway.gateway.internal.ChatService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public OpenAiChatResponseDTO openAiChat(@RequestBody OpenAiChatRequestDTO request) {
        var domainRequest = toDomain(request);
        var domainResponse = chatService.chat(domainRequest);
        return toDTO(domainResponse);
    }
}
