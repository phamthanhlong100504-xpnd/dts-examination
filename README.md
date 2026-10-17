# DTS Examination

Dịch vụ thi chính thức của DTS, Java21, Spring Boot3.3.2, Gradle Wrapper8.8. Quản trị viên tạo bài thi, cấu trúc, quy tắc, tiêu chí và phiên bản; học viên xem bài theo GPLX, bắt đầu phiên, làm bài, nộp và xem kết quả theo thời điểm công bố. PostgreSQL lưu nguồn phiên thi; outbox chuyển kết quả đủ điều kiện sang Result qua Kafka.

| Nội dung | Tài liệu |
| --- | --- |
| Chạy máy mới và biến môi trường | [LOCAL_SETUP](docs/LOCAL_SETUP.md) |
| Nghiệp vụ và trách nhiệm từng nhóm source | [EXAMINATION_FEATURES](docs/EXAMINATION_FEATURES.md) |
| API mới/thay đổi so với main | [API blueprints](docs/api-blueprint/README.md) |
| Migration V001–V004 và history | [Migration catalog](docs/db/MIGRATION_CATALOG.md) |
| Mẫu cấu hình không chứa credential thật | [.env.example](.env.example) |

Nhánh bàn giao hiện tại feat/official-exam-workflow; nền main lúc review416924c. Source chức năng/SQL đang chạy local được giữ nguyên trong bước chuẩn bị Git. Schema do file migration trong docs/db quản lý, không phải Flyway. Không dùng Hibernate update để thay thế migration khi thiết lập máy mới.

Examination chạy8088, PostgreSQL host-local5436/database examination. Web đi Gateway8888 với /api/examination/v1/**. Git cung cấp chức năng/migration; credential, user data, bài thi đã tạo và volume thuộc môi trường. Pull code không tự mang dữ liệu của máy người phát triển.
