# Update Exam Structure

## Part 0 — Classification & Identity

- **API Name**: Update Exam Structure
- **API Type**: Internal (Admin)
- **Module**: Content Builder
- **Feature**: Exam Structure Configuration
- **Description**: Partially updates an existing exam structure's layout, title, or status.
- **Related Tables**: `exam_structures`, `exam_versions`
- **Related Services**: Identity Service (for authorization and auditing)

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: PATCH
- **URL**: `/api/v1/exam-structures/{structureId}`
- **Content Type**: `application/json`

### Request

#### Headers

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `Authorization` | String | Yes | Bearer token for authentication | Must be a valid JWT |

#### Path Variables

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `structureId` | UUID | Yes | Target exam structure ID | Valid UUID format |

#### Request Body

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `title` | String | No | New title | Max 255 chars |
| `sections` | JSON Array | No | New array of sections | Valid section format if provided |
| `status` | String | No | New status | ACTIVE/INACTIVE |
| `metadata` | JSON Object | No | Extensible metadata | Valid JSON object |

### Response

- **Success Status**: 200 OK

#### Response Body

| Name | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Unique identifier of the structure |
| `title` | String | Title of the exam structure |
| `status` | String | Status of the structure |

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam_structure:update` permission | Access denied |
| `AUTH_004` | 403 Forbidden | Fails ownership validation (SEC-006a) | Access denied to update resource |
| `VAL_001` | 400 Bad Request | Payload validation failed | Invalid input data |
| `NF_001` | 404 Not Found | Structure does not exist | Exam structure not found |
| `BUS_001` | 409 Conflict | Structure is in use by a PUBLISHED exam version | Cannot modify in-use structure |

---

## Part 2 — Processing Specification

### Controller Layer

1. Authenticate request.
2. Validate path variable and request body.
3. Map JSON payload to update DTO.
4. Delegate to Service Layer.

### Service Layer

1. **Permission Validation**: Verify the user has `exam_structure:update` permission.
2. **Transaction Boundary**: Start database transaction.
3. **Business Workflow**:
   - Call Repository to fetch structure by `id`. Throw 404 if not found.
   - Apply `SEC-006a`: If `createdBy != current_userId`, throw `AccessDeniedException` (Enforcing resource ownership).
   - Call Repository to check if `exam_versions` reference this `structureId` where `status = 'PUBLISHED'`. If yes, throw 409 Conflict.
   - Update fields that are present in the request payload.
   - Set `updated_by` to the current user ID.
4. Save entity via Repository.
5. Commit transaction.
6. Map to response DTO.

### Repository Layer

1. Query `exam_structures` by ID.
2. Query `exam_versions` to check active references.
3. Persist updated `exam_structures` entity.

### External Interaction

None

### Validation

- **Request Validation**: Null checks for partial updates.
- **Business Validation**: Verify not used by PUBLISHED exam versions.
- **Permission Validation**: Rule `SEC-006a` for resource ownership.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT
- **Target Table**: `exam_structures`
- **Conditions**: `id = :structureId AND deleted_at IS NULL`
- **Expected Result**: Entity fetched for update.

- **Operation Type**: SELECT (COUNT/EXISTS)
- **Target Table**: `exam_versions`
- **Conditions**: `exam_structure_id = :structureId AND status = 'PUBLISHED'`
- **Expected Result**: Return true/false for dependency check.

- **Operation Type**: UPDATE
- **Target Table**: `exam_structures`
- **Conditions**: `id = :structureId`
- **Expected Result**: Row is updated with new values.

---

## Part 4 — Operational Notes

- **Idempotency**: Yes (applying same updates yields same final state).
- **Tenant Isolation**: Not Applicable.
- **Retry Strategy**: None.
- **Audit Logging**: Required for updates.
- **Monitoring**: Standard latency metrics.
- **Tracing**: Request ID propagation.
