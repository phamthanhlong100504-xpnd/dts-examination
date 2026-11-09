package dts.com.examination.application.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dts.com.examination.application.exception.ExaminationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Read-only administration surface. Never loads answers or exposes grading/score fields. */
@Service
@RequiredArgsConstructor
public class AdminOfficialSessions {
    private static final Set<String> STATES = Set.of("READY", "IN_PROGRESS", "PAUSED", "SUBMITTED", "EXPIRED", "TERMINATED");
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    public record Filter(UUID examId, UUID versionId, UUID userId, String licenseClass, String status,
                         Instant startedFrom, Instant startedUntil, int page, int size) { }
    public record SessionRow(UUID sessionId, UUID examId, UUID examVersionId, UUID userId,
                             String licenseClass, Integer attemptNo, String status, Instant startedAt,
                             Instant completedAt, String gradingStatus, String resultReleaseStatus,
                             String terminationReason) { }
    public record SessionDetail(SessionRow session, String resultReleaseMode, String deliveryStatus,
                                Instant resultReleasedAt, UUID releasedBy, boolean canRelease, String reasonCode) { }
    public record SessionPage(List<SessionRow> content, int page, int size, long totalElements) { }
    public record ReleaseStatus(UUID sessionId, String resultReleaseMode, String gradingStatus,
                                String resultReleaseStatus, boolean canRelease, String reasonCode,
                                Instant releasedAt, UUID releasedBy, String deliveryStatus) { }

    private static final String BASE = " FROM exam_sessions s JOIN exam_versions v ON v.id=s.exam_version_id "
            + "JOIN exams e ON e.id=v.exam_id WHERE s.deleted_at IS NULL AND v.deleted_at IS NULL "
            + "AND e.deleted_at IS NULL AND v.exam_type='OFFICIAL' "
           ;
    private static final String SELECT = "SELECT s.id AS session_id,v.exam_id,v.id AS exam_version_id,"
            + "s.user_id,s.attempt_no,s.status,s.started_at,s.submitted_at,s.metadata::text AS session_metadata,"
            + "v.configs->>'licenseClass' AS license_class";

    @Transactional(readOnly = true)
    public SessionPage list(Filter filter) {
        if (filter == null || filter.page < 0 || filter.size < 1 || filter.size > 50
                || (filter.startedFrom != null && filter.startedUntil != null
                && filter.startedFrom.isAfter(filter.startedUntil))) {
            throw bad("ADMIN_SESSION_FILTER_INVALID", "Invalid pagination or date filter");
        }
        var params = new MapSqlParameterSource();
        var where = new StringBuilder(BASE);
        if (filter.examId != null) { where.append(" AND v.exam_id=:examId"); params.addValue("examId", filter.examId); }
        if (filter.versionId != null) { where.append(" AND v.id=:versionId"); params.addValue("versionId", filter.versionId); }
        if (filter.userId != null) { where.append(" AND s.user_id=:userId"); params.addValue("userId", filter.userId); }
        if (filter.licenseClass != null && !filter.licenseClass.isBlank()) {
            String license = filter.licenseClass.trim().toUpperCase(Locale.ROOT);
            if (!license.matches("[A-Z][A-Z0-9]{0,5}")) throw bad("ADMIN_SESSION_FILTER_INVALID", "Invalid license class");
            where.append(" AND v.configs->>'licenseClass'=:license"); params.addValue("license", license);
        }
        if (filter.status != null && !filter.status.isBlank()) {
            String state = filter.status.trim().toUpperCase(Locale.ROOT);
            if (!STATES.contains(state)) throw bad("ADMIN_SESSION_FILTER_INVALID", "Invalid session status");
            where.append(" AND s.status=:status"); params.addValue("status", state);
        }
        if (filter.startedFrom != null) { where.append(" AND s.started_at>=:fromTime"); params.addValue("fromTime", filter.startedFrom); }
        if (filter.startedUntil != null) { where.append(" AND s.started_at<=:untilTime"); params.addValue("untilTime", filter.startedUntil); }
        Long total = jdbc.queryForObject("SELECT count(*)" + where, params, Long.class);
        params.addValue("limit", filter.size);
        params.addValue("offset", (long) filter.page * filter.size);
        List<SessionRow> rows = jdbc.query(SELECT + where + " ORDER BY s.started_at DESC,s.id DESC LIMIT :limit OFFSET :offset",
                params, (rs, n) -> row(rs));
        return new SessionPage(rows, filter.page, filter.size, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    public SessionDetail detail(UUID sessionId) {
        if (sessionId == null) throw bad("ADMIN_SESSION_ID_INVALID", "Session ID is required");
        var matches = jdbc.query(SELECT + BASE + " AND s.id=:sessionId",
                new MapSqlParameterSource("sessionId", sessionId), (rs, n) -> detailOf(rs));
        if (matches.size() != 1) throw new ExaminationException(HttpStatus.NOT_FOUND, "SESSION_NOT_FOUND", "Official session not found");
        return matches.get(0);
    }

    @Transactional(readOnly = true)
    public ReleaseStatus releaseStatus(UUID sessionId) {
        SessionDetail detail = detail(sessionId);
        return new ReleaseStatus(sessionId, detail.resultReleaseMode(), detail.session().gradingStatus(),
                detail.session().resultReleaseStatus(), detail.canRelease(), detail.reasonCode(),
                detail.resultReleasedAt(), detail.releasedBy(), detail.deliveryStatus());
    }

    private SessionRow row(ResultSet rs) throws SQLException {
        JsonNode metadata = parse(rs.getString("session_metadata"));
        String mode = releaseMode(metadata);
        String status = rs.getString("status");
        Instant completed = timestamp(rs, "submitted_at");
        boolean frozen = metadata.path("officialSessionVersion").asInt(0) == 1;
        return new SessionRow(rs.getObject("session_id", UUID.class), rs.getObject("exam_id", UUID.class),
                rs.getObject("exam_version_id", UUID.class), rs.getObject("user_id", UUID.class),
                rs.getString("license_class"), rs.getObject("attempt_no", Integer.class),
                status, timestamp(rs, "started_at"), completed, frozen ? grading(status, metadata) : "UNKNOWN",
                frozen ? release(status, metadata, mode, clock.instant()) : "UNKNOWN", text(metadata, "submissionReason"));
    }

    private SessionDetail detailOf(ResultSet rs) throws SQLException {
        JsonNode metadata = parse(rs.getString("session_metadata"));
        String mode = releaseMode(metadata);
        SessionRow row = row(rs);
        boolean allowed = "SUBMITTED".equals(row.status()) && "GRADED".equals(row.gradingStatus())
                && "MANUAL".equals(mode) && metadata.path("resultReleasedAt").isMissingNode();
        String reason = allowed ? "READY" : "UNKNOWN".equals(row.gradingStatus()) ? "LEGACY_SESSION_NO_FROZEN_POLICY"
                : !"SUBMITTED".equals(row.status()) ? "SESSION_NOT_SUBMITTED"
                : !"GRADED".equals(row.gradingStatus()) ? "GRADING_INCOMPLETE"
                : !"MANUAL".equals(mode) ? "RELEASE_MODE_NOT_MANUAL" : "ALREADY_RELEASED";
        return new SessionDetail(row, mode, text(metadata, "outboxState"),
                instant(text(metadata, "resultReleasedAt")), uuid(text(metadata, "resultReleasedBy")), allowed, reason);
    }

    static String grading(String status, JsonNode metadata) {
        return "SUBMITTED".equals(status) && metadata.path("gradingSnapshot").isObject()
                && metadata.path("gradingSnapshot").hasNonNull("score")
                && metadata.path("gradingSnapshot").hasNonNull("maxScore")
                && metadata.path("gradingSnapshot").hasNonNull("questions") ? "GRADED" : "PENDING";
    }
    static String release(String status, JsonNode metadata, String mode, Instant now) {
        if (!"SUBMITTED".equals(status) || !"GRADED".equals(grading(status, metadata))) return "NOT_AVAILABLE";
        return switch (mode) {
            case "IMMEDIATE", "AFTER_SUBMIT" -> "RELEASED";
            case "MANUAL" -> metadata.hasNonNull("resultReleasedAt") ? "RELEASED" : "HELD";
            case "AFTER_EXAM_END" -> {
                Instant end = instant(metadata.path("officialPaper").path("examEndedAt").asText(null));
                yield end == null ? "UNKNOWN" : now.isBefore(end) ? "SCHEDULED" : "RELEASED";
            }
            default -> "UNKNOWN";
        };
    }
    private static String releaseMode(JsonNode metadata) {
        String mode = metadata.path("officialPaper").path("configuration").path("rule")
                .path("resultReleaseMode").asText("");
        return Set.of("IMMEDIATE", "AFTER_SUBMIT", "AFTER_EXAM_END", "MANUAL").contains(mode) ? mode : "UNKNOWN";
    }
    private JsonNode parse(String json) {
        try { return mapper.readTree(json == null ? "{}" : json); }
        catch (Exception e) { throw new ExaminationException(HttpStatus.CONFLICT, "SESSION_SNAPSHOT_INVALID", "Stored official session metadata is invalid"); }
    }
    private static String text(JsonNode json, String key) { return json.hasNonNull(key) ? json.get(key).asText() : null; }
    private static Instant instant(String raw) { try { return raw == null ? null : Instant.parse(raw); } catch (Exception e) { return null; } }
    private static UUID uuid(String raw) { try { return raw == null ? null : UUID.fromString(raw); } catch (Exception e) { return null; } }
    private static Instant timestamp(ResultSet rs, String name) throws SQLException {
        java.sql.Timestamp time = rs.getTimestamp(name); return time == null ? null : time.toInstant();
    }
    private ExaminationException bad(String code, String reason) { return new ExaminationException(HttpStatus.BAD_REQUEST, code, reason); }
}
