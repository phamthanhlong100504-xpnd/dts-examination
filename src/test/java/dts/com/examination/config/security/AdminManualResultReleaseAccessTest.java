package dts.com.examination.config.security;

import dts.com.examination.api.controller.AdminOfficialExamSessionController;
import dts.com.examination.application.service.AdminOfficialSessions;
import dts.com.examination.application.service.session.OfficialExamSessionService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import java.security.Principal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes=AdminManualResultReleaseAccessTest.Config.class)
class AdminManualResultReleaseAccessTest {
    @Autowired AdminOfficialExamSessionController controller;
    @Autowired OfficialExamSessionService official;
    private static final UUID SESSION=new UUID(10,20),ACTOR=new UUID(1,2);
    @BeforeEach void before(){SecurityContextHolder.clearContext();reset(official);}
    @AfterEach void after(){SecurityContextHolder.clearContext();}
    private Principal auth(String...grants){
        var token=new UsernamePasswordAuthenticationToken(ACTOR.toString(),null,
            Arrays.stream(grants).map(SimpleGrantedAuthority::new).toList());
        SecurityContextHolder.getContext().setAuthentication(token);
        return ()->ACTOR.toString();
    }
    @Test void studentCannotReleaseEvenWithGrant(){
        Principal p=auth("ROLE_STUDENT","PERM_official-exams:release-result");
        assertThrows(AccessDeniedException.class,()->controller.releaseResult(SESSION,p));verifyNoInteractions(official);
    }
    @Test void adminNeedsExactReleaseGrant(){
        Principal p=auth("ROLE_ADMIN","PERM_exam_session:read","PERM_exams:update");
        assertThrows(AccessDeniedException.class,()->controller.releaseResult(SESSION,p));verifyNoInteractions(official);
    }
    @Test void grantedAdminInvokesOwnerOnceAndNoStore(){
        Principal p=auth("ROLE_ADMIN","PERM_official-exams:release-result");
        var response=controller.releaseResult(SESSION,p);
        assertEquals(204,response.getStatusCode().value());
        assertEquals("no-store",response.getHeaders().getFirst("Cache-Control"));
        verify(official,times(1)).releaseManually(SESSION,ACTOR);
    }
    @Configuration(proxyBeanMethods=false)
    @EnableMethodSecurity(proxyTargetClass=true)
    @Import({AdminOfficialExamSessionController.class,ExamAdminAccess.class})
    static class Config {
        @Bean AdminOfficialSessions sessions(){return mock(AdminOfficialSessions.class);}
        @Bean OfficialExamSessionService official(){return mock(OfficialExamSessionService.class);}
    }
}
