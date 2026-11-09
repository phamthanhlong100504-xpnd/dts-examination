package dts.com.examination.api.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** BE06-09: owner-scoped, no-store operational evidence. No cross-service success is inferred. */
@RestController
public class AdminOperationalStatusController {
    private final AdminOfficialSyncController.Monitor monitor;
    public AdminOperationalStatusController(AdminOfficialSyncController.Monitor monitor) { this.monitor=monitor; }

    @GetMapping("/api/v1/admin/operations/examination-status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN') and hasAuthority('PERM_system-status:read')")
    public ResponseEntity<Map<String,Object>> status() {

        Map<String,Object> source=monitor.summary();
        var out=new LinkedHashMap<String,Object>();out.put("sourceService","dts-examination");out.put("asOf",Instant.now());
        out.put("dataStatus","PARTIAL");out.put("localOfficialHistoryStatus",source.get("dataStatus"));out.put("officialHistory",source);
        out.put("resultProjectionReadiness",null);out.put("missingSources",List.of("dts-result"));
        out.put("warnings",List.of("Only the legacy official-history outbox is covered, not every official delivery mode"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out);
    }
}
