package dts.com.examination.application.service.selection;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Private backend content and answer key. Never use this as a student response DTO. */
public record FrozenExamQuestion(UUID id, String contentVersion, String content, String type,
        @JsonProperty("isCritical") boolean isCritical, List<UUID> chapterIds, List<UUID> mediaFileIds,
        List<Option> options, Map<String, Object> explanations) {
    public FrozenExamQuestion {
        chapterIds = List.copyOf(chapterIds);
        mediaFileIds = List.copyOf(mediaFileIds);
        options = List.copyOf(options);
        explanations = explanations == null ? null : immutableMap(explanations);
    }

    public record Option(UUID id, String content, Integer sortOrder, @JsonProperty("isCorrect") boolean isCorrect) {
    }

    private static Map<String, Object> immutableMap(Map<?, ?> value) {
        Map<String, Object> copy = new java.util.LinkedHashMap<>();
        value.forEach((key, item) -> {
            if (!(key instanceof String name)) {
                throw new IllegalArgumentException("JSON object keys must be strings");
            }
            copy.put(name, immutable(item));
        });
        return java.util.Collections.unmodifiableMap(copy);
    }

    private static Object immutable(Object value) {
        if (value instanceof Map<?, ?> map) return immutableMap(map);
        if (value instanceof List<?> list) {
            List<Object> copy = new java.util.ArrayList<>();
            list.forEach(item -> copy.add(immutable(item)));
            return java.util.Collections.unmodifiableList(copy);
        }
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        throw new IllegalArgumentException("Unsupported JSON value in source explanation");
    }
}
