# Examination Service – Create Exam Version

## Part 1 — API Specification

### 1.1 Endpoint Overview
- **Path**: `POST /api/v1/exams/{examId}/versions`
- **Method**: `POST`
- **Purpose**: Creates a new version for a specific exam. The new version will automatically be assigned the next available version number (`max + 1`) and a `DRAFT` status.

### 1.2 Request Specification

#### Headers
- `Authorization`: `Bearer <token>`
- `Content-Type`: `application/json`

#### Path Variables
| Name | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `examId` | UUID | Yes | Unique identifier of the exam |

#### Request Body
| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `title` | String | Yes | Title of the version | Not blank, Max 255 chars |
| `examType` | String | Yes | Type of the exam | Enum: MOCK_TEST, REAL_TEST, etc. |
| `thumbnailId` | UUID | No | Image reference for thumbnail | |
| `examStructureId` | UUID | Yes | Structure layout ID | Must exist in DB |
| `examRuleId` | UUID | Yes | Rule configuration ID | Must exist in DB |
| `examCriteriaId` | UUID | No | Passing criteria ID | If provided, must exist in DB |
| `contentType` | String | Yes | Type of the content linked | Enum: QUESTION, QUESTION_BLOCK, CHAPTER, LEARNING_PROGRAM |
| `contentId` | UUID | Yes | Content identifier in Question Bank | |
| `startedAt` | ISO-8601 | No | Scheduled start time | Must be before `endedAt` |
| `endedAt` | ISO-8601 | No | Scheduled end time | Must be after `startedAt` |
| `metadata` | JSON | No | Additional custom data | Valid JSON |

### 1.3 Response Specification

- **Success Status**: 201 Created

#### Response Body
| Name | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Unique identifier of the new version |
| `examId` | UUID | ID of the parent exam |
| `versionNo` | Integer | Sequentially assigned version number |
| `title` | String | Title of the version |
| `status` | String | `DRAFT` |

### 1.4 Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam_version:create` permission | Access denied |
| `VAL_001` | 400 Bad Request | Payload validation failed | Invalid input data |
| `NF_001` | 404 Not Found | Parent exam does not exist | Exam not found |
| `NF_002` | 404 Not Found | Structure/Rule/Criteria does not exist | Referenced configuration not found |
| `BUS_001` | 409 Conflict | Invalid time range | `startedAt` must be before `endedAt` |

---

## Part 2 — Processing Specification

### Controller Layer
1. Authenticate request via `Authorization` header.
2. Validate path variables and JSON payload against JSR-380 annotations.
3. Delegate to Service Layer.

### Service Layer
1. **Permission Validation**: Verify the user has `exam_version:create` permission (via `PERM_exam_version:create`).
2. **Business Validation**: 
   - Fetch target exam using `examId`. Throw 404 Not Found if missing or deleted.
   - Verify that `examStructureId`, `examRuleId`, and `examCriteriaId` (if provided) exist.
   - If both `startedAt` and `endedAt` are provided, ensure `startedAt < endedAt`.
3. **Transaction Boundary**: Start database transaction.
4. **Business Workflow**:
   - Query the maximum `version_no` for the given `examId`.
   - Set `version_no = max + 1` (default to 1 if no previous versions exist).
   - Set `status` to `DRAFT`.
   - Set `created_by` to authenticated user ID.
   - Set `created_at` to current UTC timestamp.
5. Save the `ExamVersion` entity via Repository.
6. Commit transaction.
7. Map saved entity to response DTO.

### Repository Layer
1. Query `exams`, `exam_structures`, `exam_rules`, `exam_criteria` to check existence.
2. Query `SELECT MAX(version_no) FROM exam_versions WHERE exam_id = :examId`.
3. Persist new `ExamVersion` entity to database.

### External Interaction
None.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT (MAX)
- **Target Table**: `exam_versions`
- **Conditions**: `exam_id = :examId`
- **Expected Result**: Maximum version number or null.

- **Operation Type**: INSERT
- **Target Table**: `exam_versions`
- **Conditions**: N/A
- **Expected Result**: A new exam version row is inserted.
