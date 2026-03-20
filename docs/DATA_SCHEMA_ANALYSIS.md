# Data Schema Analysis: Dev Spec vs Backend vs Frontend

## Executive Summary

There are **significant schema mismatches** between the final merged dev spec, the backend implementation, and the frontend models. The frontend was built based on the 3 separate original dev specs, while the backend was built from the merged dev spec. These differences create integration challenges.

---

## 1. ENTITY MAPPING COMPARISON

### 1.1 Project Entity

| Field | Final Dev Spec | Backend | Frontend | Issue |
|-------|---------------|---------|----------|-------|
| id | UUID | string (ULID) | string | ✅ Compatible |
| name | VARCHAR(255) | string | string | ✅ Compatible |
| description | TEXT | string | string | ✅ Compatible |
| owner_id | UUID (FK) | userId | - | ⚠️ Frontend missing |
| boards | One-to-Many | Board[] | boardId (single) | 🔴 **MAJOR** - Frontend expects single boardId |
| members | - | - | ProjectMember[] | ⚠️ Frontend expects members on project |
| columns/stages | - | - | BoardColumn[] | 🔴 **MAJOR** - Frontend nests columns in Project |
| tasks/cards | - | - | BoardTask[] | 🔴 **MAJOR** - Frontend nests tasks in Project |
| decisions | - | - | ProjectDecision[] | ⚠️ Frontend-specific |
| created_at | TIMESTAMP | Date | - | ⚠️ Frontend missing timestamps |

### 1.2 Board Entity (Stage in Frontend = Stage in Backend/Dev Spec)

| Field | Final Dev Spec | Backend | Frontend | Issue |
|-------|---------------|---------|----------|-------|
| id | UUID | string | - | ✅ Backend uses ULID |
| project_id | UUID (FK) | projectId | boardId on Project | 🔴 Different structure |
| name | VARCHAR(255) | string | - | ✅ Compatible |
| stages | One-to-Many | Stage[] | columns: BoardColumn[] | ⚠️ Different naming |
| template | Enum | BoardTemplate | - | ⚠️ Frontend missing |
| settings | - | BoardSettings | - | ⚠️ Frontend missing |
| ai_generated | BOOLEAN | boolean | - | ⚠️ Frontend missing |

### 1.3 Stage Entity (Column in Frontend)

| Field | Final Dev Spec | Backend | Frontend | Issue |
|-------|---------------|---------|----------|-------|
| id | UUID | string | string | ✅ Compatible |
| board_id | UUID (FK) | boardId | - | ✅ Backend uses ULID |
| name | VARCHAR(255) | string | title | ⚠️ Different naming (name vs title) |
| position/order | INTEGER | orderIndex | - | ⚠️ Frontend missing |
| color | - | string | color | ✅ Compatible |
| wip_limit | INTEGER | number | - | ⚠️ Frontend missing |
| cards | One-to-Many | Card[] | - | ⚠️ Backend nests, Frontend doesn't |

### 1.4 Card Entity (Task in Frontend)

| Field | Final Dev Spec | Backend | Frontend | Issue |
|-------|---------------|---------|----------|-------|
| id | UUID | string | string | ✅ Compatible |
| stage_id | UUID (FK) | stageId | columnId | ⚠️ Different naming |
| title | VARCHAR(500) | string | string | ✅ Compatible |
| description | TEXT | string | string | ✅ Compatible |
| priority | VARCHAR/Enum | Priority enum | 'LOW'\|'MEDIUM'\|'HIGH'\|'CRITICAL' | ⚠️ Case mismatch |
| type | Enum | CardType | - | ⚠️ Frontend missing |
| status | Enum | CardStatus | - | ⚠️ Frontend missing |
| orderIndex | INTEGER | number | - | ⚠️ Frontend missing |
| assignee_id | UUID (FK) | assignedTo | assignee: {name} | 🔴 **MAJOR** - Different structure |
| due_date | TIMESTAMP | string (ISO) | createdDate | 🔴 **MAJOR** - Different concepts |
| estimated_hours | DOUBLE | number | - | ⚠️ Frontend missing |
| tags | - | string[] | - | ⚠️ Frontend missing |
| ai_generated | BOOLEAN | boolean | - | ⚠️ Frontend missing |

### 1.5 User Entity

| Field | Final Dev Spec | Backend | Frontend | Issue |
|-------|---------------|---------|----------|-------|
| id | UUID | string | - | ✅ Compatible |
| email | VARCHAR(255) | string | email | ✅ Compatible |
| first_name | VARCHAR(100) | - | - | ⚠️ Backend uses 'name' only |
| last_name | VARCHAR(100) | - | - | ⚠️ Backend uses 'name' only |
| name | - | string | name | ✅ Compatible |
| role | Enum | - | role | ⚠️ Backend missing |
| avatar | - | avatarUrl | - | ✅ Similar |

---

## 2. CRITICAL SCHEMA ISSUES

### Issue #1: Project-Board Relationship Mismatch 🔴

**Dev Spec & Backend:**
```typescript
Project {
  id: string;
  boards: Board[];  // One-to-Many
}

Board {
  id: string;
  projectId: string;
  stages: Stage[];
}
```

**Frontend:**
```typescript
Project {
  id: string;
  boardId: string;  // Single board reference
  columns: BoardColumn[];  // Stages nested in Project
  tasks: BoardTask[];  // Cards nested in Project
}
```

**Impact:** Frontend expects a flat structure with columns and tasks directly on Project. Backend returns nested structure.

### Issue #2: Terminology Differences 🔴

| Dev Spec/Backend | Frontend |
|------------------|----------|
| Stage | Column/BoardColumn |
| Card | Task/BoardTask |
| assignedTo (userId) | assignee: { name: string } |
| stageId | columnId |
| name (stage) | title (column) |
| dueDate | createdDate |

**Impact:** Field names don't match, requiring mapping layer.

### Issue #3: Priority Enum Case Mismatch ⚠️

**Backend:**
```typescript
enum Priority {
  LOW = 'low',
  MEDIUM = 'medium',
  HIGH = 'high',
  CRITICAL = 'critical'
}
```

**Frontend:**
```typescript
type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
```

**Impact:** Backend returns lowercase, frontend expects uppercase.

### Issue #4: Missing Meeting & Change Entities 🔴

**Dev Spec** defines:
- meeting_sessions
- meeting_notes
- meeting_summaries
- action_items
- decisions
- changes
- approval_requests

**Backend:** Only implements User Story 1 (Board Generation)
- Users, Projects, Boards, Stages, Cards only

**Frontend:** Has full models for:
- Meetings
- Action Items
- Decisions
- Changes
- Approvals

**Impact:** Backend missing 2/3 of required entities for full functionality.

### Issue #5: Assignee Structure Mismatch 🔴

**Backend:**
```typescript
assignedTo: string;  // User ID
```

**Frontend:**
```typescript
assignee?: {
  name: string;
};
```

**Impact:** Backend stores ID, frontend expects object with name.

---

## 3. RECOMMENDATIONS

### Option A: Adapt Backend to Frontend Schema (Quick Fix)

Modify backend responses to match frontend expectations:

1. **Flatten Project Response:**
```typescript
// Transform backend Project + Board to frontend Project format
function transformProject(backendProject): FrontendProject {
  const board = backendProject.boards[0]; // Take first board
  return {
    id: backendProject.id,
    name: backendProject.name,
    description: backendProject.description,
    boardId: board.id,
    columns: board.stages.map(transformStage),
    tasks: board.stages.flatMap(s => s.cards.map(transformCard)),
    members: [], // Fetch separately or add to project
    decisions: [], // From meeting store
  };
}
```

2. **Transform Field Names:**
```typescript
function transformCard(backendCard): FrontendTask {
  return {
    id: backendCard.id,
    title: backendCard.title,
    description: backendCard.description,
    priority: backendCard.priority.toUpperCase(), // 'high' -> 'HIGH'
    columnId: backendCard.stageId,
    assignee: backendCard.assignedTo ? { name: 'Unknown' } : undefined, // Need user lookup
    createdDate: backendCard.createdAt,
  };
}
```

**Pros:** Frontend works immediately
**Cons:** Backend API not RESTful, loses flexibility

### Option B: Adapt Frontend to Backend Schema (Proper Fix)

Update frontend stores to match backend structure:

1. **Update Project Interface:**
```typescript
interface Project {
  id: string;
  name: string;
  description: string;
  boards: Board[];  // Array instead of single boardId
  // Remove columns and tasks from here
}
```

2. **Update Components:**
- Dashboard fetches projects, then fetches boards separately
- KanbanBoard component receives `boardId` and fetches board with stages/cards

**Pros:** Proper REST architecture, matches dev spec
**Cons:** Requires significant frontend refactoring

### Option C: Create API Adapter Layer (Hybrid)

Create a service layer that transforms between formats:

```typescript
// api-adapter.ts
export const adaptedApi = {
  async getProjects(): Promise<FrontendProject[]> {
    const backendProjects = await api.getProjects();
    return Promise.all(backendProjects.map(transformProject));
  },
  
  async createProject(data): Promise<FrontendProject> {
    const backendResponse = await api.createProject({
      name: data.name,
      description: data.description,
      templateHint: 'SOFTWARE_DEVELOPMENT',
    });
    return transformProject(backendResponse);
  }
};
```

**Pros:** Clean separation, can migrate incrementally
**Cons:** Additional complexity, duplicate type definitions

---

## 4. IMPLEMENTATION PRIORITY

### Immediate (Required for Demo)
1. Fix Project response structure (flatten boards/columns/tasks)
2. Transform priority values to uppercase
3. Map field names (stageId ↔ columnId, name ↔ title)
4. Handle assignee transformation

### Short-term (Required for Full Functionality)
1. Implement User Story 2: Meeting Management
2. Implement User Story 3: Change Approval
3. Add missing entities: meetings, action_items, decisions, changes

### Long-term (Architecture)
1. Decide on unified schema (Option A, B, or C)
2. Migrate frontend OR backend to match
3. Remove adapter layer if no longer needed

---

## 5. SPECIFIC SCHEMA RECOMMENDATIONS FOR FINAL DEV SPEC

The final merged dev spec has some inconsistencies that should be addressed:

### 5.1 Normalization Issue
The dev spec shows proper database normalization (separate tables), but this creates complexity for the frontend.

**Recommendation:** Define a "View Model" section showing the aggregated structures the API should return.

### 5.2 Missing Field Consistency
Some fields have inconsistent naming:
- `created_at` vs `createdAt` (database vs API)
- `owner_id` vs `userId` vs `facilitator_id` (similar concepts, different names)

**Recommendation:** Standardize naming conventions in the spec.

### 5.3 Frontend Expectations Not Documented
The dev specs were written from a backend perspective without considering the frontend's need for flattened/nested data.

**Recommendation:** Add "API Response Examples" showing the actual JSON structure expected by the frontend.

---

## 6. CONCLUSION

The schema differences are significant but manageable. The quickest path to a working demo is **Option A** (adapt backend responses to frontend expectations). However, for long-term maintainability, **Option B** (proper REST API with frontend adaptation) is recommended.

The dev spec merging process created an inconsistency: User Stories 2 and 3 entities (meetings, changes, approvals) were documented but not implemented in the backend. This should be addressed for full functionality.
