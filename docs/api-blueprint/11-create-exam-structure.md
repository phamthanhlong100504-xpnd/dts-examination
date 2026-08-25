# Create Exam Structure

## Part 0 — Classification & Identity

- **API Name**: Create Exam Structure
- **API Type**: Internal (Admin)
- **Module**: Content Builder
- **Feature**: Exam Structure Configuration
- **Description**: Creates a new structure and layout configuration for examinations.
- **Related Tables**: `exam_structures`
- **Related Services**: Identity Service (for authorization and auditing)

---

## Part 1 — API Contract

### Endpoint

- **HTTP Method**: POST
- **URL**: `/api/v1/exam-structures`
- **Content Type**: `application/json`

### Request

#### Headers

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `Authorization` | String | Yes | Bearer token for authentication | Must be a valid JWT |

#### Request Body

| Name | Type | Required | Description | Validation Rules |
| :--- | :--- | :--- | :--- | :--- |
| `title` | String | Yes | Title of the exam structure | Not blank, max 255 chars |
| `sections` | JSON Array | Yes | Array defining sections of the exam | Min 1 element |
| `sections[].code` | String | Yes | Code identifier for the section | Not blank |
| `sections[].title` | String | Yes | Display title for the section | Not blank |
| `sections[].questionCount` | Integer | Yes | Number of questions in this section | > 0 |
| `sections[].score` | Integer | Yes | Points per question in this section | >= 0 |
| `sections[].order` | Integer | Yes | Order of the section within the structure | Unique within the array |
| `metadata` | JSON Object | No | Extensible metadata | Valid JSON object |

### Response

- **Success Status**: 201 Created

#### Response Body

| Name | Type | Description |
| :--- | :--- | :--- |
| `id` | UUID | Unique identifier of the created structure |
| `title` | String | Title of the exam structure |
| `status` | String | Status of the structure (ACTIVE) |
| `sections` | JSON Array | Defined sections of the exam |
| `metadata` | JSON Object | Extensible metadata |
| `createdAt` | DateTime | ISO-8601 creation timestamp |
| `updatedAt` | DateTime | ISO-8601 update timestamp |

### Error Codes

| Error Code | HTTP Status | Business Meaning | Client Message |
| :--- | :--- | :--- | :--- |
| `AUTH_001` | 401 Unauthorized | Missing or invalid token | Unauthorized access |
| `AUTH_003` | 403 Forbidden | Lacks `exam_structure:create` permission | Access denied |
| `VAL_001` | 400 Bad Request | Request payload validation failed | Invalid input data provided |

---

## Part 2 — Processing Specification

### Controller Layer

1. Validate input payload format against requirements.
2. Authenticate the request via `Authorization` header.
3. Map the incoming JSON request to an internal creation DTO.
4. Delegate execution to the Service Layer.

### Service Layer

1. **Permission Validation**: Verify the user has the `exam_structure:create` permission.
2. **Business Validation**: 
   - Check if `sections` array has at least 1 item.
   - Check if `sections[].order` values are unique.
3. **Business Workflow**:
   - Initialize a new `ExamStructure` entity.
   - Assign the input values to the entity.
   - Set `status` to `ACTIVE`.
   - Set `created_by` and `updated_by` from the current authenticated user context.
4. **Transaction Boundary**: Start database transaction.
5. Save the entity via the Repository Layer.
6. Commit transaction.
7. Map the saved entity to the response DTO.

### Repository Layer

1. Persist the new `ExamStructure` entity into the database.

### External Interaction

None

### Validation

- **Request Validation**: JSR-303 / standard validation on `title` (not blank, max 255), `sections` (not empty).
- **Business Validation**: Order uniqueness across the `sections` array.
- **Permission Validation**: Requires `exam_structure:create`.

---

## Part 3 — Data Interaction

- **Operation Type**: INSERT
- **Target Table**: `exam_structures`
- **Conditions**: N/A
- **Expected Result**: A new row is inserted.

---

## Part 4 — Operational Notes

- **Idempotency**: Not guaranteed (generates a new UUID each time).
- **Tenant Isolation**: Not Applicable.
- **Retry Strategy**: None.
- **Audit Logging**: Successful creation is logged with the user ID.
- **Monitoring**: Standard API latency metrics.
- **Tracing**: Pass standard request ID in logs.
