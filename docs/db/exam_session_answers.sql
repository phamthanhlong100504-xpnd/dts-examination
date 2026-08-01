-- Table: exam_session_answers
-- Service: examination
-- Entities mapped: ExamSessionAnswer
-- Engine: PostgreSQL
-- Mô tả: Lưu trữ câu trả lời của thí sinh cho từng câu hỏi trong một phiên thi cụ thể.
-- Bảng này hỗ trợ đa dạng các loại câu hỏi thông qua trường JSONB selected_answer và lưu giữ trạng thái hiển thị qua display_snapshot.
--
-- Cấu trúc tham khảo (JSONB):
-- 1. display_snapshot: Lưu trạng thái hiển thị của câu hỏi lúc thi. Giúp UI/Business logic không bị ảnh hưởng nếu sau này bổ sung thêm các loại hiển thị phức tạp (matrix, drag & drop, media block...).
--    - Ví dụ: { "position": 5, "sectionId": "TRAFFIC_SIGNS", "optionOrder": ["C", "A", "D", "B"] }
-- 2. selected_answer: Câu trả lời thực tế của thí sinh, format tùy biến theo từng loại câu hỏi ở runtime.
--    - Ví dụ (Trắc nghiệm): { "type": "single_choice", "optionIds": ["A"] }
--    - Ví dụ (Tự luận): { "type": "text", "text": "Tôi chọn đáp án A" }
--
-- Quản lý vòng đời (Lifecycle):
-- - Khi thí sinh trả lời câu hỏi, một bản ghi được tạo ra (hoặc cập nhật nếu đã có).
-- - Trạng thái chấm điểm ban đầu (score, is_correct) là NULL. Sau quá trình chấm thi (thủ công hoặc tự động),
--   is_correct và score sẽ được cập nhật.
-- - Không dùng khóa ngoại database-level cho question_id vì thuộc về service Question Bank (Cross-service reference).
-- - Khóa chính sử dụng UUID tự sinh qua hàm gen_random_uuid().

CREATE TABLE exam_session_answers (
    id UUID NOT NULL DEFAULT gen_random_uuid(),    -- Khóa chính, định danh duy nhất cho câu trả lời
    exam_session_id UUID NOT NULL,    -- Khóa ngoại tham chiếu đến phiên thi (exam_sessions) của thí sinh
    question_id UUID NOT NULL,    -- Khóa ngoại logic tham chiếu đến câu hỏi thuộc Question Bank service
    selected_answer JSONB,    -- Câu trả lời của thí sinh (định dạng JSON linh hoạt theo loại câu hỏi)
    display_snapshot JSONB,    -- Snapshot lưu trạng thái hiển thị của câu hỏi (thứ tự, section...)
    is_correct BOOLEAN,    -- Kết quả chấm điểm: TRUE (đúng), FALSE (sai), NULL (chưa được chấm điểm).
    score NUMERIC(5,2),    -- Điểm số đạt được của câu trả lời. NULL nếu chưa được chấm điểm.
    answered_at TIMESTAMPTZ NOT NULL,    -- Thời điểm thí sinh thực hiện trả lời câu hỏi này
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,    -- Thời điểm tạo bản ghi lần đầu
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP    -- Thời điểm cập nhật bản ghi lần cuối
);

ALTER TABLE exam_session_answers
    ADD CONSTRAINT pk_exam_session_answers PRIMARY KEY (id),
    ADD CONSTRAINT fk_exam_session_answers_exam_session_id FOREIGN KEY (exam_session_id) REFERENCES exam_sessions(id),
    ADD CONSTRAINT ck_exam_session_answers_score CHECK (score >= 0),
    ADD CONSTRAINT ck_exam_session_answers_has_answer CHECK (selected_answer IS NOT NULL);

COMMENT ON COLUMN exam_session_answers.id IS 'Khóa chính, định danh duy nhất cho câu trả lời';
COMMENT ON COLUMN exam_session_answers.exam_session_id IS 'Khóa ngoại tham chiếu đến phiên thi (exam_sessions) của thí sinh';
COMMENT ON COLUMN exam_session_answers.question_id IS 'Khóa ngoại logic tham chiếu đến câu hỏi thuộc Question Bank service (Cross-service reference)';
COMMENT ON COLUMN exam_session_answers.selected_answer IS 'Câu trả lời của thí sinh (JSON format linh hoạt theo loại câu hỏi: single_choice, text...)';
COMMENT ON COLUMN exam_session_answers.display_snapshot IS 'Snapshot lưu trạng thái hiển thị của câu hỏi lúc thi (vị trí, thứ tự trộn đáp án, section...)';
COMMENT ON COLUMN exam_session_answers.is_correct IS 'Kết quả chấm điểm: TRUE (đúng), FALSE (sai), NULL (chưa được chấm điểm).';
COMMENT ON COLUMN exam_session_answers.score IS 'Điểm số đạt được của câu trả lời. NULL nếu chưa được chấm điểm.';
COMMENT ON COLUMN exam_session_answers.answered_at IS 'Thời điểm thí sinh thực hiện trả lời câu hỏi này';
COMMENT ON COLUMN exam_session_answers.created_at IS 'Thời điểm tạo bản ghi lần đầu';
COMMENT ON COLUMN exam_session_answers.updated_at IS 'Thời điểm cập nhật bản ghi lần cuối';

-- Index hỗ trợ việc lấy danh sách câu trả lời theo từng phiên thi nhanh chóng
CREATE INDEX ix_exam_session_answers_exam_session_id ON exam_session_answers (exam_session_id);

-- Index hỗ trợ việc truy vấn thống kê, phân tích câu trả lời cho một câu hỏi cụ thể
CREATE INDEX ix_exam_session_answers_question_id ON exam_session_answers (question_id);

-- Trigger: auto-update updated_at on row modification
CREATE TRIGGER trg_exam_session_answers_updated_at
    BEFORE UPDATE ON exam_session_answers
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_updated_at();
