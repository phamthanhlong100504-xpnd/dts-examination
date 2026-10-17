# DTS Examination — canonical schema V001–V004

## Cơ chế

Database examination, history dts_examination_schema_history. Không dùng Flyway. docs/db/apply-schema.psql và tools/screen12-schema.ps1 hoặc screen12_schema.py là entrypoint hiện có. manifest Revision5 kiểm SHA256 các file nguồn/schema/runner và mapping7 entity. JPA table criteria là **exam_criterias**, không nhầm docs file exam_criteria.sql là tên bảng.

| Version | File | Phạm vi |
| --- | --- | --- |
| V001 | V001__baseline_entity_schema.sql | Trigger/functions, bảng/cột/constraint/index theo entity, kiểm tương thích database đã có |
| V002 | V002__screen12_contract_integrity.sql | Integrity hợp đồng cấu trúc/version/session, FK/ràng buộc, index worker và bảo vệ dữ liệu |
| V003 | V003__structure_draft_default.sql | Default trạng thái structure DRAFT và verify nâng cấp |
| V004 | V004__screen13_result_history_indexes.sql | Index hỗ trợ history backfill/outbox official và legacy |

Các DDL docs/db/exams.sql,... là tài liệu/nguồn tương ứng được quản lý checksum, không hướng dẫn thành viên chạy riêng tùy thứ tự. SQL tests là fixture cho runner TestFresh, không phải dữ liệu business cần seed vào database đang dùng. schema-manifest.json là source chức năng cần commit cùng migration, không phải báo cáo tạm.

## Apply và bảo toàn

Runner kiểm manifest trước, database target phải đúng expected_database. apply-schema dùng một transaction, advisory lock và history/checksum; version đã chạy được đối chiếu/bỏ qua, rồi verify schema. Lỗi phải rollback, không tự ghi history bỏ qua hoặc xóa constraint để chạy được.

Database trống tạo đầy đủ schema. Database JPA cũ được kiểm shape/type/constraints và dữ liệu trước migration, gồm exam_criterias có row liên kết version. Không mặc định mọi database có bảng bất kỳ đều nâng cấp được; trường hợp không tương thích dừng để rà dữ liệu, giữ backup.

docs/db/SCREEN12_SCHEMA.md và29 file trong manifest đang có hash cố định. Không chỉnh chính tả/line ending các file đó trong đợt docs; nội dung bổ sung đặt riêng tại đây. .gitattributes giữ docs/db và runner LF, hai fixture JSON source thật đặt -text để giữ CRLF gốc. Không chạy git add --renormalize toàn repo.

## Máy mới và thay đổi tiếp

[LOCAL_SETUP](../LOCAL_SETUP.md) có lệnh Apply đúng container/database hoặc Python/psql. Sau Apply dùng Hibernate ddl-auto=validate để kiểm mapping, không dùng update thay migration. Boot JAR/Docker không tự chạy các docs/db migration. Không dùng Flyway repair/baseline vì repo này không có Flyway history.

Schema mới về sau phải có version migration mới, kiểm nâng cấp/repeat và cập nhật runner/manifest theo quy trình, không sửa V001–V004 đã chạy. Bước chuẩn bị commit chỉ bảo toàn source, không thực thi DB/restore/migration/backfill.
