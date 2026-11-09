package dts.com.examination.config.security;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component("examAdminAccess")
public class ExamAdminAccess {

    private static final Set<String> RESOURCES = Set.of(
            "exam", "exam_version", "exam_structure", "exam_criteria", "exam_rule", "exam_session");
    private static final Set<String> ACTIONS = Set.of(
            "read", "create", "update", "delete", "submit", "approve", "publish", "archive", "clone", "release_result");
    private static final Set<String> VERSION_ACTIONS = Set.of("submit", "approve", "publish", "archive", "clone");

    public boolean can(Authentication authentication, String resource, String action) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getAuthorities() == null
                || resource == null || action == null
                || !RESOURCES.contains(resource) || !ACTIONS.contains(action)) {
            return false;
        }
        if ((VERSION_ACTIONS.contains(action) && !"exam_version".equals(resource))
                || ("release_result".equals(action) && !"exam_session".equals(resource))
                || ("exam_session".equals(resource)
                    && !Set.of("read", "release_result").contains(action))) {
            return false;
        }

        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        // BE01 grants can be assigned to scoped content reviewers who have portal entry
        // but are not legacy ROLE_ADMIN. All pre-existing endpoints retain ROLE_ADMIN.
        boolean reviewAction = "exam_version".equals(resource)
                && ("submit".equals(action) || "approve".equals(action));
        if (!authorities.contains("ROLE_ADMIN")
                && !(reviewAction && authorities.contains("PERM_admin-portal:enter"))) {
            return false;
        }

        // Monitoring other users' sessions requires a subject-level grant.
        // Exact grants are seeded by the additive Identity V16 migration.
        if ("exam_session".equals(resource)) {
            // Viewing someone else's attempt and releasing a result are independent grants.
            // Legacy PERM_exams:update must never authorize either operation.
            return "read".equals(action)
                    ? authorities.contains("PERM_exam_session:read")
                    : authorities.contains("PERM_official-exams:release-result");
        }
        // The review/publish grants are independent. No legacy exams:update fallback.
        if ("exam_version".equals(resource) && ("submit".equals(action) || "approve".equals(action))) {
            return authorities.contains("PERM_official-exams:" + action);
        }
        // Publishing a version requires the subject-specific capability established by ADM01.
        // Actual snapshot approval is verified separately in the publish transaction.
        if ("exam_version".equals(resource) && "publish".equals(action)) {
            return authorities.contains("PERM_official-exams:publish");
        }

        // Identity exposes permissions for the examination module as "exams:*".
        // Preserve an explicitly granted legacy permission for the same operation.
        // Role ADMIN alone never grants an operation.
        String moduleAction = switch (action) {
            case "clone" -> "create";
            case "publish", "archive" -> "update";
            default -> action;
        };
        return authorities.contains("PERM_exams:" + moduleAction)
                || authorities.contains("PERM_" + resource + ":" + action);
    }
}