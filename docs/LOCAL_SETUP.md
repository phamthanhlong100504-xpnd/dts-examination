# Chạy DTS Examination trên máy mới

## 1. Công cụ và nhánh

JDK21, Git, Gradle Wrapper8.8 đi kèm repo. Python3 và psql nếu dùng migration runner Python; PowerShell5.1+ và Docker nếu dùng runner container. Nhánh chức năng hiện tại feat/official-exam-workflow cho tới khi nhóm merge vào main. Main class dts.com.examination.ExaminationApplication.

```powershell
Set-Location -LiteralPath 'D:\DTS\dts-examination'
java -version
```

## 2. Phụ thuộc và địa chỉ local

| Thành phần | Địa chỉ | Vai trò |
| --- | --- | --- |
| Examination | http://localhost:8088 | Admin, student catalog, phiên thi chính thức |
| PostgreSQL | localhost:5436 / examination | 7 bảng nghiệp vụ và history migration |
| Identity | http://localhost:8081 | JWT, profile ACTIVE, selectedLicenseClass, quyền |
| Content Builder | http://localhost:8082 | Ngân hàng đã publish, metadata và nội dung câu hỏi |
| Kafka | localhost:9092 | learning-results, result outbox |
| Result | http://localhost:8086 | Nhận event, lịch sử thống nhất và detail điều hướng |
| Gateway | http://localhost:8888 | Route /api/examination/v1/** |

Dùng hạ tầng DTS hiện có. Nếu chưa có database/container, người quản lý môi trường tạo database examination và credential tương ứng; không tự tái tạo container/volume đang có. Port5436 là cổng host-local đã dùng trong dự án; PG trong Docker thường nghe5432. Compose/deploy cũ dành môi trường khác, không mặc định coi đó là sơ đồ Windows local.

## 3. Environment

Máy mới chưa có .env có thể sao chép .env.example rồi điền credential riêng. Giữ cấu hình máy đang chạy bình thường. Template không tự nạp vào Gradle hoặc Java.

```powershell
Set-Location -LiteralPath 'D:\DTS\dts-examination'
Copy-Item -LiteralPath '.env.example' -Destination '.env'
notepad .env
```

JWT_SECRET phải thống nhất với Identity để kiểm token, đồng thời dùng Base64 giải mã ra ít nhất32 byte để interceptor nội bộ Content Builder khởi tạo được. JwtProvider có fallback decoding, nhưng không dựa vào fallback đó cho kết nối bank: ContentBuilderFeignConfiguration yêu cầu Base64. Không tự tạo key khác cho Examination nếu Identity/Content Builder đang dùng chung cấu hình. Không đưa key thật vào Git.

CONTENT_BUILDER_URL và IDENTITY_SERVICE_URL là tên biến chính xác tại @FeignClient. Trỏ trực tiếp origin, không thêm /api/... vào cuối. Khi URL rỗng, Feign có thể dựa service discovery; mẫu local tắt Eureka và đặt đủ URL.

Phải override toàn bộ SPRING_DATASOURCE_URL: YAML nền dùng host và port5434 của môi trường cũ; chỉ DB_HOST=localhost sẽ giữ sai cổng cho Examination local. Credential PostgreSQL dùng giá trị thật của database; thay .env không đổi password volume cũ.

Nạp file KEY=value vào terminal mà không thực thi nó như PowerShell:

```powershell
& {
    $envPath = Join-Path (Get-Location).Path '.env'
    foreach ($line in [IO.File]::ReadAllLines($envPath)) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.TrimStart().StartsWith('#')) { continue }
        $pair = $line -split '=', 2
        if ($pair.Count -ne 2 -or $pair[0] -notmatch '^[A-Z][A-Z0-9_]*$') {
            throw 'Dòng .env không đúng KEY=value.'
        }
        [Environment]::SetEnvironmentVariable($pair[0], $pair[1], 'Process')
    }
}
```

Không bao value bằng nháy với loader trên. Có thể đặt các biến tương đương trong Run Configuration của IDE. Biến trong terminal mới không thay cấu hình Java đang chạy.

## 4. Migration phải chạy trước ứng dụng trên máy mới

Đọc [Migration catalog](db/MIGRATION_CATALOG.md), giữ backup của môi trường có dữ liệu và dùng đúng database examination. Repo không có Flyway để tự ghi V001–V004 khi boot; runner kiểm hash và mapping JPA rồi chạy SQL chuẩn.

Cách Windows/Docker, dùng đúng container đã có:

```powershell
Set-Location -LiteralPath 'D:\DTS\dts-examination'
powershell.exe -NoProfile -ExecutionPolicy Bypass `
  -File '.\tools\screen12-schema.ps1' `
  -RepositoryRoot 'D:\DTS\dts-examination' `
  -DatabaseContainer 'dts-examination-postgres' `
  -Database 'examination' -DatabaseUser 'postgres' -Mode Apply
if ($LASTEXITCODE -ne 0) { throw 'Migration chưa đạt; dừng trước khi chạy ứng dụng.' }
```

Runner dùng kết nối psql trong container qua Docker, lưu báo cáo ngoài luồng code chức năng (build/ ignored). Máy dùng tài khoản/container khác phải truyền đúng tên thực tế; không bỏ database guard. Mode Apply là thao tác ghi schema, chỉ chạy khi thiết lập/nâng cấp môi trường đã chuẩn bị.

Cách Python/psql, chọn thay cho cách Docker, không phải bước bắt buộc chạy cả hai:

```powershell
$env:PGHOST = 'localhost'
$env:PGPORT = '5436'
$env:PGUSER = 'postgres'
# Thiết lập PGPASSFILE riêng hoặc cơ chế xác thực psql của máy; không ghi password ở đây.
py -3 '.\tools\screen12_schema.py' --database examination --mode apply
if ($LASTEXITCODE -ne 0) { throw 'Migration chưa đạt.' }
py -3 '.\tools\screen12_schema.py' --database examination --mode verify
if ($LASTEXITCODE -ne 0) { throw 'Schema verify chưa đạt.' }
```

SPRING_DATASOURCE_* của Java không tự cấu hình PG* cho psql. Database có bảng JPA cũ nhưng chưa có history phải qua canonical runner và điều kiện tương thích; không tự đánh dấu version/checksum hoặc chạy lại từng DDL rời. Giữ nguyên manifest-managed files; thay đổi schema tiếp theo cần migration mới và cập nhật cơ chế manifest theo quy trình nhóm.

## 5. Khởi động

Sau khi môi trường được cấu hình và schema đã đạt:

```powershell
Set-Location -LiteralPath 'D:\DTS\dts-examination'
.\gradlew.bat bootRun
```

Giữ terminal mở. Boot có thể chạy worker expiry/backfill/outbox và ghi dữ liệu nghiệp vụ; không coi khởi động là kiểm tra chỉ đọc. Trong đợt chuẩn bị commit không cần chạy lại setup/migration/bootRun nếu web đang hoạt động.

- Swagger: http://localhost:8088/swagger-ui.html
- OpenAPI: http://localhost:8088/v3/api-docs
- Health: http://localhost:8088/actuator/health
- Gateway: cấu hình EXAMINATION_SERVICE_URL=http://localhost:8088 trước khi start Gateway.
- Đường dẫn web: /api/examination/v1/student/exams, /api/examination/v1/exam-sessions/...

Không gọi /api/v1/exam-sessions/... tại Gateway cho bài chính thức: alias đó thuộc Practice. Gateway rewrite /api/examination/v1/... thành /api/v1/... của Examination. Frontend Next proxy cần trỏ Gateway thực tế; không thay bằng cổng Media8080.

## 6. Dữ liệu và quyền để chạy đủ chức năng

Identity cấp role STUDENT cho học viên ACTIVE và đã chọn GPLX. Admin cần ROLE_ADMIN cùng permission tương ứng, ví dụ PERM_exams:update hoặc legacy resource permission. Identity V10 bổ sung quyền cập nhật official exam cho ADMIN; token đã cấp trước thay quyền có thể cần đăng nhập lại.

Content Builder phải có bank published, chapter/sourceChapterId và license mappings đúng. JSON fixture trong src/test/resources không tự seed bank. Migration Examination tạo schema, không tự tạo tài khoản, bài thi admin hoặc copy lịch sử của máy khác.

Admin tạo structure/rule/criteria, parent exam và version, publish version để student thấy bài theo cửa sổ thời gian và GPLX. Dữ liệu Result xuất hiện sau nộp, đủ điều kiện release và outbox Kafka được consumer xử lý. Không thấy kết quả chưa release là trạng thái nghiệp vụ, không phải lý do sửa SQL/client để lộ grade.

## 7. Cấu trúc và vận hành

src/main/java giữ controller/form/response, service/client/selection/session, entity/repository và security/config. src/main/resources/exam-matrices chứa policy đã versioned. docs/db và tools giữ migration. Không đưa secret, volume, build/bin/.gradle, log, crash dump vào commit.

Dockerfile hiện chỉ COPY src và các file Gradle để build JAR, không đóng gói docs/db/tools để tự migrate. Deployment phải có checkout/bộ migration tương ứng và chạy bước schema riêng trước JAR. Không kết luận docker build là đã tạo schema. Settings/workflow/compose nền được giữ nguyên; cấu hình publish/build riêng cần dùng environment nhóm.
