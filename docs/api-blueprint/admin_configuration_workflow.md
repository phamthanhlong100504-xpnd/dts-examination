# API Blueprint: Cấu hình và vòng đời phiên bản

## Quyền / response

Bearer admin và permission theo [contract notes](contract_notes.md). Body DTO trực tiếp. Create trả201, read/update200, delete204. Publish/archive trả200, clone trả201 với ExamVersionResponse. Version list là Page<ExamVersionResponse> với pageable/status, khác PageResponse của cấu hình.

| Nhóm | Routes trực tiếp |
| --- | --- |
| Parent exam | POST/GET /api/v1/exams; GET/PATCH/DELETE /api/v1/exams/{examId}; PATCH /api/v1/exams/{examId}/status |
| Structure | POST/GET /api/v1/exam-structures; GET/PATCH/DELETE /api/v1/exam-structures/{structureId} |
| Rule | POST/GET /api/v1/exam-rules; GET/PATCH/DELETE /api/v1/exam-rules/{ruleId} |
| Criteria | POST/GET /api/v1/exam-criterias; GET/PATCH/DELETE /api/v1/exam-criterias/{criteriaId} |
| Version | POST/GET /api/v1/exams/{examId}/versions; GET/PATCH/DELETE /api/v1/exam-versions/{versionId} |
| State | POST /api/v1/exam-versions/{versionId}/publish, /archive, /clone |

Tên route criteria là exam-criterias theo controller, không sửa thành exam-criteria vì ngữ pháp. Các blueprint CRUD cũ được giữ làm tham khảo field nền; phần official profile/vòng đời hiện hành nằm ở trang này và dto_reference.

## Profile official

CreateExamVersionRequest có title, examType, thumbnailId, examStructureId, examRuleId, examCriteriaId, contentType, contentId, startedAt, endedAt, metadata và bốn field mới licenseClass/bankVersion/selectionMode/manualQuestionIds. Ví dụ phần profile để ghép với các UUID cấu hình thật:

```json
{"examType":"OFFICIAL","contentType":"BANK_VERSION","licenseClass":"A1","bankVersion":"dts-2026-600-v1","selectionMode":"AUTO_MATRIX","manualQuestionIds":[]}
```

Đây là fragment, không phải request đầy đủ có thể gửi ngay. contentId/structure/rule/criteria phải tồn tại và nguồn BANK_VERSION phù hợp. MANUAL thay selectionMode và cung cấp đủ UUID câu hợp lệ duy nhất; giới hạn DTO1000 không thay tổng câu thực của matrix.

Update merge các field non-null vào bản candidate, validate trước mới mutate row. Không truyền null để xóa tham chiếu rồi mong validator bỏ qua. Những official field trả về gồm profile, matrixPolicyId,totalQuestions,durationSeconds,passingCorrectAnswers,criticalQuestionCount; response không trả private correct-answer snapshot.

## Vòng đời và cấu hình tham chiếu

Create DRAFT, versionNo mới dưới khóa parent. Chỉ DRAFT chưa có session được update/delete. Publish kiểm source và policy hiện tại rồi archive version PUBLISHED cũ trong cùng transaction; parent HIDDEN vẫn giữ ẩn. Archive chỉ version PUBLISHED. Clone sao bản config/profile thành DRAFT với title [Clone] và số version mới; source cần hợp lệ cho bước tiếp theo.

Structure section có chapterIds và criticalQuestionCount. Quota null khác0; score là điểm mỗi câu. Official validator còn buộc tổng câu/thời lượng/topic/critical phù hợp policy. Rule/criteria/structure bị PUBLISHED/ARCHIVED tham chiếu không được sửa; delete bị chặn khi bất kỳ version history tham chiếu. Code trả STRUCTURE_IN_USE/REFERENCED, RULE_IN_USE/REFERENCED, CRITERIA_IN_USE/REFERENCED hoặc VERSION_IMMUTABLE/HAS_SESSIONS tùy thao tác.

Giữ dữ liệu lịch sử bằng snapshot; muốn thay cấu hình đang dùng thì tạo/clone bản khác, không update trực tiếp SQL của bản cũ. Tham khảo [matrix selection](official_question_selection.md) và [nghiệp vụ](../EXAMINATION_FEATURES.md).
