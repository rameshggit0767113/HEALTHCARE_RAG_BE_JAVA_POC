package com.rag.docMate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import io.qdrant.client.grpc.Collections.Distance;
import io.qdrant.client.grpc.Collections.VectorParams;

@SpringBootApplication
@Configuration
public class DocMateApplication {

	@Value("${anthropic.api-key}")
	private String apiKey;

	private static final String COLLECTION = "healthcare-local";
	private static final int VECTOR_SIZE = 384;

	public static void main(String[] args) {
		SpringApplication.run(DocMateApplication.class, args);
	}

	@Bean
	EmbeddingModel embeddingModel() {
		return new AllMiniLmL6V2QuantizedEmbeddingModel();
	}

	@Bean(destroyMethod = "close")
	QdrantClient qdrantClient() {
		return new QdrantClient(QdrantGrpcClient.newBuilder("localhost", 6334, false).build());
	}

	@Bean
	EmbeddingStore<TextSegment> embeddingStore(QdrantClient qdrantClient) {
		return new QdrantStore(qdrantClient, COLLECTION);
	}

	@Bean
	ChatLanguageModel chatModel() {
		return AnthropicChatModel.builder()
				.apiKey(apiKey)
				.modelName("claude-haiku-4-5-20251001")
				.build();
	}

	@Bean
	ApplicationRunner ensureCollection(QdrantClient qdrantClient) {
		return args -> {
			try {
				qdrantClient.createCollectionAsync(COLLECTION,
						VectorParams.newBuilder()
								.setSize(VECTOR_SIZE)
								.setDistance(Distance.Cosine)
								.build()).get();
				System.out.println("Qdrant collection '" + COLLECTION + "' created.");
			} catch (Exception e) {
				System.out.println("Qdrant collection '" + COLLECTION + "' already exists, skipping.");
			}
		};
	}
}
