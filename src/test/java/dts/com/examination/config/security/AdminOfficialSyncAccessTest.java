package dts.com.examination.config.security;

import dts.com.examination.api.controller.AdminOfficialSyncController;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Executes the production @PreAuthorize advisor without creating an extra
 * Spring ApplicationContext. The service is mocked: no JDBC, Kafka or student
 * data can be accessed by these permission tests.
 */
class AdminOfficialSyncAccessTest {
    private static final UUID SESSION = new UUID(0, 42);
    private final AdminOfficialSyncController.Monitor monitor = mock(AdminOfficialSyncController.Monitor.class);
    private final AdminOfficialSyncController api = securedController(monitor);

    private static AdminOfficialSyncController securedController(AdminOfficialSyncController.Monitor monitor) {
        ProxyFactory factory = new ProxyFactory(new AdminOfficialSyncController(monitor));
        factory.setProxyTargetClass(true);
        factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (AdminOfficialSyncController) factory.getProxy();
    }

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void monitoringNeedsSeparateReportsAndSessionReadGrants() {
        auth("ROLE_ADMIN", "PERM_integrations:read");
        assertThrows(AccessDeniedException.class, () -> api.summary());
        auth("ROLE_ADMIN", "PERM_exam_session:read");
        assertThrows(AccessDeniedException.class, () -> api.inspect(SESSION));
        auth("ROLE_STUDENT", "PERM_exam_session:read", "PERM_integrations:read");
        assertThrows(AccessDeniedException.class, () -> api.summary());
        verifyNoInteractions(monitor);
    }

    @Test void adminCanInspectOnlyNoStoreSourceMetadata() {
        auth("ROLE_ADMIN", "PERM_exam_session:read", "PERM_integrations:read");
        when(monitor.summary()).thenReturn(Map.of("sourceService", "dts-examination", "dataStatus", "READY"));
        when(monitor.inspect(SESSION)).thenReturn(Map.of("sourceService", "dts-examination", "sessionId", SESSION));
        var summary = api.summary();
        var detail = api.inspect(SESSION);
        assertEquals("no-store", summary.getHeaders().getFirst("Cache-Control"));
        assertEquals("no-store", detail.getHeaders().getFirst("Cache-Control"));
        assertEquals("dts-examination", summary.getBody().get("sourceService"));
        assertEquals(SESSION, detail.getBody().get("sessionId"));
        verify(monitor, times(1)).summary();
        verify(monitor, times(1)).inspect(SESSION);
        verifyNoMoreInteractions(monitor);
    }

    private static void auth(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                SESSION, null, Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }
}
