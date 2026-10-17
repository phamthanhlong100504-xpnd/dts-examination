package dts.com.examination.application.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/** Read only the profile fields needed for examination eligibility. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record IdentityMeResponse(Boolean success, Profile data) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(UUID id, String status, String selectedLicenseClass) {}
}
