package dts.com.examination.config.security;

import dts.com.examination.api.controller.AdminOfficialExamSessionController;
import dts.com.examination.api.controller.AdminOfficialExamVersionPreviewController;
import dts.com.examination.application.service.AdminOfficialSessions;
import dts.com.examination.application.service.AdminOfficialVersionPreview;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.Arrays;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes=AdminOfficialMonitoringAccessTest.Config.class)
class AdminOfficialMonitoringAccessTest {
    static final UUID ID = new UUID(0,10);
    @Autowired AdminOfficialExamSessionController sessions;
    @Autowired AdminOfficialExamVersionPreviewController versions;
    @Autowired AdminOfficialSessions service;
    @Autowired AdminOfficialVersionPreview preview;
    @BeforeEach void clear() { SecurityContextHolder.clearContext(); reset(service,preview); }
    @AfterEach void done() { SecurityContextHolder.clearContext(); }

    @Test void monitoringNeedsExplicitSubjectGrantEvenForAdmin() {
        auth("ROLE_ADMIN", "PERM_exams:read");
        assertThrows(AccessDeniedException.class, () -> sessions.detail(ID));
        verifyNoInteractions(service);
        auth("ROLE_ADMIN", "PERM_exam_session:read");
        assertEquals("no-store",sessions.detail(ID).getHeaders().getFirst("Cache-Control"));
        verify(service).detail(ID);
    }
    @Test void learnerCannotReadOtherPeoplesOfficialSessions() {
        auth("ROLE_STUDENT","PERM_exam_session:read","PERM_exams:read");
        assertThrows(AccessDeniedException.class, () -> sessions.resultRelease(ID));
        verifyNoInteractions(service);
    }
    @Test void existingVersionReadGrantCanRunPureValidationOnly() {
        auth("ROLE_ADMIN","PERM_exams:read");
        assertEquals("no-store",versions.validate(ID).getHeaders().getFirst("Cache-Control"));
        verify(preview).validate(ID);
    }
    @Test void adminRoleWithoutGrantCannotValidateOrMonitor() {
        auth("ROLE_ADMIN");
        assertThrows(AccessDeniedException.class, () -> versions.validate(ID));
        assertThrows(AccessDeniedException.class, () -> sessions.detail(ID));
        verifyNoInteractions(service,preview);
    }
    private void auth(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                ID,null,Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
    @Configuration(proxyBeanMethods=false)
    @EnableMethodSecurity(proxyTargetClass=true)
    @Import({ExamAdminAccess.class,AdminOfficialExamSessionController.class,AdminOfficialExamVersionPreviewController.class})
    static class Config {
        @Bean AdminOfficialSessions sessions() { return mock(AdminOfficialSessions.class); }
        @Bean AdminOfficialVersionPreview preview() { return mock(AdminOfficialVersionPreview.class); }
    }
}
