package dts.com.examination.config.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.api.controller.ExamController;
import dts.com.examination.api.controller.ExamCriteriaController;
import dts.com.examination.application.exception.BusinessRuleException;
import dts.com.examination.application.exception.BusinessValidationException;
import dts.com.examination.application.exception.ExaminationException;
import dts.com.examination.application.exception.GlobalExceptionHandler;
import dts.com.examination.application.exception.ResourceNotFoundException;
import dts.com.examination.application.service.ExamCriteriaService;
import dts.com.examination.application.service.ExamService;
import dts.com.examination.application.service.ExamSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ExamAdminAuthorizationTest.SecurityTestConfig.class)
class ExamErrorContractTest {

    private static final UUID EXAM_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String TRACE_ID = "00000000-0000-0000-0000-0000000000aa";

    @Autowired
    private ApplicationContext context;

    private MockMvc mvc;
    private ExamService examService;
    private ExamCriteriaService criteriaService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        MDC.remove("traceId");
        examService = context.getBean(ExamService.class);
        criteriaService = context.getBean(ExamCriteriaService.class);
        reset(examService, criteriaService, context.getBean(ExamSessionService.class));
        mvc = buildMvc(true);
        authenticate("ROLE_ADMIN", "PERM_exams:read", "PERM_exams:create", "PERM_exams:update");
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        MDC.remove("traceId");
    }

    @Test
    void invalidUuidReturns400BeforeServiceCall() throws Exception {
        error(get("/api/v1/exams/not-a-uuid"), 400, "BAD_REQUEST");
        verifyNoInteractions(examService);
    }

    @Test
    void nonIntegerPageReturns400BeforeServiceCall() throws Exception {
        error(get("/api/v1/exam-criterias").param("page", "not-an-integer"), 400, "BAD_REQUEST");
        verifyNoInteractions(criteriaService);
    }

    @Test
    void malformedJsonReturns400BeforeServiceCall() throws Exception {
        error(post("/api/v1/exams").contentType(MediaType.APPLICATION_JSON).content("{"),
                400, "BAD_REQUEST");
        verifyNoInteractions(examService);
    }

    @Test
    void blankTitleReturns400BeforeServiceCall() throws Exception {
        error(post("/api/v1/exams").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"\"}"),
                400, "BAD_REQUEST");
        verifyNoInteractions(examService);
    }

    @Test
    void studentReceivesStableAccessDeniedCode() throws Exception {
        authenticate("ROLE_STUDENT", "PERM_exams:read");
        error(get("/api/v1/exams"), 403, "ACCESS_DENIED");
        verifyNoInteractions(examService);
    }

    @Test
    void authenticationFailureHasBearerChallenge() throws Exception {
        when(examService.getDetail(EXAM_ID)).thenThrow(new BadCredentialsException("Authentication failed"));
        MvcResult result = error(get("/api/v1/exams/" + EXAM_ID), 401, "UNAUTHENTICATED");
        assertEquals("Bearer", result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    void missingExamReturns404() throws Exception {
        when(examService.getDetail(EXAM_ID)).thenThrow(new ResourceNotFoundException("Exam not found"));
        error(get("/api/v1/exams/" + EXAM_ID), 404, "NOT_FOUND");
    }

    @Test
    void legacyBusinessValidationReturns400() throws Exception {
        when(criteriaService.getCriteriaList(0, 20, null, null))
                .thenThrow(new BusinessValidationException("Invalid criteria"));
        error(get("/api/v1/exam-criterias"), 400, "BAD_REQUEST");
    }

    @Test
    void legacyBusinessRuleReturns409() throws Exception {
        doThrow(new BusinessRuleException("Cannot update an archived exam"))
                .when(examService).update(any(UUID.class), any());
        error(patch("/api/v1/exams/" + EXAM_ID)
                .contentType(MediaType.APPLICATION_JSON).content("{}"), 409, "CONFLICT");
    }

    @Test
    void codedBusinessErrorPreservesItsSpecificCode() throws Exception {
        when(examService.getDetail(EXAM_ID)).thenThrow(new ExaminationException(
                HttpStatus.CONFLICT, "EXAM_NOT_OPEN", "Exam has not opened yet"));
        error(get("/api/v1/exams/" + EXAM_ID), 409, "EXAM_NOT_OPEN");
    }

    @Test
    void sourceUnavailableErrorIsTransportedAs503() throws Exception {
        when(examService.getDetail(EXAM_ID)).thenThrow(new ExaminationException(
                HttpStatus.SERVICE_UNAVAILABLE, "SOURCE_UNAVAILABLE", "Question source unavailable"));
        error(get("/api/v1/exams/" + EXAM_ID), 503, "SOURCE_UNAVAILABLE");
    }

    @Test
    void invalidSourceResponseErrorIsTransportedAs502() throws Exception {
        when(examService.getDetail(EXAM_ID)).thenThrow(new ExaminationException(
                HttpStatus.BAD_GATEWAY, "SOURCE_INVALID_RESPONSE", "Invalid question source response"));
        error(get("/api/v1/exams/" + EXAM_ID), 502, "SOURCE_INVALID_RESPONSE");
    }

    @Test
    void unexpectedFailureReturns500WithoutInternalDetails() throws Exception {
        String internalMarker = "internal backend diagnostic marker";
        when(examService.getDetail(EXAM_ID)).thenThrow(new IllegalStateException(internalMarker));
        MvcResult result = error(get("/api/v1/exams/" + EXAM_ID), 500, "INTERNAL_ERROR");
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains(internalMarker));
        assertFalse(body.contains("IllegalStateException"));
    }

    @Test
    void missingTraceHeaderGeneratesMatchingTraceAndClearsMdc() throws Exception {
        authenticate("ROLE_STUDENT", "PERM_exams:read");
        MvcResult result = mvc.perform(get("/api/v1/exams"))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
        JsonNode body = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray());
        String traceId = result.getResponse().getHeader("X-Request-ID");
        assertNotNull(traceId);
        assertEquals(1, result.getResponse().getHeaders("X-Request-ID").size());
        UUID.fromString(traceId);
        assertEquals(traceId, body.path("traceId").asText());
        assertEquals("ACCESS_DENIED", body.path("code").asText());
        assertNull(MDC.get("traceId"));
        verifyNoInteractions(examService);
    }

    @Test
    void handlerUsesRequestTraceWhenTraceFilterIsAbsent() throws Exception {
        mvc = buildMvc(false);
        authenticate("ROLE_STUDENT", "PERM_exams:read");
        error(get("/api/v1/exams"), 403, "ACCESS_DENIED");
        verifyNoInteractions(examService);
    }

    private MockMvc buildMvc(boolean includeTraceFilter) {
        var builder = MockMvcBuilders.standaloneSetup(context.getBean(ExamController.class),
                        context.getBean(ExamCriteriaController.class))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver());
        if (includeTraceFilter) {
            builder.addFilters(new MdcTraceIdFilter());
        }
        return builder.build();
    }

    private MvcResult error(MockHttpServletRequestBuilder request, int http, String code) throws Exception {
        MvcResult result = mvc.perform(request.header("X-Request-ID", TRACE_ID))
                .andExpect(status().is(http))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Request-ID", TRACE_ID))
                .andReturn();
        JsonNode body = new ObjectMapper().readTree(result.getResponse().getContentAsByteArray());
        assertEquals(http, body.path("status").asInt());
        assertEquals(1, result.getResponse().getHeaders("X-Request-ID").size());
        assertEquals(HttpStatus.valueOf(http).getReasonPhrase(), body.path("error").asText());
        assertFalse(body.path("message").asText().isBlank());
        assertEquals(code, body.path("code").asText());
        assertEquals(TRACE_ID, body.path("traceId").asText());
        assertNull(MDC.get("traceId"));
        return result;
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                EXAM_ID, null, Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}