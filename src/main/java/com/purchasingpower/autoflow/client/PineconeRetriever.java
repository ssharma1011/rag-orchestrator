package com.purchasingpower.autoflow.client;



import com.purchasingpower.autoflow.configuration.AppProperties;
import io.pinecone.clients.Pinecone;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PineconeRetriever {

    private final Pinecone client;
    private final String indexName;

    public PineconeRetriever(AppProperties props) {
        this.client = new Pinecone.Builder(props.getPinecone().getApiKey()).build();
        this.indexName = props.getPinecone().getIndexName();
    }

    public String findRelevantCode(List<Double> vector, String repoName) {
        try {
            List<Float> floats = vector.stream().map(Double::floatValue).toList();

            QueryResponseWithUnsignedIndices response = client.getIndexConnection(indexName)
                    .query(
                            20,     // topK
                            floats, // vector
                            null,   // sparseIndices (not using hybrid search)
                            null,   // sparseValues (not using hybrid search)
                            null,   // id (not querying by specific vector ID)
                            null,   // namespace (default)
                            null,   // filter (pass Struct/Map if needed, null for now)
                            true,   // includeValues
                            true    // includeMetadata
                    );

            if (response.getMatchesList() == null || response.getMatchesList().isEmpty()) {
                return "NO CONTEXT FOUND";
            }

            return response.getMatchesList().stream()
                    .map(m -> {
                        String path = "Unknown Path";
                        String content = "";

                        // ✅ FIX 2: Handle Metadata null check manually (POJO vs Protobuf issue)
                        if (m.getMetadata() != null) {
                            var fields = m.getMetadata().getFieldsMap();

                            if (fields.containsKey("file_path")) {
                                path = fields.get("file_path").getStringValue();
                            }
                            if (fields.containsKey("content")) {
                                content = fields.get("content").getStringValue();
                            }
                        }

                        return "--- FILE: " + path + " ---\n" + content;
                    })
                    .collect(Collectors.joining("\n\n"));

        } catch (Exception e) {
            log.warn("Pinecone Query Error: {}", e.getMessage());
            return "";
        }
    }
}