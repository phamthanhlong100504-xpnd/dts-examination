# Screen 12 — schema dùng chung trong repository

Các SQL/migration này là một phần của source, cần commit cùng backend. Mọi
thành viên và quy trình triển khai VPS áp dụng cùng phiên bản. Không cần dùng
container ID, IP hoặc mật khẩu của máy người viết script.

## Phạm vi

- Examination: 7 bảng tương ứng entity hiện hành, gồm cấu trúc, tiêu chí,
  quy chế, kỳ thi, phiên bản, phiên thi và đáp án.
- Result: `learning_results`, `learning_summaries`, các index chống trùng
  source/attempt/summary và trigger cập nhật thời gian.
- Không tạo ngân hàng câu hỏi hoặc bảng người dùng trong hai database này.
- Không sửa `application.*`, Compose, dependency Java, URL, port, JWT, Kafka,
  hoặc kiến trúc. Cấu hình kết nối vẫn dùng cấu hình chung hiện tại.

## Quy tắc thay đổi CSDL cho nhóm

Schema chuẩn phải được dựng từ migration đã commit trong repository. DB
local dùng để kiểm tra chính các migration đó cùng code backend, rồi cùng
bộ migration được áp dụng với cấu hình kết nối hiện có trên môi trường chung.
Mỗi thay đổi schema cần SQL/version, manifest và kiểm tra tương ứng trong
repo trước khi chạy trên local; sửa đã chạy thành công phải được commit
cùng code. Không coi SQL chỉ nằm trong handoff hoặc thao tác riêng trên DB
local là phần triển khai dùng chung đã hoàn thành.

Fixture chỉ chạy trên DB tạm do runner tạo. Seed/reference data cần thiết
để dựng môi trường phải có script trong repo của service sở hữu dữ liệu;
dữ liệu nghiệp vụ do người dùng tạo vẫn đi qua API. Mỗi checkout/triển khai
chạy migration trước khi khởi động service; sau migration kiểm tra lại
runtime của các chức năng đã có. Giữ cấu hình và kiến trúc hiện tại.

## Các file chính

| File | Vai trò |
|---|---|
| `docs/db/apply-schema.psql` | Điểm vào chuẩn cho DB mới và DB hiện có |
| `docs/db/migrations/V001__baseline_entity_schema.sql` | Tạo bảng/cột theo entity, kiểm tra kiểu, nullable, default và khóa chính |
| `docs/db/migrations/V002__screen12_contract_integrity.sql` | Constraint, index, quan hệ nội bộ service và trigger tương thích Screen 12 |
| `docs/db/migrations/V003__structure_draft_default.sql` | Khôi phục default DRAFT khi nâng cấp schema Examination cũ; giữ nguyên bản ghi ACTIVE và lịch sử V001/V002 |
| `docs/db/verify-schema.sql` | Kiểm tra schema sau áp dụng hoặc khi kiểm tra riêng |
| `docs/db/schema-manifest.json` | Hash của file SQL và runner được sử dụng |
| `docs/db/tests/*.sql` | Ca kiểm tra schema, upgrade và rollback trên DB tạm |
| `tools/screen12-schema.ps1` | Runner Docker local cho Windows; TestFresh/Apply/Verify |
| `tools/screen12_schema.py` | Runner dùng psql với cấu hình PG* hiện có, chạy trên Windows/Linux/VPS |

Các file `docs/db/<table>.sql` đã được đồng bộ để tham khảo DDL cho DB mới.
Để áp dụng thực tế hãy dùng điểm vào versioned `apply-schema.psql`, không
chạy lặp từng file CREATE TABLE trên database đang có dữ liệu.

## Những chỗ đã đồng bộ với code

- Tên bảng theo `@Table` của entity: `ExamCriteria` dùng `exam_criterias`.
  Cột tham chiếu vẫn là `exam_versions.exam_criteria_id`. File tham khảo
  `exam_criteria.sql` giữ tên file cũ, nhưng DDL bên trong dùng bảng JPA đúng.
- `BANK_VERSION` là nguồn hợp lệ cho phiên bản OFFICIAL.
- `exam_structures` mặc định DRAFT, hỗ trợ trạng thái cấu trúc hiện hành.
- `selected_answer` cho phép null khi chưa trả lời hoặc xóa lựa chọn.
- Idempotency-Key có phạm vi trong từng phiên thi; bỏ index unique toàn
  database cũ, giữ khóa session và kiểm soát submit trong code.
- Audit `created_by` theo nullable của entity; cấu trúc vẫn yêu cầu actor.
- Snapshot, grading và outbox của Examination vẫn ở JSONB metadata hiện có.
- Result có đủ cả hai bảng và unique index để xử lý event trùng.
- Trigger dùng helper riêng của từng service, giữ nguyên helper generic
  có thể đang được bảng khác sử dụng.

## Cách quản lý migration

`apply-schema.psql` chạy trong một transaction, kiểm tra đúng tên database,
khóa advisory theo service, áp dụng V001/V002 rồi V003 của Examination và
kiểm tra lại schema, gồm default DRAFT. Result vẫn dùng V001/V002.
Lịch sử nằm trong `dts_examination_schema_history` hoặc
`dts_result_schema_history`. Phiên bản đã chạy được bỏ qua; checksum khác
với lịch sử bị từ chối. Runner kiểm tra hash các file trước khi gọi psql.
Khi checkout có Java source, runner còn đối chiếu `@Table` của bảy entity
với manifest trước khi chạy SQL. Khi triển khai riêng SQL trên VPS, mapping
đã kiểm tra được lưu trong manifest, không bắt buộc mang Java source vào DB.

Revision 3 sửa baseline chưa được commit vào DB service của cuộc bàn giao:
V001/V002 dùng đúng `exam_criterias`, V003 giữ nguyên. Báo cáo chỉ đọc đã
xác nhận chưa có bảng lịch sử migration trên DB đang dùng sau lượt Apply
thất bại. Đây là sửa baseline trước lần áp dụng thực tế đầu tiên, không ghi
đè lịch sử. Nếu DB khác đã commit checksum revision cũ, runner sẽ từ chối;
không sửa hoặc xóa lịch sử để ép áp dụng bản mới.

Sau khi đã áp dụng, không sửa nội dung migration hoặc ghi đè checksum. Thay
đổi sau này phải có migration phiên bản tiếp theo và cập nhật điểm vào,
manifest, kiểm tra phù hợp. Không xóa lịch sử migration để chạy lại trên DB
thật. Các thao tác giả lập lịch sử trong tests chỉ chạy ở DB tạm riêng.

Migration không DELETE/UPDATE dữ liệu nghiệp vụ hiện có và không dựng lại
snapshot từ nguồn câu hỏi mới. Bổ sung cột mới có default được khai báo
theo entity; các giá trị đã có được giữ. Varchar ngắn hơn yêu cầu được mở
rộng, không thu nhỏ. Kiểu dữ liệu khác không tự chuyển đổi.

Nếu có dữ liệu cũ vi phạm constraint/unique/FK, transaction thất bại và
rollback; cần đánh giá dữ liệu đó trước khi tạo migration xử lý tiếp, không
xóa bản ghi để ép script PASS. Default JSONB rỗng không thay thế được giấy
thi bất biến của một phiên cũ; code vẫn từ chối snapshot thiếu/không hợp lệ.

## Kiểm tra trên database trống — Windows/Docker local

Chạy từ repo với container PostgreSQL đang dùng cho service. Không cần dừng
service để chạy TestFresh vì runner tạo DB tạm riêng. Role cần CREATEDB ở
container local để kiểm tra. Mặc định role postgres, có thể truyền DatabaseUser.

```powershell
$repo='D:\DTS\dts-examination' # đổi sang dts-result cho Result
$run=[scriptblock]::Create([IO.File]::ReadAllText((Join-Path $repo 'tools\screen12-schema.ps1')))
& $run -RepositoryRoot $repo -DatabaseContainer 'TEN_HOAC_ID_CONTAINER_POSTGRES' -Mode TestFresh
```

Runner kiểm tra: từ chối sai target DB, dựng schema mới, các hành vi quan
trọng của schema, chạy lại version, upgrade schema cũ, bảo toàn hash bản
ghi, từ chối dữ liệu không tương thích và rollback, từ chối checksum đã sửa.
Negative test phải trả đúng SQLState dự kiến, không coi mọi lỗi CLI là PASS.
Ca JPA tạo sẵn tiêu chí trong `exam_criterias` và phiên bản tham chiếu đến nó
khi chưa có lịch sử migration, rồi áp dụng baseline và kiểm tra hash toàn bộ
năm bản ghi liên quan. Tên bảng của cả chín entity được kiểm tra khi tác giả
đóng gói; số cột/nullable không thay thế việc kiểm tra `@Table`.
Examination còn kiểm tra nâng cấp DB đã có lịch sử V001/V002 nhưng default
ACTIVE: V003 phải đổi default thành DRAFT, giữ nguyên toàn bộ bản ghi ACTIVE
và lịch sử migration đã chạy. Fixture và smoke test không bị nới điều kiện.
DB tạm do lượt chạy tạo được xóa cuối lượt. Nếu cửa sổ bị đóng đột ngột,
DB tạm có thể còn lại; báo cáo/tên DB có prefix `dts12_schema_test_` để xử lý
đúng DB đó. Runner không xóa database thật hoặc database có sẵn.

Kết quả JSON ở `build/screen12-schema-.../report.json`, chứa metadata kiểm
tra, không chứa password, token, đáp án hoặc snapshot dữ liệu thật. Fixture
SQL luôn kiểm tra tên DB tạm trước khi sửa dữ liệu/lịch sử test.

## Áp dụng cho database service đang có — Windows local

Sau khi TestFresh PASS, dừng service Examination/Result đang dùng DB cần
áp dụng để tránh lock và request trong lúc đổi schema. Giữ cửa sổ service
để chạy lại với cấu hình hiện tại. Chạy Apply, ví dụ:

```powershell
& $run -RepositoryRoot $repo -DatabaseContainer 'TEN_HOAC_ID_CONTAINER_POSTGRES' -Mode Apply
& $run -RepositoryRoot $repo -DatabaseContainer 'TEN_HOAC_ID_CONTAINER_POSTGRES' -Mode Verify
```

Database mặc định lấy từ manifest: `examination` hoặc `dts_result_db`.
Truyền `-Database` nếu cấu hình chung của môi trường thực sự dùng tên khác.
Không chọn database của service khác. Script dùng Docker context local được
pin cho toàn lượt; từ chối context remote. Đây là SQL áp dụng có chủ đích,
không chạy SQL hoặc thay cấu hình tự động khi chỉ copy bản vá source.

## Nhóm và VPS — dùng psql/config kết nối hiện có

Đây là bước migration trước khi khởi động bản backend mới. Repo Result
vẫn giữ ddl-auto:none; repo Examination giữ ddl-auto:update. Không thêm
Flyway vào hai service hoặc thay cơ chế startup ngoài yêu cầu giữ cấu hình.
File SQL tồn tại trong Git không có nghĩa PostgreSQL tự thực thi nó.

Với Python 3 và psql đã có:

```bash
# Trong repo Examination, dùng PGHOST/PGPORT/PGUSER/PGPASSFILE hoặc auth đã có.
python3 tools/screen12_schema.py --database examination --mode apply
python3 tools/screen12_schema.py --database examination --mode verify

# Trong repo Result.
python3 tools/screen12_schema.py --database dts_result_db --mode apply
python3 tools/screen12_schema.py --database dts_result_db --mode verify
```

Không đưa password lên tham số dòng lệnh hay commit vào repo. Các runner
không tạo IP/port/user/password mới. Trên DB hiện có, lưu backup phù hợp với
quy trình triển khai nhóm trước khi áp dụng. Role migration cần quyền
CREATE/ALTER trên schema/table đang sở hữu. Schema public và PostgreSQL
13+ được dùng như hệ thống hiện tại.

Kiểm tra file hash là bước bắt buộc; dùng runner Python/PowerShell ở trên.
Nếu trực tiếp dùng psql trong pipeline đã kiểm tra hash:

```bash
psql -X -v ON_ERROR_STOP=1 -v expected_database=examination -d examination -f docs/db/apply-schema.psql
```

Không chạy SQL test fixture hoặc sửa lịch sử test trên VPS. Gói này chưa
thực thi migration hoặc thay đổi database trên VPS.

## Dữ liệu cần để dùng Screen 12

Schema và dữ liệu nghiệp vụ là hai phần khác nhau. Theo kiến trúc hiện có:

- Identity áp dụng migration của chính nó, gồm
  `src/main/resources/db/migration/V10__add_official_exam_update_permission.sql`.
  Tài khoản và quyền phải có trong hệ thống Identity chung; STUDENT cần ACTIVE.
- Content Builder có migration, dữ liệu `data/question-banks/2026/600/`
  và tools import/publish hiện có. Bộ câu hỏi thật phải được import/publish
  ở Content Builder, không insert lại vào Examination.
- Media quản lý metadata và file ảnh trong storage hiện có. File binary
  trong storage không tự xuất hiện vì đã tạo schema Examination/Result.
- Cấu trúc, tiêu chí, quy chế, kỳ thi và phiên bản được tạo qua API ADMIN.
  Phiên thi, kết quả và outbox được backend tạo khi có thao tác nghiệp vụ.
  Không seed các phiên/kết quả test của một cá nhân vào database nhóm/VPS.

Nếu nhóm đang dùng dữ liệu/infra chung đã có, giữ nguyên nguồn đó. Nếu dùng
DB trống cho các service nguồn, thực hiện quy trình import/seed của service
nguồn riêng trước khi kiểm tra HTTP xuyên service.

## Trạng thái kiểm thử

Bản backend trước thay đổi schema đã PASS 270-case runtime và 137-case
OpenAPI/Kafka recovery/replay trên local B. Kết quả đó không thay thế việc
chạy SQL/migration mới. Môi trường tác giả đã kiểm tra hash, tham chiếu file,
đối chiếu các cột entity, cú pháp Python và cấu trúc PowerShell; chưa có
PostgreSQL/PowerShell để thực thi TestFresh. Cần gửi report TestFresh/Apply
và chạy lại bộ runtime phù hợp sau khi áp dụng schema mới trước khi chốt
khả năng dựng lại cho cả nhóm hoặc triển khai VPS.

Tham khảo PostgreSQL chính thức:
https://www.postgresql.org/docs/16/app-psql.html
https://www.postgresql.org/docs/16/sql-altertable.html
