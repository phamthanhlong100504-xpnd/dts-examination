package dts.com.examination.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.application.client.*;
import dts.com.examination.application.exception.ExaminationException;
import feign.Logger;
import feign.Retryer;
import org.junit.jupiter.api.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.context.request.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StudentIdentityProfileTest {
    private static final UUID USER=new UUID(0,100);
    private static final String AUTHORIZATION="Bearer opaque-unit-test-credential";
    private IdentityProfileClient client;
    private CurrentStudentProfileReader reader;
    private MockHttpServletRequest request;
    @BeforeEach void setup(){client=mock(IdentityProfileClient.class);reader=new CurrentStudentProfileReader(client);
        request=new MockHttpServletRequest();request.addHeader("Authorization",AUTHORIZATION);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        when(client.getCurrentUser(AUTHORIZATION)).thenReturn(profile(USER,"ACTIVE","B"));}
    @AfterEach void clear(){RequestContextHolder.resetRequestAttributes();}

    @Test void validReaderForwardsOnlyTheCurrentRequestCredentialAndUsesServerProfile() {
        assertEquals("B",reader.selectedLicenseClass(USER));verify(client).getCurrentUser(AUTHORIZATION);verifyNoMoreInteractions(client);
        when(client.getCurrentUser(AUTHORIZATION)).thenReturn(profile(USER,"ACTIVE"," a1 "));assertEquals("A1",reader.selectedLicenseClass(USER));
    }
    @Test void missingRequestOrAuthenticatedSubjectCannotReadIdentity() {
        assertThrows(InsufficientAuthenticationException.class,()->reader.selectedLicenseClass(null));RequestContextHolder.resetRequestAttributes();
        assertThrows(InsufficientAuthenticationException.class,()->reader.selectedLicenseClass(USER));verifyNoInteractions(client);
    }
    @Test void missingMalformedOrEmptyBearerHeadersAreRejectedBeforeNetworkCalls() {
        for(String header:Arrays.asList(null,"Basic credential","Bearer ","Bearer \ncredential","Bearer \rcredential")) {
            var invalid=new MockHttpServletRequest();if(header!=null)invalid.addHeader("Authorization",header);
            RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(invalid));
            assertThrows(InsufficientAuthenticationException.class,()->reader.selectedLicenseClass(USER));
        }verifyNoInteractions(client);
    }
    @Test void identityIdMustMatchJwtSubject() {
        when(client.getCurrentUser(AUTHORIZATION)).thenReturn(profile(new UUID(0,999),"ACTIVE","B"));error("ACCESS_DENIED",403);
    }
    @Test void pendingLockedOrBannedAccountsCannotBrowseExams() {
        for(String state:List.of("PENDING","LOCKED","BANNED")){when(client.getCurrentUser(AUTHORIZATION)).thenReturn(profile(USER,state,"B"));error("ACCESS_DENIED",403);}
    }
    @Test void absentLicenseRequiresIdentitySelectionWithoutInventingADefault() {
        for(String license:Arrays.asList(null,""," ")){when(client.getCurrentUser(AUTHORIZATION)).thenReturn(profile(USER,"ACTIVE",license));error("LICENSE_CLASS_REQUIRED",409);}
    }
    @Test void unsupportedLicenseIsAnInvalidUpstreamResponse() {
        when(client.getCurrentUser(AUTHORIZATION)).thenReturn(profile(USER,"ACTIVE","UNKNOWN"));error("IDENTITY_INVALID_RESPONSE",502);
    }
    @Test void incompleteOrFailedIdentityEnvelopeIsNotAnEmptyExamList() {
        for(var invalid:Arrays.asList(null,new IdentityMeResponse(false,new IdentityMeResponse.Profile(USER,"ACTIVE","B")),
                new IdentityMeResponse(true,null),profile(null,"ACTIVE","B"),profile(USER,null,"B"))){
            when(client.getCurrentUser(AUTHORIZATION)).thenReturn(invalid);error("IDENTITY_INVALID_RESPONSE",502);
        }
    }
    @Test void upstreamStatusesKeepAuthenticationAndOutageSemantics() {
        var unauthorized=IdentityProfileClientFallback.failureForStatus(401);assertEquals(401,unauthorized.getStatus().value());assertEquals("UNAUTHENTICATED",unauthorized.getCode());
        for(int status:new int[]{403,404})assertEquals("ACCESS_DENIED",IdentityProfileClientFallback.failureForStatus(status).getCode());
        for(int status:new int[]{-1,408,429,500,503})assertEquals("IDENTITY_UNAVAILABLE",IdentityProfileClientFallback.failureForStatus(status).getCode());
        for(int status:new int[]{200,302,400,422})assertEquals("IDENTITY_INVALID_RESPONSE",IdentityProfileClientFallback.failureForStatus(status).getCode());
    }
    @Test void wrappedKnownErrorsArePreservedInsteadOfReturningFallbackProfiles() {
        var known=IdentityProfileClientFallback.failureForStatus(401);assertSame(known,IdentityProfileClientFallback.failure(new RuntimeException(known)));
        var fallback=new IdentityProfileClientFallback().create(new RuntimeException("Unavailable"));
        assertEquals("IDENTITY_UNAVAILABLE",assertThrows(ExaminationException.class,()->fallback.getCurrentUser(AUTHORIZATION)).getCode());
    }
    @Test void identityClientDisablesCredentialLoggingRetriesAndRedirects() {
        var config=new IdentityProfileFeignConfiguration();assertEquals(Logger.Level.NONE,config.identityProfileLoggerLevel());assertSame(Retryer.NEVER_RETRY,config.identityProfileRetryer());
        var options=config.identityProfileRequestOptions();assertEquals(5000,options.connectTimeoutMillis());assertEquals(10000,options.readTimeoutMillis());assertFalse(options.isFollowRedirects());
    }
    @Test void identityContractUsesOneExactCurrentProfileRouteAndExplicitBearerHeader() throws Exception {
        var client=IdentityProfileClient.class.getAnnotation(FeignClient.class);assertEquals("${IDENTITY_SERVICE_URL:}",client.url());
        assertArrayEquals(new Class<?>[]{IdentityProfileFeignConfiguration.class},client.configuration());
        var method=IdentityProfileClient.class.getMethod("getCurrentUser",String.class);assertArrayEquals(new String[]{"/api/v1/users/me"},method.getAnnotation(GetMapping.class).value());
        assertEquals("Authorization",((RequestHeader)method.getParameterAnnotations()[0][0]).value());
    }
    @Test void minimalProfileDecoderDropsUnneededPersonalFields() throws Exception {
        var mapper=new ObjectMapper();var response=mapper.readValue("{\"success\":true,\"message\":\"ok\",\"data\":{\"id\":\""+USER+"\",\"status\":\"ACTIVE\",\"selectedLicenseClass\":\"B\",\"email\":\"unused\",\"fullName\":\"unused\",\"phoneNumber\":\"unused\"}}",IdentityMeResponse.class);
        assertEquals(USER,response.data().id());var safe=mapper.valueToTree(response).get("data");assertFalse(safe.has("email"));assertFalse(safe.has("fullName"));assertFalse(safe.has("phoneNumber"));
    }
    @Test void networkFailureMessageCannotExposeCredentialOrRequestBody() {
        when(client.getCurrentUser(AUTHORIZATION)).thenThrow(new RuntimeException("Request Authorization: "+AUTHORIZATION));
        var error=assertThrows(ExaminationException.class,()->reader.selectedLicenseClass(USER));assertEquals("IDENTITY_UNAVAILABLE",error.getCode());
        assertFalse(error.getMessage().contains(AUTHORIZATION));assertNull(error.getCause());
    }
    private IdentityMeResponse profile(UUID id,String status,String license){return new IdentityMeResponse(true,new IdentityMeResponse.Profile(id,status,license));}
    private void error(String code,int http){var error=assertThrows(ExaminationException.class,()->reader.selectedLicenseClass(USER));assertEquals(code,error.getCode());assertEquals(http,error.getStatus().value());}
}
