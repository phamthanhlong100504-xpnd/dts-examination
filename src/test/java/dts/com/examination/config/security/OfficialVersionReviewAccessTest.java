package dts.com.examination.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OfficialVersionReviewAccessTest {
    private final ExamAdminAccess access = new ExamAdminAccess();
    private UsernamePasswordAuthenticationToken auth(String... grants) {
        return new UsernamePasswordAuthenticationToken(UUID.randomUUID(),null,
                Arrays.stream(grants).map(SimpleGrantedAuthority::new).toList());
    }
    @Test void reviewPermissionsAreIndependentFromLegacyExamUpdate() {
        var legacy=auth("ROLE_ADMIN","PERM_exams:update");
        assertFalse(access.can(legacy,"exam_version","submit"));
        assertFalse(access.can(legacy,"exam_version","approve"));
        assertFalse(access.can(legacy,"exam_version","publish"));
        var submit=auth("ROLE_ADMIN","PERM_official-exams:submit");
        assertTrue(access.can(submit,"exam_version","submit"));
        assertFalse(access.can(submit,"exam_version","approve"));
        assertFalse(access.can(submit,"exam_version","publish"));
        var approve=auth("ROLE_ADMIN","PERM_official-exams:approve");
        assertTrue(access.can(approve,"exam_version","approve"));
        assertFalse(access.can(approve,"exam_version","publish"));
        assertFalse(access.can(auth("PERM_official-exams:approve"),"exam_version","approve"));
        assertTrue(access.can(auth("PERM_admin-portal:enter","PERM_official-exams:approve"),
                "exam_version","approve"));
    }
}
