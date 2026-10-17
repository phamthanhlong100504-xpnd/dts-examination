package dts.com.examination.api.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SessionViolationRequest(@NotNull UUID eventId, @NotBlank String eventType) {}
