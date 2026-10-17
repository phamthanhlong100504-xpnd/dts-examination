package dts.com.examination.application.service;

import dts.com.examination.application.client.*;
import dts.com.examination.application.exception.ExaminationException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CurrentStudentProfileReader {
    private static final Set<String> LICENSES = Set.of("A1", "A", "B1", "B", "C1", "C", "D1", "D2", "D", "BE", "C1E", "CE", "D1E", "D2E", "DE");
    private final IdentityProfileClient identity;

    public String selectedLicenseClass(UUID authenticatedUserId) {
        if (authenticatedUserId == null || !(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes context)) {
            throw new InsufficientAuthenticationException("An authenticated request is required");
        }
        String authorization = context.getRequest().getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.substring(7).isBlank()
                || authorization.indexOf('\r') >= 0 || authorization.indexOf('\n') >= 0) {
            throw new InsufficientAuthenticationException("A valid access token is required");
        }
        IdentityMeResponse response;
        try { response = identity.getCurrentUser(authorization); }
        catch (RuntimeException failure) { throw IdentityProfileClientFallback.failure(failure); }
        if (response == null || !Boolean.TRUE.equals(response.success()) || response.data() == null
                || response.data().id() == null || response.data().status() == null) {
            throw new ExaminationException(HttpStatus.BAD_GATEWAY, "IDENTITY_INVALID_RESPONSE", "Identity profile is incomplete");
        }
        var profile = response.data();
        if (!authenticatedUserId.equals(profile.id()) || !"ACTIVE".equals(profile.status())) {
            throw new ExaminationException(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "This account cannot participate in examinations");
        }
        if (profile.selectedLicenseClass() == null || profile.selectedLicenseClass().isBlank()) {
            throw new ExaminationException(HttpStatus.CONFLICT, "LICENSE_CLASS_REQUIRED", "Select a license class in Identity before browsing examinations");
        }
        String license = profile.selectedLicenseClass().trim().toUpperCase(Locale.ROOT);
        if (!LICENSES.contains(license)) {
            throw new ExaminationException(HttpStatus.BAD_GATEWAY, "IDENTITY_INVALID_RESPONSE", "Identity returned an unsupported license class");
        }
        return license;
    }
}
