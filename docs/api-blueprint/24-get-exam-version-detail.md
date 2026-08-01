# Examination Service – Get Exam Version Detail

## Part 1 — API Specification

### 1.1 Endpoint Overview
- **Path**: `GET /api/v1/exam-versions/{versionId}`
- **Method**: `GET`
- **Purpose**: Retrieve the full details of a specific exam version, including its structural and rule references.

### 1.2 Request Specification

#### Headers
- `Authorization`: `Bearer <token>`

#### Path Variables
| Name | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `versionId` | UUID | Yes | Unique identifier of the exam version |

### 1.3 Response Specification

- **Success Status**: 200 OK

#### Response Body
| Name | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Unique identifier of the version |
| `examId` | UUID | ID of the parent exam |
| `versionNo` | Integer | Version sequence number |
| `title` | String | Title of the version |
| `examType` | String | Exam type |
| `thumbnailId` | UUID | Thumbnail image ID |
| `examStructureId` | UUID | Structure layout ID |
| `examRuleId` | UUID | Rule configuration ID |
| `examCriteriaId` | UUID | Passing criteria ID |
| `contentType` | String | Content type |
| `contentId` | UUID | Content reference ID |
| `startedAt` | ISO-8601 | Start time (if any) |
| `endedAt` | ISO-8601 | End time (if any) |
| `status` | String | Current status |
| `metadata` | JSON | Additional custom data |
| `createdAt` | ISO-8601 | Creation timestamp |
| `updatedAt` | ISO-8601 | Last update timestamp |

### 1.4 Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam_version:read` permission | Access denied |
| `NF_001` | 404 Not Found | Version does not exist | Exam version not found |

---

## Part 2 — Processing Specification

### Controller Layer
1. Authenticate request via `Authorization` header.
2. Delegate to Service Layer.

### Service Layer
1. **Permission Validation**: Verify the user has `exam_version:read` permission.
2. **Business Workflow**:
   - Fetch the version using `versionId`.
   - Throw 404 Not Found if missing or logically deleted.
3. Map entity to detailed response DTO.

### Repository Layer
1. Query `exam_versions` by `id` where `deleted_at IS NULL`.

### External Interaction
None.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT
- **Target Table**: `exam_versions`
- **Conditions**: `id = :versionId AND deleted_at IS NULL`
- **Expected Result**: A single exam version row.
