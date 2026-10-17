package dts.com.examination.api.form;

import jakarta.validation.constraints.Min;

public record SessionActionRequest(@Min(0) Integer version, @Min(1) Integer targetPosition, Boolean marked) {}
