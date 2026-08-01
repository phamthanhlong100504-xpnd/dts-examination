# Examination Service – Archive Exam Version

## Part 1 — API Specification

### 1.1 Endpoint Overview
- **Path**: `POST /api/v1/exam-versions/{versionId}/archive`
- **Method**: `POST`
- **Purpose**: Archive a `PUBLISHED` exam version manually. 

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
| `status` | String | `ARCHIVED` |

### 1.4 Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam_version:archive` permission | Access denied |
| `NF_001` | 404 Not Found | Version does not exist | Exam version not found |
| `BUS_006` | 409 Conflict | Invalid status transition | Only PUBLISHED versions can be explicitly archived |

---

## Part 2 — Processing Specification

### Controller Layer
1. Authenticate request via `Authorization` header.
2. Delegate to Service Layer.

### Service Layer
1. **Permission Validation**: Verify the user has `exam_version:archive` permission.
2. **Business Validation**: 
   - Fetch target version. Throw 404 if missing.
   - If version `status` is not `PUBLISHED`, throw 409 Conflict.
3. **Transaction Boundary**: Start database transaction.
4. **Business Workflow**:
   - Change version's `status` to `ARCHIVED`.
   - Query if there are any other `PUBLISHED` versions for the parent `examId` (there shouldn't be).
   - If no other published version exists, fetch parent `Exam` and change its `status` to `ARCHIVED` (or `HIDDEN`, depending on business rules).
5. Save entities via Repository.
6. Commit transaction.
7. Return basic DTO.

### Repository Layer
1. Query `exam_versions` by `id`.
2. Update `exam_versions` status.

### External Interaction
None.

---

## Part 3 — Data Interaction

- **Operation Type**: UPDATE
- **Target Table**: `exam_versions`, `exams`
- **Conditions**: `id = :versionId`
- **Expected Result**: Status is updated to `ARCHIVED`.
