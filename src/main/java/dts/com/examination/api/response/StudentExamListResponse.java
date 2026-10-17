package dts.com.examination.api.response;

import java.time.Instant;
import java.util.List;

public record StudentExamListResponse(String selectedLicenseClass, String tab, int page, int size,
        long totalElements, int totalPages, List<StudentExamSummaryResponse> items, Instant serverTime) {
    public StudentExamListResponse { items = List.copyOf(items); }
}
