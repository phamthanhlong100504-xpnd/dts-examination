package dts.com.examination.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.api.form.*;
import dts.com.examination.api.response.ExamVersionResponse;
import dts.com.examination.application.client.ContentBuilderClient;
import dts.com.examination.api.response.InternalQuestionDetailResponse;
import dts.com.examination.application.exception.*;
import dts.com.examination.application.service.impl.ExamVersionServiceImpl;
import dts.com.examination.application.service.selection.*;
import dts.com.examination.domain.entity.*;
import dts.com.examination.domain.entity.json.CriteriaConfig;
import dts.com.examination.domain.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExamVersionWorkflowTest {
    private static final UUID EXAM_ID = new UUID(0, 1);
    private static final UUID VERSION_ID = new UUID(0, 2);
    private static final UUID STRUCTURE_ID = new UUID(0, 3);
    private static final UUID RULE_ID = new UUID(0, 4);
    private static final UUID CRITERIA_ID = new UUID(0, 5);
    private static final Instant START = Instant.parse("2026-11-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-11-30T00:00:00Z");
    private static final String BANK = "dts-2026-600-v1";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private ExamVersionRepository versions;
    private ExamRepository exams;
    private ExamStructureRepository structures;
    private ExamRuleRepository rules;
    private ExamCriteriaRepository criterias;
    private ExamSessionRepository sessions;
    private ContentBuilderClient client;
    private ExamVersionServiceImpl service;
    private OfficialExamVersionValidator validator;
    private ExamQuestionSelectionService selector;
    private ExamQuestionPool pool;
    private Map<UUID, InternalQuestionDetailResponse> actualDetails;
    private Exam parent;
    private ExamStructure structure;
    private ExamRule rule;
    private ExamCriteria criteria;
    private ExamVersion stored;
    private long nextSavedId;

    @BeforeEach
    void setUp() throws Exception {
        versions=mock(ExamVersionRepository.class); exams=mock(ExamRepository.class);
        structures=mock(ExamStructureRepository.class); rules=mock(ExamRuleRepository.class);
        criterias=mock(ExamCriteriaRepository.class); sessions=mock(ExamSessionRepository.class);
        client=mock(ContentBuilderClient.class);
        byte[] bytes;
        try (InputStream input=getClass().getClassLoader().getResourceAsStream("screen12-source-B-real.json")) {
            assertNotNull(input); bytes=input.readAllBytes();
        }
        assertEquals("719454d2e29fd2ee921d33e9619b365056a638def7f50608ec7d624f61f2fdd3",
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        JsonNode root=mapper.readTree(bytes);
        List<ExamQuestionPool.ChapterReference> refs=new ArrayList<>();
        Map<UUID,ExamSourceQuestionMetadata> metadata=new LinkedHashMap<>();
        Map<Integer,UUID> byNumber=new HashMap<>();
        for (var chapter:root.get("chapters")) {
            UUID id=UUID.fromString(chapter.get("id").asText()); int number=chapter.get("sourceChapterId").asInt();
            refs.add(new ExamQuestionPool.ChapterReference(id,number,BANK)); byNumber.put(number,id);
            for(var item:chapter.get("items")) {
                var q=mapper.convertValue(item,ExamSourceQuestionMetadata.class); metadata.putIfAbsent(q.id(),q);
            }
        }
        pool=new ExamQuestionPool(BANK,"B",refs,new ArrayList<>(metadata.values()));
        actualDetails=new LinkedHashMap<>();
        for(var item:root.get("questions")) {
            var q=mapper.convertValue(item,InternalQuestionDetailResponse.class); actualDetails.put(q.getId(),q);
        }
        when(client.getQuestionBank(BANK,"B")).thenReturn(pool);
        when(client.getQuestionsBatch(anyList(),eq("B"))).thenAnswer(call -> {
            List<UUID> ids=call.getArgument(0); return ids.stream().map(actualDetails::get).toList();
        });
        var catalog=new ExamMatrixPolicyCatalog(mapper);
        selector=new ExamQuestionSelectionService(catalog,new ExamStructureValidator());
        validator=new OfficialExamVersionValidator(catalog,selector,new ExamQuestionSourceReader(client),new ExamStructureValidator(),mapper);
        service=new ExamVersionServiceImpl(versions,exams,structures,rules,criterias,sessions,validator);
        parent=Exam.builder().id(EXAM_ID).title("Workflow fixture configuration").build();
        List<Section> sections=new ArrayList<>(); int[] counts={8,1,1,1,9,9};
        for(int i=0;i<6;i++) sections.add(new Section("CHAPTER_"+(i+1),"Chapter "+(i+1),counts[i],1,i,List.of(byNumber.get(i+1)),0));
        sections.add(new Section("CRITICAL","Critical",1,1,6,List.of(),1));
        structure=ExamStructure.builder().id(STRUCTURE_ID).title("Standard B").sections(sections).build();
        rule=ExamRule.builder().id(RULE_ID).title("Standard B").status("ACTIVE").durationSeconds(1200)
                .navigationMode("FREE").reviewMode("ALL").resultReleaseMode("IMMEDIATE").autoSubmit(true).build();
        criteria=ExamCriteria.builder().id(CRITERIA_ID).title("Standard B").criteria(CriteriaConfig.builder()
                .totalScore(30).passScore(27).gradingMethod(CriteriaConfig.GradingMethod.SUM).build()).build();
        when(exams.findByIdForUpdate(EXAM_ID)).thenReturn(Optional.of(parent));
        when(exams.findByIdAndNotDeleted(EXAM_ID)).thenReturn(Optional.of(parent));
        when(structures.findByIdForUpdate(STRUCTURE_ID)).thenReturn(Optional.of(structure));
        when(rules.findByIdForUpdate(RULE_ID)).thenReturn(Optional.of(rule));
        when(criterias.findByIdForUpdate(CRITERIA_ID)).thenReturn(Optional.of(criteria));
        when(versions.findMaxVersionNoByExamId(EXAM_ID)).thenReturn(0);
        nextSavedId=2;
        when(versions.save(any(ExamVersion.class))).thenAnswer(call -> {
            ExamVersion version=call.getArgument(0); if(version.getId()==null)version.setId(new UUID(0,nextSavedId++));
            stored=version;return version;
        });
        when(versions.findByIdAndDeletedAtIsNull(VERSION_ID)).thenAnswer(call -> Optional.ofNullable(stored));
        when(versions.findExamIdByVersionId(VERSION_ID)).thenAnswer(call -> stored == null ? Optional.empty() : Optional.of(EXAM_ID));
        when(versions.findByIdForUpdate(VERSION_ID)).thenAnswer(call -> Optional.ofNullable(stored));
        when(versions.findByExamIdAndStatusAndDeletedAtIsNullList(EXAM_ID,"PUBLISHED")).thenReturn(List.of());
    }

    @Test
    void automaticCreatePersistsExplicitPolicyAndPerformsNoQuestionDrawOrBatchRead() {
        var response=service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of()));
        assertEquals("B",response.licenseClass());assertEquals(BANK,response.bankVersion());
        assertEquals(ExamSelectionMode.AUTO_MATRIX,response.selectionMode());
        assertEquals(30,response.totalQuestions().intValue());assertEquals(1200,response.durationSeconds().intValue());
        assertEquals(27,response.passingCorrectAnswers().intValue());assertEquals(1,response.criticalQuestionCount().intValue());
        assertEquals(List.of(),response.manualQuestionIds());assertEquals("DRAFT",response.status());
        assertFalse(stored.getConfigs().containsKey("manualQuestionsSnapshot"));
        verify(client,never()).getQuestionsBatch(anyList(),anyString());
    }

    @Test
    void manualCreateValidatesAndStoresActualIdsOrderAndRevisionsBeforeSave() {
        var ids=manualIds();var response=service.create(EXAM_ID,request(ExamSelectionMode.MANUAL,ids));
        assertEquals(ids,response.manualQuestionIds());
        assertEquals(30,((Map<?,?>)stored.getConfigs().get("draftSourceRevisions")).size());
        assertFalse(stored.getConfigs().containsKey("manualQuestionsSnapshot"));
        verify(client).getQuestionsBatch(ids,"B");
        var order=inOrder(client,versions);order.verify(client).getQuestionBank(BANK,"B");
        order.verify(client).getQuestionsBatch(ids,"B");order.verify(versions).findMaxVersionNoByExamId(EXAM_ID);
        order.verify(versions).save(any());
    }

    @Test
    void wrongManualCountCannotCreateAVersion() {
        error("MANUAL_QUESTION_COUNT",()->service.create(EXAM_ID,request(ExamSelectionMode.MANUAL,manualIds().subList(0,29))));
        verify(versions,never()).save(any());
    }

    @Test
    void duplicateManualIdsCannotCreateAVersion() {
        var ids=new ArrayList<>(manualIds());ids.set(1,ids.get(0));
        error("MANUAL_DUPLICATE_QUESTION",()->service.create(EXAM_ID,request(ExamSelectionMode.MANUAL,ids)));
        verify(versions,never()).save(any());
    }

    @Test
    void correctTotalWithWrongManualTopicQuotaCannotCreateAVersion() {
        var ids=new ArrayList<>(manualIds());var signChapter=pool.chapters().get(4).id();var situationChapter=pool.chapters().get(5).id();
        UUID sign=pool.questions().stream().filter(q->ids.contains(q.id())&&q.chapterIds().contains(signChapter)).findFirst().orElseThrow().id();
        UUID replacement=pool.questions().stream().filter(q->!ids.contains(q.id())&&q.chapterIds().contains(situationChapter)).findFirst().orElseThrow().id();
        ids.set(ids.indexOf(sign),replacement);
        error("MANUAL_MATRIX_MISMATCH",()->service.create(EXAM_ID,request(ExamSelectionMode.MANUAL,ids)));
        verify(versions,never()).save(any());
    }

    @Test
    void sourceFailureBeforeSaveDoesNotCreateAVersion() {
        when(client.getQuestionBank(BANK,"B")).thenThrow(new ExaminationException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"SOURCE_UNAVAILABLE","Unavailable"));
        error("SOURCE_UNAVAILABLE",()->service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of())));
        verify(versions,never()).save(any());
    }

    @Test
    void manualPublishFreezesRealPrivateQuestionsKeysAndAllConfigurationsWithoutLeakingThemInDto() {
        var ids=manualIds();service.create(EXAM_ID,request(ExamSelectionMode.MANUAL,ids));
        var response=service.publish(VERSION_ID);assertEquals("PUBLISHED",response.status());
        assertEquals("PUBLISHED",parent.getStatus());
        for(var key:List.of("structureSnapshot","ruleSnapshot","criteriaSnapshot","matrixPolicySnapshot","manualQuestionsSnapshot","manualSectionAssignments"))assertTrue(stored.getConfigs().containsKey(key));
        JsonNode frozen=mapper.valueToTree(stored.getConfigs().get("manualQuestionsSnapshot"));
        assertEquals(30,frozen.size());
        for(int i=0;i<30;i++){
            var original=mapper.valueToTree(actualDetails.get(ids.get(i)));
            for(var field:List.of("id","content","type","isCritical","mediaFileIds","contentVersion","options","explanations"))assertTrue(original.path(field).equals(frozen.get(i).path(field)));
        }
        JsonNode publicJson=mapper.valueToTree(response);
        assertFalse(publicJson.has("configs"));assertFalse(publicJson.toString().contains("isCorrect"));
    }

    @Test
    void automaticPublishChecksAll600RealContentsButDoesNotPreselectAPaper() {
        service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of()));
        service.publish(VERSION_ID);
        var ids=pool.questions().stream().map(ExamSourceQuestionMetadata::id).toList();
        verify(client).getQuestionsBatch(ids.subList(0,500),"B");verify(client).getQuestionsBatch(ids.subList(500,600),"B");
        assertFalse(stored.getConfigs().containsKey("manualQuestionsSnapshot"));
        assertEquals(List.of(),stored.getConfigs().get("manualQuestionIds"));
    }

    @Test
    void changedManualRevisionAfterSaveBlocksPublishUntilDraftIsReviewedAndSavedAgain() {
        var ids=manualIds();service.create(EXAM_ID,request(ExamSelectionMode.MANUAL,ids));
        UUID id=ids.get(0);String changed="sha256:"+"0".repeat(64);
        var updated=new ArrayList<>(pool.questions());
        for(int i=0;i<updated.size();i++){
            var q=updated.get(i);if(q.id().equals(id))updated.set(i,new ExamSourceQuestionMetadata(q.id(),q.optionIds(),q.chapterIds(),q.isCritical(),q.status(),q.questionType(),q.mediaFileIds(),changed));
        }
        when(client.getQuestionBank(BANK,"B")).thenReturn(new ExamQuestionPool(BANK,"B",pool.chapters(),updated));
        actualDetails.get(id).setContentVersion(changed);
        error("SOURCE_CHANGED",()->service.publish(VERSION_ID));
        assertEquals("DRAFT",stored.getStatus());assertEquals("DRAFT",parent.getStatus());
        service.update(VERSION_ID,UpdateExamVersionRequest.builder().title("Reviewed").build());
        assertEquals("PUBLISHED",service.publish(VERSION_ID).status());
    }

    @Test
    void publishFailureDoesNotArchiveCurrentPublishedVersionOrChangeParent() {
        service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of()));
        var old=ExamVersion.builder().id(new UUID(0,20)).examId(EXAM_ID).status("PUBLISHED").build();
        when(versions.findByExamIdAndStatusAndDeletedAtIsNullList(EXAM_ID,"PUBLISHED")).thenReturn(List.of(old));
        clearInvocations(versions,exams);rule.setDurationSeconds(1);
        error("RULE_POLICY_MISMATCH",()->service.publish(VERSION_ID));
        assertEquals("PUBLISHED",old.getStatus());assertEquals("DRAFT",stored.getStatus());assertEquals("DRAFT",parent.getStatus());
        verify(versions,never()).save(any());verify(exams,never()).save(any());
    }

    @Test
    void successfulPublishArchivesPreviousVersionAndPreservesItsPrivateSnapshot() {
        service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of()));
        var old=ExamVersion.builder().id(new UUID(0,20)).examId(EXAM_ID).status("PUBLISHED")
                .configs(Map.of("privateHistory",Map.of("revision","existing"))).build();
        when(versions.findByExamIdAndStatusAndDeletedAtIsNullList(EXAM_ID,"PUBLISHED")).thenReturn(List.of(old));
        service.publish(VERSION_ID);assertEquals("ARCHIVED",old.getStatus());
        assertEquals(Map.of("revision","existing"),old.getConfigs().get("privateHistory"));assertTrue(old.getConfigs().containsKey("archivedAt"));
    }

    @Test
    void publicationDoesNotUnhideAnExplicitlyHiddenParent() {
        parent.setStatus("HIDDEN");service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of()));
        service.publish(VERSION_ID);assertEquals("HIDDEN",parent.getStatus());
    }

    @Test
    void mergedUpdateWindowIsValidatedBeforeMutatingManagedVersion() {
        service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of()));
        var originalTitle=stored.getTitle();var originalStart=stored.getStartedAt();clearInvocations(versions);
        error("VERSION_INVALID",()->service.update(VERSION_ID,UpdateExamVersionRequest.builder()
                .title("Do not persist").startedAt(END.plusSeconds(1)).build()));
        assertEquals(originalTitle,stored.getTitle());assertEquals(originalStart,stored.getStartedAt());verify(versions,never()).save(any());
    }

    @Test
    void updateHandlesTypeSourceAndSelectionFieldsWhichWerePreviouslyIgnored() {
        service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of()));
        var ids=manualIds();var result=service.update(VERSION_ID,UpdateExamVersionRequest.builder()
                .contentType("BANK_VERSION").contentId(pool.chapters().get(1).id()).examType("OFFICIAL")
                .selectionMode(ExamSelectionMode.MANUAL).manualQuestionIds(ids).build());
        assertEquals(ExamSelectionMode.MANUAL,result.selectionMode());assertEquals(ids,result.manualQuestionIds());
        assertEquals(pool.chapters().get(1).id(),result.contentId());
    }

    @Test
    void publishedAndArchivedVersionsCannotBeUpdatedOrDeleted() {
        service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of()));
        for(String status:List.of("PUBLISHED","ARCHIVED")){
            stored.setStatus(status);error("VERSION_IMMUTABLE",()->service.update(VERSION_ID,UpdateExamVersionRequest.builder().title("Change").build()));
            error("VERSION_IMMUTABLE",()->service.delete(VERSION_ID));
        }
    }

    @Test
    void anySessionHistoryPreventsDraftRewritePublishAndDelete() {
        service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of()));
        when(sessions.existsByExamVersionId(VERSION_ID)).thenReturn(true);
        error("VERSION_HAS_SESSIONS",()->service.update(VERSION_ID,UpdateExamVersionRequest.builder().title("Change").build()));
        error("VERSION_HAS_SESSIONS",()->service.delete(VERSION_ID));error("VERSION_HAS_SESSIONS",()->service.publish(VERSION_ID));
    }

    @Test
    void draftDeletionIsSoftAndPreservesStoredConfiguration() {
        service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of()));var original=Map.copyOf(stored.getConfigs());
        service.delete(VERSION_ID);assertNotNull(stored.getDeletedAt());assertEquals(original,stored.getConfigs());
    }

    @Test
    void cloneRevalidatesRealManualSourceAndDropsPublicationStateAndPrivateSnapshot() {
        var ids=manualIds();service.create(EXAM_ID,request(ExamSelectionMode.MANUAL,ids));service.publish(VERSION_ID);
        var sourceVersion=stored;when(versions.findMaxVersionNoByExamId(EXAM_ID)).thenReturn(1);
        var clone=service.clone(VERSION_ID);assertEquals("DRAFT",clone.status());assertEquals(2,clone.versionNo().intValue());assertNotEquals(sourceVersion.getId(),clone.id());
        assertEquals(ids,clone.manualQuestionIds());assertFalse(stored.getConfigs().containsKey("publishedAt"));
        assertFalse(stored.getConfigs().containsKey("manualQuestionsSnapshot"));assertEquals("PUBLISHED",sourceVersion.getStatus());
    }

    @Test
    void archiveRetainsPrivateSnapshotAndAddsHistoryTime() {
        service.create(EXAM_ID,request(ExamSelectionMode.MANUAL,manualIds()));service.publish(VERSION_ID);
        var before=stored.getConfigs().get("manualQuestionsSnapshot");
        assertEquals("ARCHIVED",service.archive(VERSION_ID).status());assertEquals(before,stored.getConfigs().get("manualQuestionsSnapshot"));
        assertTrue(stored.getConfigs().containsKey("archivedAt"));
    }

    @Test
    void rowLocksAreAcquiredInParentVersionStructureRuleCriteriaOrder() {
        service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of()));clearInvocations(exams,versions,structures,rules,criterias);
        service.update(VERSION_ID,UpdateExamVersionRequest.builder().title("Changed").build());
        var order=inOrder(exams,versions,structures,rules,criterias);
        order.verify(versions).findExamIdByVersionId(VERSION_ID);order.verify(exams).findByIdForUpdate(EXAM_ID);
        order.verify(versions).findByIdForUpdate(VERSION_ID);order.verify(structures).findByIdForUpdate(STRUCTURE_ID);
        order.verify(rules).findByIdForUpdate(RULE_ID);order.verify(criterias).findByIdForUpdate(CRITERIA_ID);
        verify(versions,never()).findByIdAndDeletedAtIsNull(VERSION_ID);
    }

    @Test
    void missingOrDeletedParentIsRejectedBeforeAnySourceReadOrVersionWrite() {
        when(exams.findByIdForUpdate(EXAM_ID)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,()->service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of())));
        verifyNoInteractions(client);verify(versions,never()).save(any());
    }

    @Test
    void missingReferencesAreRejectedBeforeSourceReads() {
        when(structures.findByIdForUpdate(STRUCTURE_ID)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,()->service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of())));
        verifyNoInteractions(client);verify(versions,never()).save(any());
    }

    @Test
    void officialRequiresExplicitModeLicenseBankWindowCriteriaAndBankSourceType() {
        for(var invalid:List.of(
                request(null,List.of()),
                CreateExamVersionRequest.builder().title("Official").examType("OFFICIAL").contentType("BANK_VERSION")
                        .contentId(pool.chapters().get(0).id()).examStructureId(STRUCTURE_ID).examRuleId(RULE_ID).examCriteriaId(CRITERIA_ID)
                        .startedAt(START).endedAt(END).selectionMode(ExamSelectionMode.AUTO_MATRIX).build(),
                new CreateExamVersionRequest("Official","OFFICIAL",null,STRUCTURE_ID,RULE_ID,CRITERIA_ID,"CHAPTER",pool.chapters().get(0).id(),START,END,Map.of(),"B",BANK,ExamSelectionMode.AUTO_MATRIX,List.of()),
                new CreateExamVersionRequest("Official","OFFICIAL",null,STRUCTURE_ID,RULE_ID,CRITERIA_ID,"BANK_VERSION",pool.chapters().get(0).id(),null,END,Map.of(),"B",BANK,ExamSelectionMode.AUTO_MATRIX,List.of()),
                new CreateExamVersionRequest("Official","OFFICIAL",null,STRUCTURE_ID,RULE_ID,null,"BANK_VERSION",pool.chapters().get(0).id(),START,END,Map.of(),"B",BANK,ExamSelectionMode.AUTO_MATRIX,List.of()))) {
            error("VERSION_INVALID",()->service.create(EXAM_ID,invalid));
        }
        verify(versions,never()).save(any());
    }

    @Test
    void criteriaTotalMustMatchActualStructureWeights() {
        criteria.getCriteria().setTotalScore(31);
        error("CRITERIA_STRUCTURE_MISMATCH",()->service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of())));
        verify(versions,never()).save(any());
    }

    @Test
    void weightedAndPercentageCriteriaKeepTheirOwnDeclaredScoreScale() {
        structure.getSections().forEach(s->s.setScore(2));criteria.getCriteria().setTotalScore(60);criteria.getCriteria().setPassScore(54);
        criteria.getCriteria().setGradingMethod(CriteriaConfig.GradingMethod.WEIGHTED);
        service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of()));
        criteria.getCriteria().setGradingMethod(CriteriaConfig.GradingMethod.PERCENTAGE);criteria.getCriteria().setTotalScore(100);criteria.getCriteria().setPassScore(90);
        service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of()));
    }

    @Test
    void undefinedGradingMethodsAndMaximumWrongRulesCannotBePublishedAsWorking() {
        for(var method:List.of(CriteriaConfig.GradingMethod.BEST_OF,CriteriaConfig.GradingMethod.AVERAGE)){
            criteria.getCriteria().setGradingMethod(method);error("CRITERIA_UNSUPPORTED",()->service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of())));
        }
        criteria.getCriteria().setGradingMethod(CriteriaConfig.GradingMethod.SUM);
        var ids=manualIds();criteria.getCriteria().setMandatoryRules(List.of(new CriteriaConfig.MandatoryRule(CriteriaConfig.MandatoryRule.RuleType.MAX_WRONG,List.of(ids.get(0).toString()))));
        error("CRITERIA_UNSUPPORTED",()->service.create(EXAM_ID,request(ExamSelectionMode.MANUAL,ids)));
    }

    @Test
    void mandatoryQuestionIdsMustBelongToManualPaperAndCannotSilentlyForceAutomaticSelection() {
        var ids=manualIds();criteria.getCriteria().setMandatoryRules(List.of(new CriteriaConfig.MandatoryRule(CriteriaConfig.MandatoryRule.RuleType.MUST_CORRECT,List.of(ids.get(0).toString()))));
        service.create(EXAM_ID,request(ExamSelectionMode.MANUAL,ids));
        error("CRITERIA_UNSUPPORTED",()->service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of())));
        UUID outside=pool.questions().stream().filter(q->!ids.contains(q.id())).findFirst().orElseThrow().id();
        criteria.getCriteria().getMandatoryRules().get(0).setQuestionIds(List.of(outside.toString()));
        error("CRITERIA_STRUCTURE_MISMATCH",()->service.create(EXAM_ID,request(ExamSelectionMode.MANUAL,ids)));
    }

    @Test
    void invalidSectionMinimumRoundingAndPenaltyBoundsAreRejected() {
        criteria.getCriteria().setSectionRules(List.of(new CriteriaConfig.SectionRule("CHAPTER_1",9)));
        error("CRITERIA_STRUCTURE_MISMATCH",()->service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of())));
        criteria.getCriteria().setSectionRules(null);criteria.getCriteria().setRounding(new CriteriaConfig.RoundingConfig("HALF_UP",3));
        error("CRITERIA_INVALID",()->service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of())));
        criteria.getCriteria().setRounding(null);criteria.getCriteria().setPenalties(List.of(new CriteriaConfig.PenaltyConfig(CriteriaConfig.PenaltyConfig.PenaltyType.WRONG_ANSWER,Double.NaN)));
        error("CRITERIA_INVALID",()->service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of())));
    }

    @Test
    void invalidDurationResumeAndEnumsAreRejectedBeforeSaving() {
        rule.setDurationSeconds(1);error("RULE_POLICY_MISMATCH",()->service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of())));
        rule.setDurationSeconds(1200);rule.setAllowResume(true);error("RULE_INVALID",()->service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of())));
        rule.setAllowResume(false);rule.setNavigationMode("UNKNOWN");error("RULE_INVALID",()->service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of())));
    }

    @Test
    void publishedSnapshotDoesNotShareMutableConfigurationObjects() {
        service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of()));service.publish(VERSION_ID);
        var ruleBefore=mapper.valueToTree(stored.getConfigs().get("ruleSnapshot"));
        var structureBefore=mapper.valueToTree(stored.getConfigs().get("structureSnapshot"));
        rule.setDurationSeconds(10);structure.getSections().get(0).setScore(100);
        assertEquals(ruleBefore,mapper.valueToTree(stored.getConfigs().get("ruleSnapshot")));
        assertEquals(structureBefore,mapper.valueToTree(stored.getConfigs().get("structureSnapshot")));
    }

    @Test
    void existingPracticeConstructorSourceContractAndPageResponseRemainCompatible() {
        var request=new CreateExamVersionRequest("Practice","PRACTICE",null,STRUCTURE_ID,RULE_ID,null,"CHAPTER",pool.chapters().get(0).id(),null,null,Map.of());
        var result=service.create(EXAM_ID,request);assertNull(result.selectionMode());assertNull(result.licenseClass());
        verifyNoInteractions(client);
        when(versions.findByExamIdAndStatus(eq(EXAM_ID),isNull(),any())).thenReturn(Page.empty());
        assertTrue(service.getList(EXAM_ID,null,PageRequest.of(0,20)).isEmpty());
    }

    @Test
    void publishingEndedWindowFailsBeforeSourceCalls() {
        service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of()));
        stored.setStartedAt(Instant.parse("2026-10-01T00:00:00Z"));
        stored.setEndedAt(Instant.parse("2026-10-02T00:00:00Z"));
        clearInvocations(client);
        error("VERSION_WINDOW_CLOSED",()->service.publish(VERSION_ID));
        verifyNoInteractions(client);
    }

    @Test
    void versionWindowCannotCrossIntoAnUnverifiedFutureMatrixPolicy() {
        var request=new CreateExamVersionRequest("Official","OFFICIAL",null,STRUCTURE_ID,RULE_ID,CRITERIA_ID,
                "BANK_VERSION",pool.chapters().get(0).id(),START,Instant.parse("2027-03-02T00:00:00Z"),Map.of(),
                "B",BANK,ExamSelectionMode.AUTO_MATRIX,List.of());
        error("MATRIX_POLICY_UNAVAILABLE",()->service.create(EXAM_ID,request));
        verifyNoInteractions(client);
    }

    @Test
    void sourceAnchorCannotBeAQuestionUuidOrAnUnrelatedContentId() {
        var request=new CreateExamVersionRequest("Official","OFFICIAL",null,STRUCTURE_ID,RULE_ID,CRITERIA_ID,
                "BANK_VERSION",pool.questions().get(0).id(),START,END,Map.of(),"B",BANK,ExamSelectionMode.AUTO_MATRIX,List.of());
        error("SOURCE_BAD_REQUEST",()->service.create(EXAM_ID,request));
        verify(versions,never()).save(any());
    }

    @Test
    void inactiveRuleOrCriteriaCannotBeUsedForOfficialDraft() {
        rule.setStatus("INACTIVE");error("RULE_NOT_READY",()->service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of())));
        rule.setStatus("ACTIVE");criteria.setStatus("INACTIVE");
        error("CRITERIA_NOT_READY",()->service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of())));
        verifyNoInteractions(client);
    }

    @Test
    void versionNumberOverflowCannotWrapToANegativeNumber() {
        when(versions.findMaxVersionNoByExamId(EXAM_ID)).thenReturn(Integer.MAX_VALUE);
        error("VERSION_NUMBER_EXHAUSTED",()->service.create(EXAM_ID,request(ExamSelectionMode.AUTO_MATRIX,List.of())));
        verify(versions,never()).save(any());
    }

    private CreateExamVersionRequest request(ExamSelectionMode mode,List<UUID> ids) {
        return new CreateExamVersionRequest("Official B","OFFICIAL",null,STRUCTURE_ID,RULE_ID,CRITERIA_ID,
                "BANK_VERSION",pool.chapters().get(0).id(),START,END,Map.of(),"B",BANK,mode,ids);
    }
    private List<UUID> manualIds() {
        return selector.select("B",START,pool,structure.getSections(),ExamSelectionMode.AUTO_MATRIX,null)
                .questions().stream().map(ExamSourceQuestionMetadata::id).toList();
    }
    private void error(String code,Runnable action) {
        var error=assertThrows(ExaminationException.class,action::run);assertEquals(code,error.getCode());
    }
}
