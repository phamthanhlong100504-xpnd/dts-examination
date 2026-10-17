# DTS Examination — API blueprints

Contract của source local bàn giao trên nhánh feat/official-exam-workflow, nền main416924c. Path trực tiếp8088; Gateway8888 dùng /api/examination/v1/**. Response DTO trực tiếp, không wrapper data chung.

| Tài liệu hiện hành | Chức năng |
| --- | --- |
| [Contract notes](contract_notes.md) | Origin, route, JWT/quyền, owner, lỗi/trace/cache |
| [Admin workflow](admin_configuration_workflow.md) | Parent/config CRUD, official profile, publish/archive/clone và reference guards |
| [Question selection](official_question_selection.md) | AUTO_MATRIX/MANUAL, bank nội bộ và frozen snapshot |
| [Student catalog](student_exam_catalog.md) | OPEN/UPCOMING/PARTICIPATED, Identity GPLX, điều kiện tham gia |
| [Session workflow](official_session_workflow.md) | Start/paper/save/version/mark/navigation/pause/violation/submit |
| [Result release](official_result_release.md) | Result/review privacy, release-result, lịch sử và title |
| [Outbox integration](result_outbox_integration.md) | Expiry/history/producer worker, retry và legacy namespace |
| [DTO reference](dto_reference.md) | Danh sách field form/response từ source |

[Local setup](../LOCAL_SETUP.md), [Nghiệp vụ](../EXAMINATION_FEATURES.md), [Migration](../db/MIGRATION_CATALOG.md).

## Blueprint nền được giữ lại

Các file sau đã có trên main và được giữ, không bị xóa khi bổ sung contract. Những ví dụ cũ chưa có GPLX/version/release hoặc permission vẫn phải áp dụng điều kiện hiện hành ở các trang trên; không dùng body/lifecycle cũ để bỏ qua validator mới.

- [01-create-exam-criteria.md](01-create-exam-criteria.md)
- [02-get-criteria-list.md](02-get-criteria-list.md)
- [03-get-criteria-detail.md](03-get-criteria-detail.md)
- [04-update-criteria.md](04-update-criteria.md)
- [05-delete-criteria.md](05-delete-criteria.md)
- [06-create-exam-rule.md](06-create-exam-rule.md)
- [07-get-rule-list.md](07-get-rule-list.md)
- [08-get-rule-detail.md](08-get-rule-detail.md)
- [09-update-exam-rule.md](09-update-exam-rule.md)
- [10-delete-exam-rule.md](10-delete-exam-rule.md)
- [11-create-exam-structure.md](11-create-exam-structure.md)
- [12-get-exam-structure-list.md](12-get-exam-structure-list.md)
- [13-get-exam-structure-detail.md](13-get-exam-structure-detail.md)
- [14-update-exam-structure.md](14-update-exam-structure.md)
- [15-delete-exam-structure.md](15-delete-exam-structure.md)
- [16-create-exam.md](16-create-exam.md)
- [17-get-exam-list.md](17-get-exam-list.md)
- [18-get-exam-detail.md](18-get-exam-detail.md)
- [19-update-exam.md](19-update-exam.md)
- [20-delete-exam.md](20-delete-exam.md)
- [21-change-exam-status.md](21-change-exam-status.md)
- [22-create-exam-version.md](22-create-exam-version.md)
- [23-get-exam-version-list.md](23-get-exam-version-list.md)
- [24-get-exam-version-detail.md](24-get-exam-version-detail.md)
- [25-update-exam-version.md](25-update-exam-version.md)
- [26-delete-exam-version.md](26-delete-exam-version.md)
- [27-publish-exam-version.md](27-publish-exam-version.md)
- [28-archive-exam-version.md](28-archive-exam-version.md)
- [29-clone-exam-version.md](29-clone-exam-version.md)
- [30-start-exam-session.md](30-start-exam-session.md)
- [31-get-session-detail.md](31-get-session-detail.md)
- [32-get-session-progress.md](32-get-session-progress.md)
- [33-get-exam-paper.md](33-get-exam-paper.md)
- [34-save-session-answers.md](34-save-session-answers.md)
- [35-pause-session.md](35-pause-session.md)
- [36-resume-session.md](36-resume-session.md)
- [37-submit-exam.md](37-submit-exam.md)
- [38-get-exam-result.md](38-get-exam-result.md)
- [39-get-session-history.md](39-get-session-history.md)
