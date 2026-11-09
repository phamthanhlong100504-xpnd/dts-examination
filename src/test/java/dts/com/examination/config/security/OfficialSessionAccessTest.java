package dts.com.examination.config.security;

import dts.com.examination.api.controller.ExamSessionController;
import dts.com.examination.api.form.StartExamSessionRequest;
import dts.com.examination.api.response.ExamSessionResponse;
import dts.com.examination.application.service.ExamSessionService;
import dts.com.examination.application.service.session.OfficialExamSessionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes=OfficialSessionAccessTest.Config.class)
class OfficialSessionAccessTest {
    static final UUID USER=new UUID(0,100),SESSION=new UUID(0,200);
    @Autowired ExamSessionController controller;
    @Autowired ExamSessionService legacy;
    @Autowired OfficialExamSessionService official;
    @BeforeEach void before(){SecurityContextHolder.clearContext();reset(legacy,official);}
    @AfterEach void after(){SecurityContextHolder.clearContext();}

    @Test void authenticatedStudentCanReadOwnSessionWithoutAdminPermission() {
        var user=auth("ROLE_STUDENT");controller.getSessionDetail(SESSION,user);verify(legacy).getSessionDetail(SESSION,USER);
    }
    @Test void adminAndTeacherCannotUseStudentSessionEndpointsByAdministrativePermission() {
        for(var role:List.of("ROLE_ADMIN","ROLE_TEACHER")){
            var user=auth(role,"PERM_exams:read");assertThrows(AccessDeniedException.class,()->controller.getSessionDetail(SESSION,user));
        }verifyNoInteractions(legacy,official);
    }
    @Test void manualReleaseRequiresIndependentSubjectGrantNotGeneralUpdate() {
        var student=auth("ROLE_STUDENT","PERM_exams:update");assertThrows(AccessDeniedException.class,()->controller.releaseResult(SESSION,student));
        var admin=auth("ROLE_ADMIN");assertThrows(AccessDeniedException.class,()->controller.releaseResult(SESSION,admin));
        var legacy=auth("ROLE_ADMIN","PERM_exams:update");assertThrows(AccessDeniedException.class,()->controller.releaseResult(SESSION,legacy));
        var forbidden=auth("ROLE_STUDENT","PERM_official-exams:release-result");assertThrows(AccessDeniedException.class,()->controller.releaseResult(SESSION,forbidden));
        var allowed=auth("ROLE_ADMIN","PERM_official-exams:release-result");assertEquals(204,controller.releaseResult(SESSION,allowed).getStatusCode().value());
        verify(official).releaseManually(SESSION,USER);
    }
    @Test void creatingReturns201AndRecoveryReturns200WithNoStore() {
        var user=auth("ROLE_STUDENT");var request=StartExamSessionRequest.builder().examId(new UUID(0,101)).build();
        when(legacy.startSession(request,USER)).thenReturn(ExamSessionResponse.builder().sessionId(SESSION).resumedExisting(false).build());
        var created=controller.startSession(request,user);assertEquals(201,created.getStatusCode().value());assertEquals("no-store",created.getHeaders().getFirst("Cache-Control"));
        when(legacy.startSession(request,USER)).thenReturn(ExamSessionResponse.builder().sessionId(SESSION).resumedExisting(true).build());
        assertEquals(200,controller.startSession(request,user).getStatusCode().value());
    }
    @Test void sessionFilterAppliesNoStoreToAuthenticatedAndErrorResponses() throws Exception {
        var request=new org.springframework.mock.web.MockHttpServletRequest("GET","/api/v1/exam-sessions/invalid");
        var response=new org.springframework.mock.web.MockHttpServletResponse();
        new SessionNoStoreFilter().doFilter(request,response,(req,res)->((jakarta.servlet.http.HttpServletResponse)res).setStatus(404));
        assertEquals(404,response.getStatus());assertEquals("no-store",response.getHeader("Cache-Control"));
    }
    Authentication auth(String... roles){var auth=new UsernamePasswordAuthenticationToken(USER,null,Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList());SecurityContextHolder.getContext().setAuthentication(auth);return auth;}
    @Configuration(proxyBeanMethods=false)
    @EnableMethodSecurity(proxyTargetClass=true)
    @Import({ExamSessionController.class,ExamAdminAccess.class})
    static class Config {
        @Bean ExamSessionService legacy(){return mock(ExamSessionService.class);}
        @Bean OfficialExamSessionService official(){return mock(OfficialExamSessionService.class);}
    }
}
