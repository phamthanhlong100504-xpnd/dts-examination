package dts.com.examination.application.service.adminmedia;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

class OfficialExamMediaReferenceServiceTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void rejectAnonymousBeforeAnyDatabaseRead() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        assertThrows(ResponseStatusException.class,
            () -> new OfficialExamMediaReferenceService(jdbc).inspect(UUID.randomUUID()));
        verifyNoInteractions(jdbc);
    }
    @Test void rejectAdminMissingMediaGrantBeforeAnyDatabaseRead() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("admin","x",List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        assertThrows(ResponseStatusException.class,
            () -> new OfficialExamMediaReferenceService(jdbc).inspect(UUID.randomUUID()));
        verifyNoInteractions(jdbc);
    }
    @Test void rejectNullMediaIdWithoutQuery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("admin","x",List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),new SimpleGrantedAuthority("PERM_media:read"))));
        assertThrows(ResponseStatusException.class,
            () -> new OfficialExamMediaReferenceService(jdbc).inspect(null));
        verifyNoInteractions(jdbc);
    }
}
