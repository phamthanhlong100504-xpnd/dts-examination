# Examination Service – Get Exam Version List

## Part 1 — API Specification

### 1.1 Endpoint Overview
- **Path**: `GET /api/v1/exams/{examId}/versions`
- **Method**: `GET`
- **Purpose**: Retrieve a paginated list of versions belonging to a specific exam, with optional filtering by status.

### 1.2 Request Specification

#### Headers
- `Authorization`: `Bearer <token>`

#### Path Variables
| Name | Type | Required | Description |
| :--- | :--- | :--- | :--- |
| `examId` | UUID | Yes | Unique identifier of the exam |

#### Query Parameters
| Name | Type | Required | Default | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `page` | Integer | No | 0 | Page number (0-indexed) | >= 0 |
| `size` | Integer | No | 20 | Number of records per page | 1 - 100 |
| `status` | String | No | | Filter by version status | Enum: DRAFT, PUBLISHED, ARCHIVED, HIDDEN |

### 1.3 Response Specification

- **Success Status**: 200 OK

#### Response Body
| Name | Type | Description |
| :--- | :--- | :--- |
| `content` | Array | List of exam versions |
| `content[].id` | UUID | Unique identifier of the version |
| `content[].versionNo` | Integer | Version sequence number |
| `content[].title` | String | Version title |
| `content[].status` | String | Current status |
| `content[].createdAt` | ISO-8601 | Creation timestamp |
| `pageable` | Object | Pagination metadata (Spring Data format) |
| `totalElements` | Long | Total number of matched versions |

### 1.4 Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam_version:read` permission | Access denied |
| `NF_001` | 404 Not Found | Parent exam does not exist | Exam not found |

---

## Part 2 — Processing Specification

### Controller Layer
1. Authenticate request via `Authorization` header.
2. Resolve `Pageable` parameters.
3. Delegate to Service Layer.

### Service Layer
1. **Permission Validation**: Verify the user has `exam_version:read` permission.
2. **Business Validation**: Check if `examId` exists.
3. **Business Workflow**:
   - Query the repository for versions matching `examId` and `status` (if provided).
   - Apply pagination.
4. Return mapped paginated DTO.

### Repository Layer
1. Query `exams` to verify existence.
2. Query `exam_versions` using pagination and dynamic filters.

### External Interaction
None.

---

## Part 3 — Data Interaction

- **Operation Type**: SELECT (PAGINATION)
- **Target Table**: `exam_versions`
- **Conditions**: `exam_id = :examId AND deleted_at IS NULL AND (:status IS NULL OR status = :status)`
- **Expected Result**: Page object containing exam versions.
