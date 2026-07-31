-- Table: exam_criterias
-- Service: examination-service
-- Entities mapped: ExamCriteria
-- Engine: PostgreSQL
-- Mô tả: Bảng lưu trữ bộ tiêu chí đánh giá bài thi (ví dụ: điểm sàn, số câu đúng tối thiểu, danh sách câu điểm liệt, trọng số). 
-- 
-- Mỗi version của bài thi (ExamVersion) sẽ tham chiếu đến một ExamCriteria.
-- Dữ liệu logic chấm điểm nằm ở cột `criteria` (JSONB) chứa passScore, gradingMethod, mandatoryRules, sectionRules, penalties... giúp dễ dàng mở rộng.
-- Bảng hỗ trợ soft-delete thông qua cột `deleted_at` để bảo toàn dữ liệu lịch sử chấm thi.

CREATE TABLE exam_criterias (
    id            UUID           NOT NULL DEFAULT gen_random_uuid(),  -- Khóa chính của bộ tiêu chí
    title         VARCHAR(255)   NOT NULL,                            -- Tên hiển thị của bộ tiêu chí (VD: Tiêu chí GPLX B2 chuẩn)
    criteria      JSONB          NOT NULL,                            -- Cấu hình logic đánh giá (minScore, passIfCorrectQuestions, criticalQuestionIds, sectionWeights)
    status        VARCHAR(30)    NOT NULL DEFAULT 'ACTIVE',           -- Trạng thái của bộ tiêu chí: ACTIVE, INACTIVE
    metadata      JSONB          NULL,                                -- Dữ liệu metadata bổ sung (vehicleType...)
    created_by    UUID           NULL,                                -- UUID người tạo
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- Thời gian tạo
    updated_by    UUID           NULL,                                -- UUID người cập nhật cuối
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- Thời gian cập nhật cuối
    deleted_at    TIMESTAMPTZ    NULL                                 -- Thời gian xóa mềm (NULL = chưa xóa)
);

ALTER TABLE exam_criterias
    ADD CONSTRAINT pk_exam_criterias PRIMARY KEY (id),
    ADD CONSTRAINT ck_exam_criterias_status CHECK (status IN ('ACTIVE', 'INACTIVE'));

COMMENT ON COLUMN exam_criterias.id IS 'Khóa chính, định danh duy nhất bộ tiêu chí đánh giá.';
COMMENT ON COLUMN exam_criterias.title IS 'Tên hiển thị của tiêu chí, tối đa 255 ký tự.';
COMMENT ON COLUMN exam_criterias.criteria IS 'Chứa cấu hình chấm điểm JSON: passScore, gradingMethod, mandatoryRules, sectionRules, penalties...';
COMMENT ON COLUMN exam_criterias.status IS 'Trạng thái hoạt động, chỉ cho phép ACTIVE hoặc INACTIVE.';
COMMENT ON COLUMN exam_criterias.metadata IS 'Thông tin bổ sung tuỳ chọn mở rộng cho từng loại tiêu chí (vd: vehicleType). NULL vì không bắt buộc.';
COMMENT ON COLUMN exam_criterias.created_by IS 'ID của người dùng đã tạo bộ tiêu chí này (tham chiếu cross-service tới user-service).';
COMMENT ON COLUMN exam_criterias.created_at IS 'Thời điểm bản ghi được tạo ra, lưu dưới dạng UTC.';
COMMENT ON COLUMN exam_criterias.updated_by IS 'ID của người dùng cập nhật thông tin lần cuối (tham chiếu cross-service tới user-service).';
COMMENT ON COLUMN exam_criterias.updated_at IS 'Thời điểm bản ghi được chỉnh sửa lần cuối, tự động cập nhật bởi trigger, lưu dưới dạng UTC.';
COMMENT ON COLUMN exam_criterias.deleted_at IS 'Thời điểm bản ghi bị xóa mềm. Nếu NULL nghĩa là bản ghi đang tồn tại và hợp lệ.';

-- Chỉ mục để tìm kiếm theo tiêu đề (hỗ trợ cho API GET List)
CREATE INDEX ix_exam_criterias_title ON exam_criterias (title) WHERE deleted_at IS NULL;

-- Chỉ mục để lọc nhanh danh sách theo status
CREATE INDEX ix_exam_criterias_status ON exam_criterias (status) WHERE deleted_at IS NULL;

-- Trigger: auto-update updated_at on row modification
-- (Lưu ý: function trigger_set_updated_at() giả định đã được tạo chung cho toàn bộ DB. Nếu chưa thì bỏ comment bên dưới)
-- CREATE OR REPLACE FUNCTION trigger_set_updated_at() RETURNS TRIGGER AS $$
-- BEGIN
--     NEW.updated_at = CURRENT_TIMESTAMP;
--     RETURN NEW;
-- END;
-- $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_exam_criterias_updated_at
    BEFORE UPDATE ON exam_criterias
    FOR EACH ROW
    EXECUTE FUNCTION trigger_set_updated_at();
