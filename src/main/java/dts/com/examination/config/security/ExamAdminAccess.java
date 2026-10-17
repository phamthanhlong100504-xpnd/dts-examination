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
            "exam", "exam_version", "exam_structure", "exam_criteria", "exam_rule");
    private static final Set<String> ACTIONS = Set.of(
            "read", "create", "update", "delete", "publish", "archive", "clone");
    private static final Set<String> VERSION_ACTIONS = Set.of("publish", "archive", "clone");

    public boolean can(Authentication authentication, String resource, String action) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getAuthorities() == null
                || resource == null || action == null
                || !RESOURCES.contains(resource) || !ACTIONS.contains(action)) {
            return false;
        }
        if (VERSION_ACTIONS.contains(action) && !"exam_version".equals(resource)) {
            return false;
        }

        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        if (!authorities.contains("ROLE_ADMIN")) {
            return false;
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