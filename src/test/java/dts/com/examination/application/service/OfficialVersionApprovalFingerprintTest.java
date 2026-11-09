package dts.com.examination.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.domain.entity.ExamVersion;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OfficialVersionApprovalFingerprintTest {
    private final OfficialVersionApprovalFingerprint proof =
            new OfficialVersionApprovalFingerprint(new ObjectMapper().findAndRegisterModules());
    private final UUID versionId = UUID.randomUUID();

    private ExamVersion version() {
        return ExamVersion.builder().id(versionId).examId(UUID.randomUUID()).versionNo(1)
                .status("DRAFT").title("Official exam").examType("OFFICIAL")
                .contentType("BANK_VERSION").contentId(UUID.randomUUID())
                .examStructureId(UUID.randomUUID()).examRuleId(UUID.randomUUID())
                .examCriteriaId(UUID.randomUUID()).startedAt(Instant.parse("2026-11-08T00:00:00Z"))
                .endedAt(Instant.parse("2026-11-09T00:00:00Z"))
                .metadata(Map.of("note", "unchanged")).build();
    }

    private Map<String, Object> config() {
        var map=new LinkedHashMap<String,Object>();
        map.put("officialConfigVersion",1);
        map.put("bankVersion","dts-2026-600-v1");
        map.put("selectionMode","MANUAL");
        map.put("manualQuestionIds",List.of("00000000-0000-0000-0000-000000000001"));
        map.put("draftSourceRevisions",Map.of("question-a", "sha256:abc"));
        map.put("structureSnapshot",Map.of("sections",List.of(Map.of("chapter",1,"count",8))));
        return map;
    }

    @Test void generatedPublicationTimestampDoesNotInvalidateApproval() {
        var version=version();var validated=config();
        validated.put("publishedAt","2026-11-06T00:00:00Z");
        String before=proof.of(version,validated);
        validated.put("publishedAt","2026-11-07T01:02:03Z");
        assertEquals(before,proof.of(version,validated));
    }

    @Test void changingSourceDependencyOrVersionMetadataInvalidatesApproval() {
        var v=version();var config=config();String before=proof.of(v,config);
        config.put("draftSourceRevisions",Map.of("question-a","sha256:def"));
        assertNotEquals(before,proof.of(v,config));
        config=config();config.put("structureSnapshot",Map.of("sections",List.of(Map.of("chapter",1,"count",9))));
        assertNotEquals(before,proof.of(v,config));
        v.setTitle("Altered official exam");
        assertNotEquals(before,proof.of(v,config()));
    }

    @Test void mapKeyOrderHasNoEffectButArrayOrderDoes() {
        var v=version();var a=config();var b=new LinkedHashMap<String,Object>();
        var keys=new java.util.ArrayList<>(a.keySet());java.util.Collections.reverse(keys);
        keys.forEach(k->b.put(k,a.get(k)));
        assertEquals(proof.of(v,a),proof.of(v,b));
        b.put("manualQuestionIds",List.of("2","1"));
        assertNotEquals(proof.of(v,a),proof.of(v,b));
    }

    @Test void unverifiedAndNonOfficialConfigurationsAreRejected() {
        var v=version();
        assertThrows(IllegalArgumentException.class,()->proof.of(v,Map.of()));
        v.setExamType("PRACTICE");
        assertThrows(IllegalArgumentException.class,()->proof.of(v,config()));
    }
}
