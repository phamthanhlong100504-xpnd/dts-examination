package dts.com.examination.config.security;

import dts.com.examination.api.controller.StudentExamController;
import dts.com.examination.api.response.StudentExamListResponse;
import dts.com.examination.application.exception.GlobalExceptionHandler;
import dts.com.examination.application.service.StudentExamReadService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes=StudentExamAccessTest.SecurityTestConfig.class)
class StudentExamAccessTest {
    private static final UUID USER=new UUID(0,100),EXAM=new UUID(0,101),VERSION=new UUID(0,102);
    @Autowired StudentExamController controller;
    @Autowired StudentExamReadService service;
    @BeforeEach void setup(){SecurityContextHolder.clearContext();reset(service);}
    @AfterEach void clear(){SecurityContextHolder.clearContext();}

    @Test void studentNeedsNoAdministrativePermissionToReadBothEndpoints() {
        var student=authenticate("ROLE_STUDENT");controller.list(student,"OPEN",null,0,20);controller.detail(student,EXAM,null);
        verify(service).list(USER,"OPEN",null,0,20);verify(service).detail(USER,EXAM,null);verifyNoMoreInteractions(service);
    }
    @Test void administrativePermissionsAloneCannotOpenStudentEndpoints() {
        for(String role:List.of("ROLE_ADMIN","ROLE_TEACHER","PERM_exams:read")){
            var account=authenticate(role,"PERM_exams:read","PERM_exams:update");
            assertThrows(AccessDeniedException.class,()->controller.list(account,"OPEN",null,0,20));
            assertThrows(AccessDeniedException.class,()->controller.detail(account,EXAM,null));
        }verifyNoInteractions(service);
    }
    @Test void anonymousAuthorityCannotSpoofStudentAuthentication() {
        var anonymous=new AnonymousAuthenticationToken("unit","anonymous",List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
        SecurityContextHolder.getContext().setAuthentication(anonymous);
        assertThrows(AccessDeniedException.class,()->controller.list(anonymous,"OPEN",null,0,20));verifyNoInteractions(service);
    }
    @Test void unauthenticatedAndMissingContextsAreDeniedBeforeServiceCalls() {
        var unauthenticated=new UsernamePasswordAuthenticationToken(USER,null);SecurityContextHolder.getContext().setAuthentication(unauthenticated);
        assertThrows(AccessDeniedException.class,()->controller.detail(unauthenticated,EXAM,null));SecurityContextHolder.clearContext();
        assertThrows(AuthenticationException.class,()->controller.list(unauthenticated,"OPEN",null,0,20));verifyNoInteractions(service);
    }
    @Test void successfulControllerResponsesCarryNoStore() {
        var student=authenticate("ROLE_STUDENT");assertEquals("no-store",controller.list(student,"OPEN",null,0,20).getHeaders().getFirst("Cache-Control"));
        assertEquals("no-store",controller.detail(student,EXAM,null).getHeaders().getFirst("Cache-Control"));
    }
    @Test void mvcRejectsNonIntegerPagingAndMalformedUuidUsingExistingErrorContract() throws Exception {
        var mvc=MockMvcBuilders.standaloneSetup(new StudentExamController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/v1/student/exams").param("page","abc").principal(()->USER.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST")).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/v1/student/exams/invalid-uuid").principal(()->USER.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));verifyNoInteractions(service);
    }
    @Test void mvcDefaultsPagingAndReturnsDirectJsonRatherThanPracticeEnvelope() throws Exception {
        when(service.list(USER,"OPEN",null,0,20)).thenReturn(new StudentExamListResponse("B","OPEN",0,20,0,0,List.of(),Instant.parse("2026-11-01T00:00:00Z")));
        var mvc=MockMvcBuilders.standaloneSetup(new StudentExamController(service)).build();
        mvc.perform(get("/api/v1/student/exams").principal(()->USER.toString())).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.selectedLicenseClass").value("B"))
                .andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.data").doesNotExist());
        verify(service).list(USER,"OPEN",null,0,20);
    }
    @Test void detailForwardsAnExplicitHistoricalVersionAndAuthenticatedOwner() {
        var student=authenticate("ROLE_STUDENT");controller.detail(student,EXAM,VERSION);verify(service).detail(USER,EXAM,VERSION);verifyNoMoreInteractions(service);
    }

    private Authentication authenticate(String...authorities){var auth=new UsernamePasswordAuthenticationToken(USER,null,
            Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());SecurityContextHolder.getContext().setAuthentication(auth);return auth;}
    @Configuration(proxyBeanMethods=false)
    @EnableMethodSecurity(proxyTargetClass=true)
    @Import(StudentExamController.class)
    static class SecurityTestConfig{@Bean StudentExamReadService service(){return mock(StudentExamReadService.class);}}
}
