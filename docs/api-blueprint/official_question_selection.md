# Official exam: nguồn, matrix và frozen snapshot

Đây là contract giữa Examination và Content Builder, không phải endpoint public frontend. Frontend chỉ nhập profile/config vào version và đọc DTO đã lọc.

## Ba lời gọi nội bộ

ContentBuilderClient sử dụng CONTENT_BUILDER_URL:

| Method | Path | Tham số |
| --- | --- | --- |
| GET | /api/v1/content-builder/internal/questions/bank | bankVersion,licenseClass |
| GET | /api/v1/content-builder/internal/questions/metadata | contentId,contentType,licenseClass tùy nhánh |
| POST | /api/v1/content-builder/internal/questions/batch | body array UUID,licenseClass tùy nhánh |

Interceptor gắn X-DTS-Exam-Source-Token cho đúng ba đường, TTL45 giây, issuer dts-examination, audience dts-content-builder, purpose EXAM_SOURCE_READ; key dẫn xuất bằng HMAC context từ shared Base64 secret. Không gắn credential này toàn cục sang Identity. Logging Feign NONE, timeout connect5/read15 giây, Retryer.NEVER_RETRY tại cấu hình client. Source failure được map thành lỗi, không trả list rỗng giả thành công.

## Chọn câu và đóng băng

Policy csgt-2262-2025.json có bankVersion, khoảng validFrom/validUntilExclusive và profile từng hạng. Tổng câu, duration, số đúng tối thiểu và critical quota lấy từ policy đã versioned. Điều kiện chương ordinary/critical được validator kiểm riêng; không hard-code lại A1=25 câu19 phút trong frontend cho mọi hạng.

AUTO_MATRIX shuffle nguồn và matching các slot phù hợp, không lặp questionId, bảo đảm section/topic/critical quota. Publish chỉ kiểm khả năng tạo paper; attempt mới mới chọn câu riêng. MANUAL giữ danh sách input đúng thứ tự và lưu snapshot câu/assignment tại publish; start dùng bản đóng băng đó. Bank metadata phải đầy đủ và batch nội dung khớp contentVersion/option IDs/chapters/media/type.

PublishedExamSnapshot chứa structure/rule/criteria/policy/profile riêng của version. FrozenOfficialSession bổ sung questions/sectionAssignments/exam IDs/end time cho từng attempt. Cả hai là model server chứa thông tin riêng, không serialize trực tiếp vào API public. Session paper trả options theo thứ tự đã lưu và không có isCorrect. Đổi title/bank/rule về sau không làm lại đáp án/grade của session đã đóng băng.

Xem [admin workflow](admin_configuration_workflow.md) để tạo version, [session workflow](official_session_workflow.md) để làm bài. Nguồn: [selection](../../src/main/java/dts/com/examination/application/service/selection), [OfficialExamVersionValidator](../../src/main/java/dts/com/examination/application/service/OfficialExamVersionValidator.java).
