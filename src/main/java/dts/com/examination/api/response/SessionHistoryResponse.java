package dts.com.examination.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionHistoryResponse {
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private List<SessionHistoryItemResponse> items;
}
