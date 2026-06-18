package com.rag.docMate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.PointId;
import io.qdrant.client.grpc.Points.PointStruct;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import io.qdrant.client.grpc.Points.Vector;
import io.qdrant.client.grpc.Points.Vectors;
import io.qdrant.client.grpc.Points.WithPayloadSelector;
import io.qdrant.client.grpc.Points.WithVectorsSelector;

/**
 * Custom Qdrant EmbeddingStore that sets withVectors(true) on search,
 * avoiding the zero-length vector bug in langchain4j-qdrant.
 */
public class QdrantStore implements EmbeddingStore<TextSegment> {

    private static final String TEXT_KEY = "text";

    private final QdrantClient client;
    private final String collection;

    QdrantStore(QdrantClient client, String collection) {
        this.client = client;
        this.collection = collection;
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> segments) {
        List<PointStruct> points = new ArrayList<>();
        List<String> ids = new ArrayList<>();

        for (int i = 0; i < embeddings.size(); i++) {
            String id = UUID.randomUUID().toString();
            ids.add(id);

            List<Float> vec = toFloatList(embeddings.get(i).vector());

            Map<String, Value> payload = new HashMap<>();
            payload.put(TEXT_KEY, Value.newBuilder()
                    .setStringValue(segments.get(i).text())
                    .build());

            points.add(PointStruct.newBuilder()
                    .setId(PointId.newBuilder().setUuid(id).build())
                    .setVectors(Vectors.newBuilder()
                            .setVector(Vector.newBuilder().addAllData(vec).build())
                            .build())
                    .putAllPayload(payload)
                    .build());
        }

        try {
            client.upsertAsync(collection, points).get();
        } catch (Exception e) {
            throw new RuntimeException("Qdrant upsert failed", e);
        }
        return ids;
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding reference, int maxResults) {
        List<Float> vec = toFloatList(reference.vector());

        SearchPoints req = SearchPoints.newBuilder()
                .setCollectionName(collection)
                .addAllVector(vec)
                .setLimit(maxResults)
                .setWithPayload(WithPayloadSelector.newBuilder().setEnable(true).build())
                .setWithVectors(WithVectorsSelector.newBuilder().setEnable(true).build())
                .build();

        List<ScoredPoint> hits;
        try {
            hits = client.searchAsync(req).get();
        } catch (Exception e) {
            throw new RuntimeException("Qdrant search failed", e);
        }

        return hits.stream()
                .filter(sp -> sp.containsPayload(TEXT_KEY) &&
                        !sp.getPayloadOrThrow(TEXT_KEY).getStringValue().isBlank())
                .map(sp -> {
                    double score = sp.getScore();
                    String text = sp.getPayloadOrThrow(TEXT_KEY).getStringValue();
                    List<Float> storedVec = sp.getVectors().getVector().getDataList();
                    float[] arr = new float[storedVec.size()];
                    for (int i = 0; i < storedVec.size(); i++) arr[i] = storedVec.get(i);
                    return new EmbeddingMatch<>(score, sp.getId().getUuid(), Embedding.from(arr), TextSegment.from(text));
                }).collect(Collectors.toList());
    }

    @Override
    public List<EmbeddingMatch<TextSegment>> findRelevant(Embedding reference, int maxResults, double minScore) {
        return findRelevant(reference, maxResults).stream()
                .filter(m -> m.score() >= minScore)
                .collect(Collectors.toList());
    }

    @Override public String add(Embedding e) { throw new UnsupportedOperationException(); }
    @Override public void add(String id, Embedding e) { throw new UnsupportedOperationException(); }
    @Override public String add(Embedding e, TextSegment s) { throw new UnsupportedOperationException(); }
    @Override public List<String> addAll(List<Embedding> e) { throw new UnsupportedOperationException(); }

    private static List<Float> toFloatList(float[] arr) {
        List<Float> list = new ArrayList<>(arr.length);
        for (float v : arr) list.add(v);
        return list;
    }
}
