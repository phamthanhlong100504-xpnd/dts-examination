package dts.com.examination.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.application.client.ContentBuilderClient;
import dts.com.examination.application.service.selection.*;
import dts.com.examination.domain.entity.*;
import dts.com.examination.domain.entity.json.CriteriaConfig;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Configuration fixtures use the verified policy and chapter IDs from the real source export. No database writes. */
final class PublishedVersionTestFixture {
    static final UUID USER = new UUID(0, 100), EXAM = new UUID(0, 101), VERSION = new UUID(0, 102);
    static final Instant START = Instant.parse("2026-11-01T00:00:00Z"), END = Instant.parse("2026-11-30T00:00:00Z");
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final OfficialExamVersionValidator validator;
    final PublishedExamSnapshotReader reader;
    final ExamVersion version;
    final ExamRule rule;
    final ExamStructure structure;

    PublishedVersionTestFixture(String license) throws Exception {
        byte[] bytes;
        try(InputStream stream=getClass().getClassLoader().getResourceAsStream("screen12-source-B-real.json")) {
            assertNotNull(stream);bytes=stream.readAllBytes();
        }
        assertEquals("719454d2e29fd2ee921d33e9619b365056a638def7f50608ec7d624f61f2fdd3",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        JsonNode actual=mapper.readTree(bytes);
        Map<Integer,UUID> chapters=new HashMap<>();
        for(var c:actual.get("chapters"))chapters.put(c.get("sourceChapterId").asInt(),UUID.fromString(c.get("id").asText()));
        var catalog=new ExamMatrixPolicyCatalog(mapper);var policy=catalog.require(license,START);
        var structureValidator=new ExamStructureValidator();
        validator=new OfficialExamVersionValidator(catalog,new ExamQuestionSelectionService(catalog,structureValidator),
                new ExamQuestionSourceReader(mock(ContentBuilderClient.class)),structureValidator,mapper);
        reader=new PublishedExamSnapshotReader(mapper,validator,structureValidator);
        List<Section> sections=new ArrayList<>();int order=0;
        for(var topic:policy.topics())sections.add(new Section(topic.code(),topic.code(),topic.questionCount(),1,order++,
                topic.sourceChapterIds().stream().map(chapters::get).toList(),0));
        sections.add(new Section("CRITICAL","CRITICAL",policy.criticalQuestionCount(),1,order,List.of(),policy.criticalQuestionCount()));
        structure=ExamStructure.builder().id(new UUID(0,103)).title("Verified policy configuration").sections(sections).build();
        rule=ExamRule.builder().id(new UUID(0,104)).title("Verified policy configuration").status("ACTIVE")
                .durationSeconds(policy.durationMinutes()*60).navigationMode("FREE").reviewMode("ALL").resultReleaseMode("IMMEDIATE").build();
        var criteria=ExamCriteria.builder().id(new UUID(0,105)).title("Verified policy configuration")
                .criteria(CriteriaConfig.builder().totalScore(policy.totalQuestions()).passScore(policy.passingCorrectAnswers())
                        .gradingMethod(CriteriaConfig.GradingMethod.SUM).build()).build();
        Map<String,Object> config=new LinkedHashMap<>();config.put("officialConfigVersion",1);
        config.put("licenseClass",license);config.put("bankVersion",policy.bankVersion());config.put("selectionMode","AUTO_MATRIX");
        config.put("manualQuestionIds",List.of());config.put("matrixPolicyId",policy.id());config.put("totalQuestions",policy.totalQuestions());
        config.put("durationSeconds",rule.getDurationSeconds());config.put("passingCorrectAnswers",policy.passingCorrectAnswers());
        config.put("criticalQuestionCount",policy.criticalQuestionCount());config.put("publishedAt",START.minusSeconds(60).toString());
        config.put("structureSnapshot",json(structure));config.put("ruleSnapshot",json(rule));config.put("criteriaSnapshot",json(criteria));
        config.put("matrixPolicySnapshot",json(policy));
        // Real private content proves that serialization of student DTOs does not expose stored answer keys.
        config.put("privateContentForSerializationCheck",mapper.convertValue(actual.get("questions").get(0),Object.class));
        version=ExamVersion.builder().id(VERSION).examId(EXAM).title("Verified policy configuration").examType("OFFICIAL")
                .examStructureId(structure.getId()).examRuleId(rule.getId()).examCriteriaId(criteria.getId())
                .contentType("BANK_VERSION").contentId(chapters.get(1)).startedAt(START).endedAt(END)
                .status("PUBLISHED").configs(config).metadata(Map.of()).build();
    }

    void freezeRule() {version.getConfigs().put("ruleSnapshot",json(rule));version.getConfigs().put("durationSeconds",rule.getDurationSeconds());}
    Map<String,Object> json(Object value){return mapper.convertValue(value,new TypeReference<LinkedHashMap<String,Object>>(){});}
    ExamSession session(String status,int attempt,Instant expiry){return ExamSession.builder().id(new UUID(0,200+attempt))
            .examVersionId(VERSION).userId(USER).attemptNo(attempt).status(status).startedAt(START)
            .expiredAt(expiry).sessionToken(new UUID(0,300+attempt)).build();}
}
