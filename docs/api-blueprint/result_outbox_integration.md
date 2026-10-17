# Official / legacy result outbox

## Worker và dữ liệu

OfficialSessionWorker mặc định bật qua dts.examination.session-worker.enabled=true (matchIfMissing). Scheduler3 thread tách expiry khỏi Kafka timeout. expiry-delay-ms mặc định1000, outbox-delay-ms1000, history-backfill-delay-ms15000; batch expiry100,outbox50,history20. Không đổi worker flag chỉ để commit Git.

Official metadata: officialSessionVersion,officialPaper,gradingSnapshot,historyDisplayTitle,historySchemaVersion,resultEvent,outboxState,outboxAttempts,outboxNextAttemptAt,outboxDeliveredAt. Không lộ metadata này trong DTO học viên. MANUAL chờ marker, AFTER_EXAM_END chờ end. Chờ Kafka send ack10 giây, retry backoff min(300,2^min(attempts,8)) giây.

Backfill chỉ nâng event từ source grade hợp lệ, không chấm lại lịch sử bằng bank mới. Failure ghi historyBackfillState=FAILED,historyBackfillError/time bằng transaction riêng; không tự xóa marker hay sửa session khi audit không đủ. Phiên official cũ không có immutable paper cần reconciliation riêng, không bịa snapshot.

LegacyResultOutbox lưu legacyResultEvent/legacyHistoryOutboxState/Attempts/NextAttemptAt, chạy lịch default5000ms, chỉ phát SUBMITTED và legacyResultReleaseMode IMMEDIATE/IMMEDIATELY. Đây là namespace/luồng tương thích riêng, không khẳng định toàn bộ legacy đều có privacy/release behavior giống official mới.

## Ranh giới trách nhiệm

Examination xác định grade và thời điểm được công bố; Kafka chuyển event; Result tạo projection và dedup. DELIVERED chứng minh producer đã nhận ack, không tự chứng minh public projection đã xuất hiện. Frontend chờ/refresh đúng source/result API theo trạng thái, không tự publish Kafka hoặc sửa outbox.

Pull repo không chuyển pending event/data của máy khác. Migrations tạo index hỗ trợ worker; payload Git không chạy outbox/replay/backfill/API. Source: [OfficialSessionWorker](../../src/main/java/dts/com/examination/application/service/session/OfficialSessionWorker.java), [OfficialExamSessionService](../../src/main/java/dts/com/examination/application/service/session/OfficialExamSessionService.java), [LegacyResultOutbox](../../src/main/java/dts/com/examination/application/service/session/LegacyResultOutbox.java).
