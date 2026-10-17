# Chức năng và nghiệp vụ DTS Examination

## 1. Trách nhiệm dữ liệu

Examination sở hữu parent exam, phiên bản, cấu trúc, quy tắc, tiêu chí, phiên thi và câu trả lời. Content Builder sở hữu ngân hàng câu hỏi; Identity sở hữu user, GPLX và permission; Result sở hữu lịch sử thống nhất/analytics/leaderboard. Không lấy dữ liệu Progress để chấm official exam. Thi Practice tiếp tục theo repo Practice và nhánh tương thích cũ trong Examination khi examType=PRACTICE.

## 2. Quản trị bài và cấu hình

Parent exam là đối tượng quản lý và trạng thái hiển thị. Version xác định title riêng, examType, nguồn nội dung, structure/rule/criteria, cửa sổ startedAt/endedAt và official profile. Version mới DRAFT, versionNo cấp dưới khóa parent. Update/delete chỉ với DRAFT chưa có lịch sử session. Published/archived phải clone thành DRAFT mới thay vì viết lại cấu hình đã dùng.

Publish xác minh source và cấu hình hoàn chỉnh trước khi thay version cũ. Các version PUBLISHED trước của cùng parent chuyển ARCHIVED; parent DRAFT/PUBLISHED chuyển PUBLISHED, parent HIDDEN vẫn HIDDEN. Archive chỉ với PUBLISHED. Trình tự khóa parent→version→structure→rule→criteria giúp giữ trạng thái tham chiếu nhất quán. Rule/criteria/structure có guard khi bị phiên bản sử dụng; không sửa cấu hình đã có lịch sử để thay grade cũ.

## 3. Cấu trúc, rule, criteria

Structure gồm section code/title/questionCount/score/order, chapterIds, criticalQuestionCount. Code không trùng không phân biệt hoa thường, order không âm và duy nhất, count>0, score>=0; quota critical null là không ràng buộc riêng,0 loại câu critical tại section. Tổng câu=sum count; maxScore=sum count*score, có kiểm overflow. Cấu trúc tạo mới DRAFT theo schema V003.

Rule điều khiển duration, retry/maxRetry/interval, grace/autoSubmit, điều hướng/skip/review, pause/resume, shuffle, fullscreen/tab-switch, release và hiển thị đáp án/giải thích/điểm từng câu. Official validator buộc các flag/giới hạn kết hợp hợp lệ; không chỉ chấp nhận vì request JSON parse được.

Criteria có tổng điểm, điểm đạt, phương pháp chấm, rounding, penalty, mandatory và section minimum. Official hỗ trợ SUM/WEIGHTED/PERCENTAGE theo validator; không hỗ trợ BEST_OF/AVERAGE. Mandatory fixed question cần MANUAL, MAX_WRONG chưa có giới hạn nên từ chối. Rounding precision0..2, UNNECESSARY chỉ hợp lệ nếu mọi đóng góp biểu diễn chính xác. Rule và criteria đóng băng khi publish và khi tạo session.

## 4. Ngân hàng và chọn câu

Official profile ghi licenseClass, bankVersion, selectionMode và manualQuestionIds. OFFICIAL dùng contentType=BANK_VERSION. Catalog policy csgt-2262-2025.json xác định hạng, tổng câu, thời lượng, số đúng tối thiểu, quota critical và quota chủ đề theo khoảng hiệu lực. Đây là policy của source đã bàn giao, không phải tài liệu cam kết luật ngoài hệ thống luôn không đổi.

AUTO_MATRIX kiểm khả năng phân bổ lúc publish; mỗi attempt mới chọn ngẫu nhiên từ bank, không dùng một paper cố định cho mọi học viên. Matching slot tránh lấy trùng khi điều kiện chương giao nhau. MANUAL yêu cầu đủ số UUID duy nhất đúng bank/hạng, đúng quota và lưu nội dung/assignment riêng. Không gửi manualQuestionIds cho AUTO.

Bank phải có catalog6 chương sourceChapterId1..6 nhất quán bankVersion. Question published có contentVersion sha256, option/chapter/media hợp lệ, loại SINGLE_CHOICE/MULTIPLE_CHOICE/TRUE_FALSE. Nội dung batch được đối chiếu metadata trước đóng băng; lỗi source không bị biến thành paper rỗng. Content Builder internal auth token chỉ gắn vào ba đường bank/metadata/batch, không forward key đáp án ra student.

## 5. Danh sách bài và điều kiện tham gia

StudentExamReadService lấy profile từ Identity bằng Bearer của người gọi, kiểm UUID đúng và status ACTIVE. selectedLicenseClass bắt buộc; query licenseClass không cho chọn hạng khác profile. Danh sách theo OPEN/UPCOMING/PARTICIPATED, pagination0-based. Summary trả canStart/canResume/reasonCode/retryAvailableAt để frontend dùng trực tiếp.

Availability căn parent/version/time; maxAttempts=1 khi không retry, maxRetry+1 nếu retry với giới hạn dương, null nếu unlimited theo source. Số lượt đã dùng tính từ lịch sử; không tự bỏ failed/expired để cấp lượt mới. Một active session có thể được tiếp tục, nhiều active session trả conflict. READY là trạng thái pause đã lưu, không phải phiên mới chưa bắt đầu. PARTICIPATED vẫn có thể hiển thị lịch sử sở hữu khi parent ẩn; không cho bắt đầu mới vì thế.

## 6. Phiên thi bền vững

Start khóa parent/version/history, kiểm thời gian/profile/policy, tái sử dụng active session hợp lệ hoặc tạo attempt mới. AUTO lấy paper mới; MANUAL dùng snapshot publish. Metadata lưu officialPaper riêng chứa cả answer key, cấu hình và assignment, historyDisplayTitle chụp parent title lúc bắt đầu. Answer row tạo cho mọi câu, position và optionOrder shuffle một lần; refresh/resume không đổi thứ tự.

DTO public trả nội dung/options đã loại answer key. Gửi câu trả lời phải kèm version hiện tại; save/mark/navigation tăng version. Batch được validate toàn bộ trước ghi, question phải thuộc phiên, option đúng câu, multiple choice là chuỗi UUID phân tách bằng dấu phẩy. Xóa lựa chọn dùng value rỗng/null trong selectedAnswer hợp lệ. VERSION_CONFLICT yêu cầu đọc phiên mới, không tự ghi đè state cũ.

FREE cho truy cập tự do; SEQUENTIAL giới hạn bước tiến và quyền xem lại theo NONE/CURRENT_SECTION/ALL. allowSkip=false chặn bỏ câu khi điều hướng/nộp thủ công. Mark là trạng thái cờ, không tự thay đáp án. Violation eventId dedup, TAB_SWITCH/FULLSCREEN_EXIT có thể làm fail và kết thúc theo rule; đây là sự kiện client báo, không phải bằng chứng giám sát hệ điều hành độc lập.

## 7. Thời gian, pause và nộp

Server Clock quyết định expiry=min(start+duration,exam endedAt). Pause chỉ khi rule cho pause/resume và chưa vượt maxPauseCount, lưu remaining, chuyển READY và deadline chờ resume theo min(maxPauseDuration,resumeTimeout,exam end). Resume cộng thời gian đã pause, đặt lại expiry không vượt end. Không lấy timer browser làm nguồn hạn nộp.

Khi đến expiry, không ghi thêm đáp án; grace chỉ cho nộp đáp án đã lưu, bị chặn bởi exam end. Hết cutoff, autoSubmit=true chấm/nộp, false chuyển EXPIRED. Worker xử lý phiên hết hạn; GET có thể phát sinh chuyển trạng thái thời gian nên không coi mọi GET session là thuần read-only.

Submit yêu cầu Idempotency-Key1..100 ký tự chữ/số/._:-. Retry cùng key trả cùng phiên, khác key sau nộp có thể409IDEMPOTENCY_CONFLICT. Kết thúc ghi grade snapshot và stable eventId trong transaction cùng answers/session. Không cộng điểm bằng cách frontend tự tính lại hoặc gửi lại nộp để sửa grade.

## 8. Chấm điểm

Chấm từ frozen paper, không đọc cấu hình admin hoặc bank hiện tại. Câu đúng khi tập option đã chọn bằng chính xác tập đáp án; chưa trả lời là không đúng, critical sai/chưa trả lời làm fail. Tính điểm trọng số, penalty sai/bỏ trống, clamp tổng tối thiểu0, PERCENTAGE quy về100, rounding theo criteria.

PASS cần không có proctoring failure, không fail critical, đạt mandatory/section minima, đủ số đúng của matrix và đạt passScore. Result summary phân biệt wrong đã trả lời với unanswered; không lấy total-correct làm wrongQuestions nếu vẫn có câu trống.

## 9. Công bố và lịch sử

IMMEDIATE/AFTER_SUBMIT công bố sau SUBMITTED; AFTER_EXAM_END chờ end đã đóng băng; MANUAL chờ admin release-result. Trước release source result chỉ có trạng thái/thông tin release, không summary/answers; review409. Release chỉ submitted MANUAL, retry204 giữ nguyên marker.

Outbox resultEvent trong session.metadata được worker gửi learning-results, key=userId, chờ Kafka ack rồi DELIVERED. Retry exponential tối đa300 giây; delivery ít nhất một lần, Result cần dedup event/source. Kafka mất kết nối không cần học viên nộp lại bài đã commit. Phiên chưa release không được đẩy sang lịch sử để lộ grade. LegacyResultOutbox có namespace metadata riêng cho nhánh cũ, không được đánh đồng với official outbox.

History event version2 chứa OFFICIAL_EXAM, EXAM_SESSION/sourceId=session, targetId=parent exam, license/title/số câu/attempt/timestamps và result flags. title lịch sử cũ không được chụp có thể null; backfill không lấy tên admin hiện tại để thay lịch sử. Student list title là version title, historyDisplayTitle là parent title tại start; không bắt hai nguồn này luôn giống nhau.

## 10. Ranh giới truy cập và vận hành

Admin cần ROLE_ADMIN cùng module permission hoặc legacy permission. Học viên cần ROLE_STUDENT, chỉ đọc phiên của mình; source403 cho người khác, Result history detail có contract404 riêng. Session response no-store; không cache paper/grade theo URL dùng chung người dùng.

Schema V001–V004 được quản lý bằng history/checksum. Build artifacts, hai JDBC scratch helpers và exam.txt nháp không thuộc source chạy web; chúng được chuyển backup trước commit xóa. Không chạy helper DROP/JDBC đó. Nâng cấp source từ Git không xóa hoặc sửa data cũ; database/account/bank cần cấu hình riêng của môi trường.
