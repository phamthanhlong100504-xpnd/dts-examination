package dts.com.examination.application.service.selection;

import dts.com.examination.domain.entity.ExamStructure;
import dts.com.examination.domain.entity.ExamRule;
import dts.com.examination.domain.entity.ExamCriteria;

/** Private server configuration. Never use this record as an HTTP response. */
public record PublishedExamSnapshot(OfficialExamVersionConfig profile, ExamMatrixPolicy policy,
        ExamStructure structure, ExamRule rule, ExamCriteria criteria) {}
