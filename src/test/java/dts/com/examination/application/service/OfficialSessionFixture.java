package dts.com.examination.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import dts.com.examination.api.form.*;
import dts.com.examination.application.client.ContentBuilderClient;
import dts.com.examination.application.service.selection.*;
import dts.com.examination.application.service.session.*;
import dts.com.examination.domain.entity.*;
import dts.com.examination.domain.repository.*;
import org.springframework.kafka.core.KafkaTemplate;
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static dts.com.examination.application.service.PublishedVersionTestFixture.*;

final class OfficialSessionFixture {
    final PublishedVersionTestFixture published;
    final ExamRepository exams = mock(ExamRepository.class);
    final ExamVersionRepository versions = mock(ExamVersionRepository.class);
    final ExamSessionRepository sessions = mock(ExamSessionRepository.class);
    final ExamSessionAnswerRepository answers = mock(ExamSessionAnswerRepository.class);
    final CurrentStudentProfileReader profiles = mock(CurrentStudentProfileReader.class);
    final ContentBuilderClient content = mock(ContentBuilderClient.class);
    @SuppressWarnings("unchecked") final KafkaTemplate<String,Object> kafka = mock(KafkaTemplate.class);
    final MutableClock clock = new MutableClock(START.plusSeconds(120));
    final List<ExamSession> history = new ArrayList<>();
    final Map<UUID,ExamSession> stored = new LinkedHashMap<>();
    final Map<UUID,List<ExamSessionAnswer>> storedAnswers = new LinkedHashMap<>();
    final Exam parent = Exam.builder().id(EXAM).status("PUBLISHED").title("Real source fixture").build();
    final OfficialExamSessionService service;
    final Map<UUID,dts.com.examination.api.response.InternalQuestionDetailResponse> details=new LinkedHashMap<>();
    long sequence = 1000;

    OfficialSessionFixture() throws Exception { this("B"); }
    OfficialSessionFixture(String license) throws Exception {
        published=new PublishedVersionTestFixture(license);
        published.rule.setAllowSkip(true);published.freezeRule();
        when(profiles.selectedLicenseClass(USER)).thenReturn(license);
        when(exams.findByIdForUpdate(EXAM)).thenReturn(Optional.of(parent));
        when(versions.findExamIdByVersionId(VERSION)).thenReturn(Optional.of(EXAM));
        when(versions.findByIdForUpdate(VERSION)).thenReturn(Optional.of(published.version));
        when(versions.existsByIdAndExamType(VERSION,"OFFICIAL")).thenReturn(true);
        when(versions.findStudentDetailVersions(EXAM,null,USER,license)).thenAnswer(inv -> List.of(published.version));
        when(sessions.findOfficialHistoryForUpdate(VERSION,USER)).thenAnswer(inv -> new ArrayList<>(history));
        when(sessions.findByIdForUpdate(any())).thenAnswer(inv -> Optional.ofNullable(stored.get(inv.getArgument(0))));
        when(sessions.isOfficialSession(any())).thenAnswer(inv -> stored.containsKey(inv.getArgument(0)));
        when(sessions.saveAndFlush(any())).thenAnswer(inv -> {
            ExamSession session=inv.getArgument(0);session.setId(new UUID(0,sequence++));stored.put(session.getId(),session);history.add(session);return session;
        });
        when(sessions.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(answers.saveAll(any())).thenAnswer(inv -> {
            Iterable<ExamSessionAnswer> batch=inv.getArgument(0);List<ExamSessionAnswer> saved=new ArrayList<>();
            for(var row:batch){if(row.getId()==null)row.setId(new UUID(0,sequence++));
                var rows=storedAnswers.computeIfAbsent(row.getExamSessionId(),id->new ArrayList<>());
                if(rows.stream().noneMatch(existing->existing.getQuestionId().equals(row.getQuestionId())))rows.add(row);saved.add(row);}
            return saved;
        });
        when(answers.findByExamSessionId(any())).thenAnswer(inv -> new ArrayList<>(storedAnswers.getOrDefault(inv.getArgument(0),List.of())));
        JsonNode root=published.mapper.readTree(getClass().getClassLoader().getResourceAsStream("A1".equals(license)?"screen12-source-real.json":"screen12-source-B-real.json"));
        List<ExamQuestionPool.ChapterReference> chapters=new ArrayList<>();Map<UUID,ExamSourceQuestionMetadata> metadata=new LinkedHashMap<>();
        for(JsonNode chapter:root.get("chapters")){
            chapters.add(new ExamQuestionPool.ChapterReference(UUID.fromString(chapter.get("id").asText()),chapter.get("sourceChapterId").asInt(),chapter.get("bankVersion").asText()));
            for(JsonNode item:chapter.get("items")){
                var q=ExamSourceQuestionMetadata.fromMap(published.mapper,published.mapper.convertValue(item,new TypeReference<Map<String,Object>>(){}));metadata.putIfAbsent(q.id(),q);
            }
        }
        var pool=new ExamQuestionPool("dts-2026-600-v1",license,chapters,new ArrayList<>(metadata.values()));
        for(var item:root.get("questions")){var q=published.mapper.convertValue(item,dts.com.examination.api.response.InternalQuestionDetailResponse.class);details.put(q.getId(),q);}
        when(content.getQuestionBank("dts-2026-600-v1",license)).thenReturn(pool);
        when(content.getQuestionsBatch(anyList(),eq(license))).thenAnswer(inv -> {List<UUID> ids=inv.getArgument(0);return ids.stream().map(details::get).toList();});
        service=new OfficialExamSessionService(exams,versions,sessions,answers,profiles,published.reader,new ExamParticipationPolicy(),
                new ExamQuestionSourceReader(content),new ExamQuestionSelectionService(new ExamMatrixPolicyCatalog(published.mapper),new ExamStructureValidator()),
                new OfficialSessionGrader(),published.mapper,clock,kafka);
    }
    StartExamSessionRequest request(){return StartExamSessionRequest.builder().examVersionId(VERSION).build();}
    ExamSession start(){return stored.get(service.start(request(),USER).getSessionId());}
    FrozenOfficialSession frozen(ExamSession s){return published.mapper.convertValue(s.getMetadata().get("officialPaper"),FrozenOfficialSession.class);}
    List<ExamSessionAnswer> rows(ExamSession s){return storedAnswers.get(s.getId());}
    SaveAnswersRequest saveRequest(ExamSession session,List<AnswerItemRequest> items){return SaveAnswersRequest.builder().version(session.getVersion()).answers(items).build();}
    AnswerItemRequest correct(FrozenExamQuestion q){return AnswerItemRequest.builder().questionId(q.id()).selectedAnswer(Map.of("type","single_choice","value",q.options().stream().filter(FrozenExamQuestion.Option::isCorrect).findFirst().orElseThrow().id().toString())).build();}
    void correctAll(ExamSession s){service.save(s.getId(),saveRequest(s,frozen(s).questions().stream().map(this::correct).toList()),USER);}
    void manual(FrozenOfficialSession paper){
        var cfg=published.version.getConfigs();cfg.put("selectionMode","MANUAL");cfg.put("manualQuestionIds",paper.questions().stream().map(q->q.id().toString()).toList());
        cfg.put("manualQuestionsSnapshot",published.mapper.convertValue(paper.questions(),Object.class));
        Map<String,String> assignments=new LinkedHashMap<>();paper.sectionAssignments().forEach((id,code)->assignments.put(id.toString(),code));cfg.put("manualSectionAssignments",assignments);
        history.clear();stored.clear();storedAnswers.clear();clearInvocations(content);
    }
    static final class MutableClock extends Clock {
        Instant now;MutableClock(Instant now){this.now=now;}void set(Instant now){this.now=now;}
        public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now;}
    }
}
