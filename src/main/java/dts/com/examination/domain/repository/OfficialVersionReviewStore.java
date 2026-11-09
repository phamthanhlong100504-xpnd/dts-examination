package dts.com.examination.domain.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OfficialVersionReviewStore {
    private final JdbcTemplate jdbc;
    public OfficialVersionReviewStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Review(UUID id, UUID versionId, String status, String fingerprint,
                         UUID submittedBy, Instant submittedAt, UUID decidedBy, Instant decidedAt,
                         String reason) { }

    private static Review row(ResultSet result, int i) throws SQLException {
        var submitted = result.getTimestamp("submitted_at");
        var decided = result.getTimestamp("decided_at");
        return new Review(result.getObject("id", UUID.class), result.getObject("exam_version_id", UUID.class),
                result.getString("status"), result.getString("fingerprint"),
                result.getObject("submitted_by", UUID.class), submitted == null ? null : submitted.toInstant(),
                result.getObject("decided_by", UUID.class), decided == null ? null : decided.toInstant(),
                result.getString("reason"));
    }

    public Optional<Review> latest(UUID versionId) {
        return jdbc.query("SELECT * FROM public.admin_official_version_reviews WHERE exam_version_id=? "
                + "ORDER BY submitted_at DESC, id DESC LIMIT 1", OfficialVersionReviewStore::row, versionId)
                .stream().findFirst();
    }

    public boolean wasEditor(UUID versionId, UUID actor) {
        Boolean found = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM public.admin_official_version_editor_events "
                + "WHERE exam_version_id=? AND actor_id=?)", Boolean.class, versionId, actor);
        return Boolean.TRUE.equals(found);
    }

    public void recordEditor(UUID versionId, UUID actor) {
        jdbc.update("INSERT INTO public.admin_official_version_editor_events "
                + "(id,exam_version_id,actor_id) VALUES (?,?,?)", UUID.randomUUID(), versionId, actor);
    }

    public void submit(UUID reviewId, UUID versionId, String fingerprint, UUID actor) {
        jdbc.update("INSERT INTO public.admin_official_version_reviews "
                + "(id,exam_version_id,status,fingerprint,submitted_by) VALUES (?,?,'IN_REVIEW',?,?)",
                reviewId, versionId, fingerprint, actor);
    }

    public void decide(UUID reviewId, String status, UUID actor, String reason) {
        int count = jdbc.update("UPDATE public.admin_official_version_reviews "
                + "SET status=?, decided_by=?, decided_at=clock_timestamp(), reason=? "
                + "WHERE id=? AND status='IN_REVIEW'", status, actor, reason, reviewId);
        if (count != 1) throw new IllegalStateException("Review state changed concurrently");
    }

    public void journal(UUID reviewId, UUID versionId, String action, UUID actor, String fingerprint, String reason) {
        jdbc.update("INSERT INTO public.admin_official_review_audit "
                + "(review_id,exam_version_id,action,actor_id,fingerprint,reason) VALUES (?,?,?,?,?,?)",
                reviewId, versionId, action, actor, fingerprint, reason);
    }
}
