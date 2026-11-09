package dts.com.examination.config.security;

import dts.com.examination.api.controller.AdminOfficialAuditStreamController;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminOfficialAuditStreamTest {
    private final AdminOfficialAuditStreamController.ReviewAuditReader reader=
        mock(AdminOfficialAuditStreamController.ReviewAuditReader.class);
    private final AdminOfficialAuditStreamController api=secured(reader);

    private static AdminOfficialAuditStreamController secured(
            AdminOfficialAuditStreamController.ReviewAuditReader reader) {
        var proxy=new ProxyFactory(new AdminOfficialAuditStreamController(reader));
        proxy.setProxyTargetClass(true);
        proxy.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (AdminOfficialAuditStreamController)proxy.getProxy();
    }
    @AfterEach void clear() {SecurityContextHolder.clearContext();}

    @Test void separateAuditGrantAndAdminRoleRequired() {
        auth("ROLE_ADMIN", "PERM_reports:read", "PERM_exam_session:read");
        assertThrows(AccessDeniedException.class,() -> api.list(null,null,null,null,0,30));
        auth("ROLE_STUDENT","PERM_audit:read");
        assertThrows(AccessDeniedException.class,() -> api.list(null,null,null,null,0,30));
        auth("ROLE_ADMIN");
        assertThrows(AccessDeniedException.class,() -> api.list(null,null,null,null,0,30));
        verifyNoInteractions(reader);
    }
    @Test void adminOnlyReceivesNoStoreSafeAuditProjection() {
        auth("ROLE_ADMIN","PERM_audit:read");
        when(reader.list(null,null,null,null,0,30)).thenReturn(Map.of("dataStatus","READY"));
        var reply=api.list(null,null,null,null,0,30);
        assertEquals("no-store",reply.getHeaders().getFirst("Cache-Control"));
        assertEquals("READY",reply.getBody().get("dataStatus"));
        verify(reader).list(null,null,null,null,0,30);
        verifyNoMoreInteractions(reader);
    }
    @Test void rejectsBadPagingOrTimeBeforeDatabaseAndOnlyReadsImmutableJournal() {
        var db=mock(JdbcTemplate.class);
        var source=new AdminOfficialAuditStreamController.ReviewAuditReader(db);
        var t=Instant.parse("2026-11-01T00:00:00Z");
        assertThrows(IllegalArgumentException.class,()->source.list(t,t,null,null,0,30));
        assertThrows(IllegalArgumentException.class,()->source.list(null,null,null,null,1001,30));
        assertThrows(IllegalArgumentException.class,()->source.list(null,null,null,null,0,0));
        verifyNoInteractions(db);
        when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("auditId","x")));
        var output=source.list(null,null,null,null,0,30);
        assertEquals("READY",output.get("dataStatus"));
        assertEquals(1,((List<?>)output.get("items")).size());
        assertEquals(false,output.get("hasMore"));
        var sql=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(db).queryForList(sql.capture(),any(Object[].class));
        String query=sql.getValue().toLowerCase();
        assertTrue(query.contains("public.admin_official_review_audit"));
        for(String forbidden:List.of("select *", "fingerprint", "answer", "metadata", "payload", "password", "jwt"))
            assertFalse(query.contains(forbidden),forbidden);
        assertTrue(query.contains("limit ? offset ?"));
    }
    @Test void pagingDoesNotReturnExtraLookaheadRow() {
        var db=mock(JdbcTemplate.class);
        var source=new AdminOfficialAuditStreamController.ReviewAuditReader(db);
        when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("auditId","a"),Map.of("auditId","b")));
        var output=source.list(null,null,null,null,0,1);
        assertEquals(true,output.get("hasMore"));
        assertEquals(1,((List<?>)output.get("items")).size());
    }
    private static void auth(String... grants) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(UUID.randomUUID(),null,
                Arrays.stream(grants).map(SimpleGrantedAuthority::new).toList()));
    }
}
