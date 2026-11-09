package dts.com.examination.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dts.com.examination.domain.entity.ExamVersion;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Content-addressed approval proof, excluding the non-deterministic publication timestamp. */
@Component
public class OfficialVersionApprovalFingerprint {
    private final ObjectMapper json;

    public OfficialVersionApprovalFingerprint(ObjectMapper json) { this.json = json; }

    public String of(ExamVersion version, Map<String, Object> validated) {
        if (!"OFFICIAL".equals(version.getExamType()) || validated == null
                || !Integer.valueOf(1).equals(validated.get("officialConfigVersion"))) {
            throw new IllegalArgumentException("A verified OFFICIAL configuration is required");
        }
        var binding = new LinkedHashMap<String, Object>();
        binding.put("examId", version.getExamId());
        binding.put("versionId", version.getId());
        binding.put("versionNo", version.getVersionNo());
        binding.put("title", version.getTitle());
        binding.put("examType", version.getExamType());
        binding.put("thumbnailId", version.getThumbnailId());
        binding.put("examStructureId", version.getExamStructureId());
        binding.put("examRuleId", version.getExamRuleId());
        binding.put("examCriteriaId", version.getExamCriteriaId());
        binding.put("contentType", version.getContentType());
        binding.put("contentId", version.getContentId());
        binding.put("startedAt", version.getStartedAt());
        binding.put("endedAt", version.getEndedAt());
        binding.put("metadata", version.getMetadata());
        // Never trust a client-provided approval flag in metadata/configs. Recalculate
        // the version, question source, structure, rule, criteria and policy every time.
        var canonicalConfig = new LinkedHashMap<>(validated);
        canonicalConfig.remove("publishedAt");
        binding.put("validatedConfiguration", canonicalConfig);
        try {
            JsonNode tree = canonical(json.valueToTree(binding));
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsString(tree).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException("Cannot calculate official version proof", error);
        }
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode ordered = json.createObjectNode();
            var fields = new TreeMap<String, JsonNode>();
            node.fields().forEachRemaining(field -> fields.put(field.getKey(), field.getValue()));
            fields.forEach((key, value) -> ordered.set(key, canonical(value)));
            return ordered;
        }
        if (node.isArray()) {
            ArrayNode ordered = json.createArrayNode();
            node.forEach(item -> ordered.add(canonical(item)));
            return ordered;
        }
        return node;
    }
}
