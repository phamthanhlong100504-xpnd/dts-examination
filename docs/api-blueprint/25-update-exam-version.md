# Examination Service – Update Exam Version

## Part 1 — API Specification

### 1.1 Endpoint Overview
- **Path**: `PATCH /api/v1/exam-versions/{versionId}`
- **Method**: `PATCH`
- **Purpose**: Partially update an exam version. Not allowed if the version is in `PUBLISHED` or `ARCHIVED` status.

### 1.2 Request Specification

#### Headers
- `Authorization`: `Bearer <token>`
- `Content-Type`: `application/json`

#### Path Variables
| Name | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `versionId` | UUID | Yes | Unique identifier of the exam version |

#### Request Body
| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `title` | String | No | Title of the version | Max 255 chars |
| `thumbnailId` | UUID | No | Image reference for thumbnail | |
| `examStructureId` | UUID | No | Structure layout ID | Must exist in DB |
| `examRuleId` | UUID | No | Rule configuration ID | Must exist in DB |
| `examCriteriaId` | UUID | No | Passing criteria ID | Must exist in DB |
| `startedAt` | ISO-8601 | No | Scheduled start time | Must be before `endedAt` |
| `endedAt` | ISO-8601 | No | Scheduled end time | Must be after `startedAt` |
| `metadata` | JSON | No | Additional custom data | Valid JSON |

*(Note: `versionNo`, `examId`, `examType`, `contentType`, `contentId` are immutable once created via standard update).*

### 1.3 Response Specification

- **Success Status**: 200 OK

#### Response Body
| Name | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Unique identifier of the version |
| `title` | String | Updated title |
| `updatedAt` | ISO-8601 | Update timestamp |

### 1.4 Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam_version:update` permission | Access denied |
| `VAL_001` | 400 Bad Request | Payload validation failed | Invalid input data |
| `NF_001` | 404 Not Found | Version does not exist | Exam version not found |
| `NF_002` | 404 Not Found | Foreign key configuration missing | Referenced configuration not found |
| `BUS_002` | 409 Conflict | Status constraint violation | Cannot update a PUBLISHED or ARCHIVED version |

---

## Part 2 — Processing Specification

### Controller Layer
1. Authenticate request via `Authorization` header.
2. Validate path variables and payload.
3. Delegate to Service Layer.

### Service Layer
1. **Permission Validation**: Verify the user has `exam_version:update` permission.
2. **Business Validation**: 
   - Fetch target version. Throw 404 if missing.
   - If version `status` is `PUBLISHED` or `ARCHIVED`, throw 409 Conflict.
   - Validate referencing foreign keys if they are present in the request.
3. **Transaction Boundary**: Start database transaction.
4. **Business Workflow**:
   - Apply non-null fields from DTO to the entity.
   - Set `updated_by` to authenticated user ID.
   - Set `updated_at` to current timestamp.
5. Save the entity via Repository.
6. Commit transaction.
7. Return updated basic info DTO.

### Repository Layer
1. Query `exam_versions` by `id`.
2. Update `exam_versions` row.

### External Interaction
None.

---

## Part 3 — Data Interaction

- **Operation Type**: UPDATE
- **Target Table**: `exam_versions`
- **Conditions**: `id = :versionId`
- **Expected Result**: Row is updated with new values.
