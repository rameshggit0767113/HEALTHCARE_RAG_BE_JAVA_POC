package com.rag.docMate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChatController {

	@Autowired RagService ragService;

    @PostMapping("/chat")
    public RagChatResponse ask(
          @RequestBody RagChatRequest request) {
        return new RagChatResponse(ragService.ask(request.getQuestion()));
    }
}