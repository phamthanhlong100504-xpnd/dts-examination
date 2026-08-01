# Delete Exam Structure

## Part 0 — Classification & Identity

- **API Name**: Delete Exam Structure
- **API Type**: Internal (Admin)
- **Module**: Content Builder
- **Feature**: Exam Structure Configuration
- **Description**: Soft deletes an exam structure if it is not in use by any exam versions.
- **Related Tables**: `exam_structures`, `exam_versions`
- **Related Services**: Identity Service (for authorization and auditing)

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: DELETE
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

### Response

- **Success Status**: 204 No Content

#### Response Body
*Empty Body*

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam_structure:delete` permission | Access denied |
| `AUTH_004` | 403 Forbidden | Fails ownership validation (SEC-006a) | Access denied to delete resource |
| `NF_001` | 404 Not Found | Structure does not exist | Exam structure not found |
| `BUS_002` | 409 Conflict | Structure is referenced by any exam versions | Cannot delete in-use structure |

---

## Part 2 — Processing Specification

### Controller Layer

1. Authenticate request.
2. Validate path variable.
3. Delegate to Service Layer.

### Service Layer

1. **Permission Validation**: Verify the user has `exam_structure:delete` permission.
2. **Transaction Boundary**: Start database transaction.
3. **Business Workflow**:
   - Call Repository to fetch structure by `id`. Throw 404 if not found.
   - Apply `SEC-006a`: Verify `createdBy == current_userId`. Throw 403 if false.
   - Check if structure is referenced by ANY `exam_versions` (regardless of status). If yes, throw 409 Conflict.
   - Set `deleted_at = CURRENT_TIMESTAMP` and `updated_by = current_userId`.
4. Save entity via Repository.
5. Commit transaction.

### Repository Layer

1. Fetch record from `exam_structures`.
2. Check references in `exam_versions`.
3. Update `deleted_at` field (Soft Delete).

### External Interaction

None

### Validation

- **Business Validation**: Cannot be deleted if used by ANY exam versions.
- **Permission Validation**: Requires `exam_structure:delete` and `SEC-006a` verification.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT
- **Target Table**: `exam_structures`
- **Conditions**: `id = :structureId AND deleted_at IS NULL`
- **Expected Result**: Row retrieved.

- **Operation Type**: SELECT (EXISTS)
- **Target Table**: `exam_versions`
- **Conditions**: `exam_structure_id = :structureId AND deleted_at IS NULL`
- **Expected Result**: True/False for usage check.

- **Operation Type**: UPDATE
- **Target Table**: `exam_structures`
- **Conditions**: `id = :structureId`
- **Expected Result**: Set `deleted_at = NOW()`.

---

## Part 4 — Operational Notes

- **Idempotency**: Yes (repeated calls return 404).
- **Tenant Isolation**: Not Applicable.
- **Retry Strategy**: None.
- **Audit Logging**: Important operation, log soft-delete action.
- **Monitoring**: Standard metrics.
- **Tracing**: Request ID propagation.
