package dts.com.examination.config.security;

import dts.com.examination.api.controller.ExamController;
import dts.com.examination.api.controller.ExamCriteriaController;
import dts.com.examination.api.controller.ExamRuleController;
import dts.com.examination.api.controller.ExamStructureController;
import dts.com.examination.api.controller.ExamVersionController;
import dts.com.examination.application.service.ExamCriteriaService;
import dts.com.examination.application.service.ExamRuleService;
import dts.com.examination.application.service.ExamService;
import dts.com.examination.application.service.ExamSessionService;
import dts.com.examination.application.service.ExamStructureService;
import dts.com.examination.application.service.ExamVersionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ExamAdminAuthorizationTest.SecurityTestConfig.class)
class ExamAdminAuthorizationTest {

    private static final UUID EXAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID VERSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final PageRequest PAGE = PageRequest.of(0, 20);

    @Autowired
    private ApplicationContext context;

    @BeforeEach
    void resetState() {
        SecurityContextHolder.clearContext();
        reset(services());
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(strings = {"exam", "exam_structure", "exam_criteria", "exam_rule", "exam_version"})
    void adminWithModuleReadReachesOnlyTheRequestedService(String resource) {
        authenticate("ROLE_ADMIN", "PERM_exams:read");
        read(resource);
        verifyRead(resource);
        verifyNoMoreInteractions(services());
    }

    @ParameterizedTest
    @ValueSource(strings = {"exam", "exam_structure", "exam_criteria", "exam_rule", "exam_version"})
    void studentWithSeededExamReadCannotReadAdministrativeResources(String resource) {
        authenticate("ROLE_STUDENT", "PERM_exams:read");
        assertThrows(AccessDeniedException.class, () -> read(resource));
        verifyNoInteractions(services());
    }

    @ParameterizedTest
    @ValueSource(strings = {"exam", "exam_structure", "exam_criteria", "exam_rule", "exam_version"})
    void teacherWithReadAndCreateCannotReadAdministrativeResources(String resource) {
        authenticate("ROLE_TEACHER", "PERM_exams:read", "PERM_exams:create");
        assertThrows(AccessDeniedException.class, () -> read(resource));
        verifyNoInteractions(services());
    }

    @ParameterizedTest
    @ValueSource(strings = {"exam", "exam_structure", "exam_criteria", "exam_rule", "exam_version"})
    void adminRoleAloneDoesNotGrantRead(String resource) {
        authenticate("ROLE_ADMIN");
        assertThrows(AccessDeniedException.class, () -> read(resource));
        verifyNoInteractions(services());
    }

    @ParameterizedTest
    @ValueSource(strings = {"exam", "exam_structure", "exam_criteria", "exam_rule", "exam_version"})
    void modulePermissionWithoutAdminRoleDoesNotGrantRead(String resource) {
        authenticate("PERM_exams:read");
        assertThrows(AccessDeniedException.class, () -> read(resource));
        verifyNoInteractions(services());
    }

    @Test
    void readPermissionDoesNotGrantAdministrativeWrites() {
        authenticate("ROLE_ADMIN", "PERM_exams:read");
        assertThrows(AccessDeniedException.class, () -> exam().create(null));
        assertThrows(AccessDeniedException.class, () -> exam().update(EXAM_ID, null));
        assertThrows(AccessDeniedException.class, () -> exam().delete(EXAM_ID));
        assertThrows(AccessDeniedException.class, () -> version().publishExamVersion(VERSION_ID));
        assertThrows(AccessDeniedException.class, () -> version().archiveExamVersion(VERSION_ID));
        assertThrows(AccessDeniedException.class, () -> version().cloneExamVersion(VERSION_ID));
        verifyNoInteractions(services());
    }

    @Test
    void createPermissionGrantsCreationAndClone() {
        authenticate("ROLE_ADMIN", "PERM_exams:create");
        exam().create(null);
        version().cloneExamVersion(VERSION_ID);
        verify(context.getBean(ExamService.class)).create(null);
        verify(context.getBean(ExamVersionService.class)).clone(VERSION_ID);
        verifyNoMoreInteractions(services());
    }

    @Test
    void createPermissionDoesNotGrantUpdateDeletePublishOrArchive() {
        authenticate("ROLE_ADMIN", "PERM_exams:create");
        assertThrows(AccessDeniedException.class, () -> exam().update(EXAM_ID, null));
        assertThrows(AccessDeniedException.class, () -> exam().delete(EXAM_ID));
        assertThrows(AccessDeniedException.class, () -> version().publishExamVersion(VERSION_ID));
        assertThrows(AccessDeniedException.class, () -> version().archiveExamVersion(VERSION_ID));
        verifyNoInteractions(services());
    }

    @Test
    void updatePermissionCanUpdateAndArchiveButCannotPublishWithoutSpecificGrant() {
        authenticate("ROLE_ADMIN", "PERM_exams:update");
        exam().update(EXAM_ID, null);
        assertThrows(AccessDeniedException.class, () -> version().publishExamVersion(VERSION_ID));
        version().archiveExamVersion(VERSION_ID);
        verify(context.getBean(ExamService.class)).update(EXAM_ID, null);
        verify(context.getBean(ExamVersionService.class)).archive(VERSION_ID);
        verifyNoMoreInteractions(services());
    }

    @Test
    void deletePermissionDoesNotGrantUpdateOrPublication() {
        authenticate("ROLE_ADMIN", "PERM_exams:delete");
        assertThrows(AccessDeniedException.class, () -> exam().update(EXAM_ID, null));
        assertThrows(AccessDeniedException.class, () -> version().publishExamVersion(VERSION_ID));
        exam().delete(EXAM_ID);
        verify(context.getBean(ExamService.class)).delete(EXAM_ID);
        verifyNoMoreInteractions(services());
    }

    @Test
    void studentWithAllModulePermissionsStillCannotWrite() {
        authenticate("ROLE_STUDENT", "PERM_exams:read", "PERM_exams:create",
                "PERM_exams:update", "PERM_exams:delete");
        assertThrows(AccessDeniedException.class, () -> exam().create(null));
        assertThrows(AccessDeniedException.class, () -> exam().update(EXAM_ID, null));
        assertThrows(AccessDeniedException.class, () -> exam().delete(EXAM_ID));
        assertThrows(AccessDeniedException.class, () -> version().publishExamVersion(VERSION_ID));
        assertThrows(AccessDeniedException.class, () -> version().archiveExamVersion(VERSION_ID));
        assertThrows(AccessDeniedException.class, () -> version().cloneExamVersion(VERSION_ID));
        verifyNoInteractions(services());
    }

    @Test
    void studentHistoryUsesTheAuthenticatedUserWithoutAdminPermission() {
        Authentication student = authenticate("ROLE_STUDENT");
        exam().getSessionHistory(EXAM_ID, 0, 20, null, null, student);
        verify(context.getBean(ExamSessionService.class))
                .getSessionHistory(EXAM_ID, EXAM_ID, 0, 20, null, null);
        verifyNoMoreInteractions(services());
    }

    @Test
    void legacyReadIsLimitedToItsResourceAndStillRequiresAdmin() {
        authenticate("ROLE_ADMIN", "PERM_exam_structure:read");
        read("exam_structure");
        assertThrows(AccessDeniedException.class, () -> read("exam"));
        assertThrows(AccessDeniedException.class, () -> read("exam_version"));
        verifyRead("exam_structure");
        verifyNoMoreInteractions(services());

        authenticate("ROLE_STUDENT", "PERM_exam_structure:read");
        assertThrows(AccessDeniedException.class, () -> read("exam_structure"));
        verifyNoMoreInteractions(services());
    }

    @Test
    void subjectSpecificPublishPermissionDoesNotGrantEditArchiveOrReview() {
        authenticate("ROLE_ADMIN", "PERM_official-exams:publish");
        version().publishExamVersion(VERSION_ID);
        assertThrows(AccessDeniedException.class, () -> version().updateExamVersion(VERSION_ID, null));
        assertThrows(AccessDeniedException.class, () -> version().archiveExamVersion(VERSION_ID));
        verify(context.getBean(ExamVersionService.class)).publish(VERSION_ID);
        verifyNoMoreInteractions(services());
    }

    @Test
    void legacyPublishAloneNoLongerAuthorizesOfficialPublication() {
        authenticate("ROLE_ADMIN", "PERM_exam_version:publish");
        assertThrows(AccessDeniedException.class, () -> version().publishExamVersion(VERSION_ID));
        assertThrows(AccessDeniedException.class, () -> version().archiveExamVersion(VERSION_ID));
        assertThrows(AccessDeniedException.class, () -> version().updateExamVersion(VERSION_ID, null));
        verifyNoInteractions(services());
    }

    @Test
    void anonymousAuthenticationCannotUseAdministrativeAuthorities() {
        Authentication anonymous = new AnonymousAuthenticationToken("test", "anonymous",
                Arrays.asList(new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("PERM_exams:read")));
        SecurityContextHolder.getContext().setAuthentication(anonymous);
        assertThrows(AccessDeniedException.class, () -> read("exam"));
        verifyNoInteractions(services());
    }

    @Test
    void gateRejectsMissingUnauthenticatedAndUnsupportedRequests() {
        ExamAdminAccess access = context.getBean(ExamAdminAccess.class);
        assertFalse(access.can(null, "exam", "read"));
        Authentication unauthenticated = new UsernamePasswordAuthenticationToken(EXAM_ID, null);
        assertFalse(access.can(unauthenticated, "exam", "read"));

        Authentication admin = authenticate("ROLE_ADMIN", "PERM_exams:read", "PERM_exams:update");
        assertFalse(access.can(admin, null, "read"));
        assertFalse(access.can(admin, "exam", null));
        assertFalse(access.can(admin, "users", "read"));
        assertFalse(access.can(admin, "exam", "unknown"));
        assertFalse(access.can(admin, "exam_rule", "publish"));
        assertFalse(access.can(admin, "exam_version", "publish"));
        Authentication publisher = authenticate("ROLE_ADMIN", "PERM_official-exams:publish");
        assertTrue(access.can(publisher, "exam_version", "publish"));
        verifyNoInteractions(services());
    }

    private Authentication authenticate(String... authorities) {
        Authentication authentication = new UsernamePasswordAuthenticationToken(EXAM_ID, null,
                Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return authentication;
    }

    private Object read(String resource) {
        return switch (resource) {
            case "exam" -> exam().getList(null, null, null, PAGE);
            case "exam_structure" -> context.getBean(ExamStructureController.class).getList(null, null, PAGE);
            case "exam_criteria" -> context.getBean(ExamCriteriaController.class).getCriteriaList(0, 20, null, null);
            case "exam_rule" -> context.getBean(ExamRuleController.class).getRuleList(0, 20, null, null);
            case "exam_version" -> version().getExamVersionList(EXAM_ID, null, PAGE);
            default -> throw new IllegalArgumentException("Unknown test resource");
        };
    }

    private void verifyRead(String resource) {
        switch (resource) {
            case "exam" -> verify(context.getBean(ExamService.class)).getList(null, null, null, PAGE);
            case "exam_structure" -> verify(context.getBean(ExamStructureService.class)).getList(null, null, PAGE);
            case "exam_criteria" -> verify(context.getBean(ExamCriteriaService.class)).getCriteriaList(0, 20, null, null);
            case "exam_rule" -> verify(context.getBean(ExamRuleService.class)).getRuleList(0, 20, null, null);
            case "exam_version" -> verify(context.getBean(ExamVersionService.class)).getList(EXAM_ID, null, PAGE);
            default -> throw new IllegalArgumentException("Unknown test resource");
        }
    }

    private ExamController exam() { return context.getBean(ExamController.class); }
    private ExamVersionController version() { return context.getBean(ExamVersionController.class); }

    private Object[] services() {
        return new Object[] {context.getBean(ExamService.class), context.getBean(ExamSessionService.class),
                context.getBean(ExamStructureService.class), context.getBean(ExamCriteriaService.class),
                context.getBean(ExamRuleService.class), context.getBean(ExamVersionService.class)};
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    @Import({ExamAdminAccess.class, ExamController.class, ExamStructureController.class,
            ExamCriteriaController.class, ExamRuleController.class, ExamVersionController.class})
    static class SecurityTestConfig {
        @Bean ExamService examService() { return mock(ExamService.class); }
        @Bean ExamSessionService examSessionService() { return mock(ExamSessionService.class); }
        @Bean ExamStructureService examStructureService() { return mock(ExamStructureService.class); }
        @Bean ExamCriteriaService examCriteriaService() { return mock(ExamCriteriaService.class); }
        @Bean ExamRuleService examRuleService() { return mock(ExamRuleService.class); }
        @Bean ExamVersionService examVersionService() { return mock(ExamVersionService.class); }
    }
}