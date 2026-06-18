package com.rag.docMate;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RagService {

	@Autowired EmbeddingStore<TextSegment> store;
	@Autowired EmbeddingModel embeddingModel;
	@Autowired ChatLanguageModel chatModel;

    public String ask(String question) {

        Embedding queryEmbedding = embeddingModel.embed(question).content();

        List<EmbeddingMatch<TextSegment>> matches = store.findRelevant(queryEmbedding, 5);

        String context = matches.stream()
                .map(m -> m.embedded().text())
                .collect(Collectors.joining("\n"));

        String prompt = """
                You are a healthcare assistant.

                Context:
                %s

                Question:
                %s
                """.formatted(context, question);

        return chatModel.generate(prompt);
    }
}
