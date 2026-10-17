package dts.com.examination.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/** Documentation only: controllers, DTOs and security remain the runtime contract. */
@Configuration(proxyBeanMethods = false)
public class Screen12OpenApiConfig {
    private static final String BEARER = "bearerAuth";
    private static final String SESSION = "/api/v1/exam-sessions";
    private static final String STUDENT = "/api/v1/student/exams";
    private static final Map<String, String> CREATE_SCHEMAS = Map.of(
            "/api/v1/exams", "ExamResponse", "/api/v1/exam-structures", "ExamStructureResponse",
            "/api/v1/exam-criterias", "ExamCriteriaResponse", "/api/v1/exam-rules", "ExamRuleResponse");

    @Bean
    public OpenAPI examinationOpenApi() {
        return new OpenAPI()
                .info(new Info().title("DTS Examination - Screen 12").version("1.0")
                        .description("Official examination administration and student attempts. "
                                + "These are direct Examination paths. Gateway/Next clients replace /api/v1/ "
                                + "with /api/examination/v1/. JWT subject determines the current user; "
                                + "never supply another user's ID. Student access also requires an ACTIVE Identity profile. "
                                + "Session endpoints are shared with legacy practice; the rules described here apply to OFFICIAL attempts."))
                .servers(List.of(new Server().url("/").description("Direct Examination service")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    public GroupedOpenApi screen12OpenApi() {
        return GroupedOpenApi.builder().group("screen12")
                .pathsToMatch(STUDENT, STUDENT + "/**", SESSION, SESSION + "/**",
                        "/api/v1/exams", "/api/v1/exams/**", "/api/v1/exam-versions/**",
                        "/api/v1/exam-structures", "/api/v1/exam-structures/**",
                        "/api/v1/exam-criterias", "/api/v1/exam-criterias/**",
                        "/api/v1/exam-rules", "/api/v1/exam-rules/**")
                .addOpenApiCustomizer(this::describeScreen12).build();
    }

    private void describeScreen12(OpenAPI api) {
        if (api.getComponents() == null) api.setComponents(new Components());
        api.getComponents().addSchemas("Screen12Error", new ObjectSchema()
                .addProperty("status", new IntegerSchema().description("HTTP status"))
                .addProperty("error", new StringSchema().description("HTTP reason phrase"))
                .addProperty("message", new StringSchema().description("Safe error description"))
                .addProperty("traceId", new StringSchema().description("Request correlation identifier"))
                .addProperty("code", new StringSchema().description("Stable application error code")));
        if (api.getPaths() == null) return;
        api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            boolean history = path.startsWith("/api/v1/exams/") && path.endsWith("/sessions");
            boolean student = path.startsWith(STUDENT) || path.startsWith(SESSION) || history;
            boolean release = path.endsWith("/release-result");
            operation.setSecurity(List.of(new SecurityRequirement().addList(BEARER)));
            operation.setTags(List.of(release || !student ? "Screen 12 administration" : "Screen 12 student"));
            operation.addExtension("x-screen12-authority", release ? "ADMIN + exams:update (or legacy exam:update)"
                    : history ? "Authenticated user; history scoped to JWT subject"
                    : student ? "STUDENT + ACTIVE profile + owned session" : "See examAdminAccess permission mapping");
            if (!student) {
                String action = method == PathItem.HttpMethod.GET ? "read" : method == PathItem.HttpMethod.DELETE ? "delete"
                        : method == PathItem.HttpMethod.PATCH || path.endsWith("/publish") || path.endsWith("/archive") ? "update" : "create";
                operation.addExtension("x-screen12-authority", "ADMIN + exams:" + action + " (or equivalent legacy operation permission)");
            }
            if (operation.getResponses() == null) operation.setResponses(new ApiResponses());
            operation.getResponses().addApiResponse("400", error("Invalid body, UUID or parameters; source configuration can also be rejected."));
            operation.getResponses().addApiResponse("401", error("UNAUTHENTICATED: a valid access token is required.")
                    .addHeaderObject("WWW-Authenticate", new Header().schema(new StringSchema()._default("Bearer"))));
            operation.getResponses().addApiResponse("403", error("ACCESS_DENIED: role, permission, Identity status or ownership check failed."));
            operation.getResponses().addApiResponse("404", error("Requested resource was not found or is not visible to this user."));
            operation.getResponses().addApiResponse("409", error("State or version conflict; reload the persisted state before deciding how to retry."));
            operation.getResponses().addApiResponse("500", error("INTERNAL_ERROR: unexpected server failure; use traceId for diagnosis."));
            operation.getResponses().addApiResponse("502", error("SOURCE_INVALID_RESPONSE, SOURCE_ACCESS_DENIED or IDENTITY_INVALID_RESPONSE: dependency response could not be trusted."));
            operation.getResponses().addApiResponse("503", error("SOURCE_UNAVAILABLE or IDENTITY_UNAVAILABLE: required dependency is temporarily unavailable."));
            describeOperation(path, method, operation);
            if (!student && method == PathItem.HttpMethod.POST) {
                String schema = CREATE_SCHEMAS.get(path);
                if (path.endsWith("/versions") || path.endsWith("/clone")) schema = "ExamVersionResponse";
                if (schema != null) {
                    operation.getResponses().remove("200");
                    operation.getResponses().addApiResponse("201", success("Created resource", schema));
                }
            }
            if (!student && method == PathItem.HttpMethod.DELETE) {
                operation.getResponses().remove("200");
                operation.getResponses().addApiResponse("204", new ApiResponse().description("Deleted resource; empty body"));
            }
            if (student && !history) operation.getResponses().values().forEach(response ->
                    response.addHeaderObject("Cache-Control", new Header().schema(new StringSchema()._default("no-store"))));
        }));
    }

    private void describeOperation(String path, PathItem.HttpMethod method, Operation operation) {
        if (path.startsWith("/api/v1/exams/") && path.endsWith("/sessions")) {
            operation.setSummary("Read the current user's attempt history for an exam");
            operation.setDescription("History is scoped to the JWT subject; no client userId is accepted. "
                    + "An unreleased OFFICIAL grade is omitted from its history summary.");
        } else if (path.equals(STUDENT)) {
            operation.setSummary("List official exams available to the current student");
            operation.setDescription("Published exams are filtered by the student's license and server time. "
                    + "PARTICIPATED also retains the student's existing attempts after an exam is hidden or archived.");
            if (operation.getParameters() != null) operation.getParameters().stream()
                    .filter(parameter -> "tab".equals(parameter.getName())).forEach(parameter ->
                            parameter.setSchema(new StringSchema()._enum(List.of("OPEN", "UPCOMING", "PARTICIPATED"))._default("OPEN")));
        } else if (path.startsWith(STUDENT)) {
            operation.setSummary("Read an official exam and the current student's participation state");
            operation.setDescription("examVersionId is optional; when supplied it must belong to examId. "
                    + "Use server-computed canStart, canResume, canRetry and availability values for UI actions.");
        } else if (path.equals(SESSION) && method == PathItem.HttpMethod.POST) {
            operation.setSummary("Start or recover the current student's official attempt");
            operation.setDescription("Supply examId or examVersionId; if both are supplied they must agree. "
                    + "HTTP 201 creates an attempt; HTTP 200 recovers the existing active attempt with resumedExisting=true. "
                    + "Server-side schedule, license, publication, retry interval and quota checks apply. "
                    + "A Content Builder failure does not consume a new attempt.");
            operation.getResponses().addApiResponse("200", success("Recovered existing attempt", "ExamSessionResponse"));
            operation.getResponses().addApiResponse("201", success("Created new attempt", "ExamSessionResponse"));
        } else if (path.startsWith(SESSION)) {
            String suffix = path.substring(SESSION.length());
            if (suffix.endsWith("/answers")) {
                operation.setSummary("Save an answer batch atomically");
                operation.setDescription("OFFICIAL saves require the latest session version and a non-empty answers list. "
                        + "Option UUIDs must belong to the frozen question. Use single_choice, multiple_choice or true_false; "
                        + "multiple choice uses comma-separated unique option UUIDs. Null or empty value clears an answer. "
                        + "All items are validated before any write. An old version returns 409 VERSION_CONFLICT.");
            } else if (suffix.endsWith("/submit")) {
                operation.setSummary("Submit once and grade the frozen attempt");
                operation.setDescription("OFFICIAL submit requires Idempotency-Key. Retry with the SAME key after a transport failure. "
                        + "The same key returns the original session version and submittedAt; a different key after submit returns a conflict. "
                        + "Grading uses saved answers and the immutable attempt snapshot. Result publication follows the saved release policy.");
                Parameter key = operation.getParameters() == null ? null : operation.getParameters().stream()
                        .filter(parameter -> "Idempotency-Key".equalsIgnoreCase(parameter.getName())).findFirst().orElse(null);
                if (key == null) {
                    key = new Parameter().name("Idempotency-Key").in("header");
                    operation.addParametersItem(key);
                }
                // The controller also serves PRACTICE. Required only for the OFFICIAL contract.
                key.setRequired(false);
                key.setDescription("Required for OFFICIAL attempts: 1-100 characters from letters, digits, dot, underscore, colon or hyphen. Reuse this exact key for retries of the same submission.");
                key.setSchema(new StringSchema().pattern("^[A-Za-z0-9._:-]{1,100}$").minLength(1).maxLength(100));
                key.addExtension("x-required-for-official", true);
            } else if (suffix.endsWith("/release-result")) {
                operation.setSummary("Release a manually held official result");
                operation.setDescription("Requires ADMIN and exams:update. Releases an already submitted result whose saved policy is MANUAL. "
                        + "Student role does not grant this operation. Kafka delivery continues from the stored outbox event.");
                operation.getResponses().remove("200");
                operation.getResponses().addApiResponse("204", new ApiResponse().description("Result released; empty body"));
            } else if (suffix.endsWith("/result")) {
                operation.setSummary("Read a released result or its pending publication state");
                operation.setDescription("HTTP 200 can mean resultPublished=false with summary/answers absent. "
                        + "IMMEDIATE publishes after submit, AFTER_EXAM_END waits for the original server deadline, "
                        + "and MANUAL waits for administrative release. Do not display an invented score while pending.");
            } else if (suffix.endsWith("/review")) {
                operation.setSummary("Review a published result under the frozen review policy");
                operation.setDescription("Before publication returns 409 RESULT_NOT_PUBLISHED. After release, answer, explanation "
                        + "and score visibility still follow the saved show/review flags; withheld fields remain absent.");
            } else if (suffix.endsWith("/paper")) {
                operation.setSummary("Read the immutable attempt paper");
                operation.setDescription("Questions, option order and selected answers are frozen for this attempt. "
                        + "No correct-answer key is returned. mediaFileIds are Media UUIDs; resolve them with the existing Media API.");
            } else if (suffix.endsWith("/navigation") || suffix.endsWith("/mark")) {
                operation.setDescription("OFFICIAL mutations require the latest version. Navigation uses one-based targetPosition; "
                        + "mark uses a boolean marked. Saved navigation/skip/review policy is enforced. Returns updated progress/version.");
            } else if (suffix.endsWith("/pause") || suffix.endsWith("/resume")) {
                operation.setDescription("The saved pause/resume policy controls count, timeout and remaining time. "
                        + "Resume retains the same attempt and does not reset the timer.");
            } else if (suffix.endsWith("/violations")) {
                operation.setDescription("Supply a UUID eventId and eventType TAB_SWITCH or FULLSCREEN_EXIT. "
                        + "The backend deduplicates the event and applies the frozen policy using server time.");
            } else {
                operation.setDescription("Read the current owner's persisted official session state, version and server timer. "
                        + "Server expiry processing can close an attempt without another browser request.");
            }
        } else {
            operation.setDescription("Administrative exam/configuration operation. ADMIN role and the mapped Identity exams permission "
                    + "are enforced by examAdminAccess. OFFICIAL configuration uses the published Content Builder bank and licensed policy. "
                    + "Publication and archive transitions retain existing attempts and their frozen snapshots.");
        }
    }

    private ApiResponse error(String description) {
        return new ApiResponse().description(description)
                .content(new Content().addMediaType("application/json", new MediaType()
                        .schema(new Schema<>().$ref("#/components/schemas/Screen12Error"))))
                .addHeaderObject("Cache-Control", new Header().schema(new StringSchema()._default("no-store")))
                .addHeaderObject("X-Request-ID", new Header().schema(new StringSchema()));
    }

    private ApiResponse success(String description, String schemaName) {
        return new ApiResponse().description(description).content(new Content().addMediaType("application/json",
                new MediaType().schema(new Schema<>().$ref("#/components/schemas/" + schemaName))));
    }
}
