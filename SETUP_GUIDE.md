# FlowBoard Setup & Running Guide

## Project Structure

```
CS698_Project_TrelloPlus/
├── backend/                    # Spring Boot backend (Java)
│   ├── src/
│   │   ├── main/java/com/flowboard/
│   │   │   ├── config/        # Security & app config
│   │   │   ├── controller/    # REST API endpoints
│   │   │   ├── dto/           # Data transfer objects
│   │   │   ├── entity/        # JPA entities
│   │   │   ├── repository/    # Data access layer
│   │   │   ├── service/       # Business logic
│   │   │   └── FlowBoardApplication.java  # Main entry point
│   │   └── resources/
│   │       ├── application.yml  # Configuration
│   │       └── db/migration/    # Database migrations (Flyway)
│   ├── pom.xml
│   └── README.md
├── src/                        # React frontend
│   ├── app/
│   │   ├── components/        # React components
│   │   ├── pages/             # Page components
│   │   ├── services/          # API service (api.ts)
│   │   ├── store/             # Zustand stores
│   │   └── App.tsx
│   └── main.tsx
├── docs/                       # Documentation & specs
├── package.json               # Frontend dependencies
└── vite.config.ts             # Vite configuration
```

## Tech Stack

**Backend:**
- Java 21
- Spring Boot 3.2.0
- Maven 3.8+
- PostgreSQL 16.1
- Spring Security + JWT
- LangChain4j (for LLM integration - currently mocked)

**Frontend:**
- React 18.3.1
- TypeScript
- Vite
- Zustand (state management)
- TailwindCSS + Radix UI

## Prerequisites

- **Java 21+** (download from [oracle.com](https://www.oracle.com/java/technologies/downloads/))
- **Maven 3.8+** (on Arch Linux: `sudo pacman -S maven`)
- **PostgreSQL 16+** (download from [postgresql.org](https://www.postgresql.org/download/))
- **Node.js 18+** (download from [nodejs.org](https://nodejs.org/))

## Installation & Setup

### 1. Database Setup

#### Option A: Using Docker (Recommended)

```bash
docker run --name flowboard-postgres \
  -e POSTGRES_USER=flowboard \
  -e POSTGRES_PASSWORD=flowboard_password \
  -e POSTGRES_DB=flowboard \
  -p 5432:5432 \
  -d postgres:16
```

Verify the container is running:
```bash
docker ps | grep flowboard-postgres
```

#### Option B: Manual PostgreSQL Setup

```bash
# Create database and user
createdb flowboard
createuser flowboard
psql flowboard -c "ALTER USER flowboard WITH PASSWORD 'flowboard_password';"
```

### 2. Backend Setup

```bash
cd backend

# Build the project (downloads dependencies, runs tests, packages)
mvn clean install

# Start the backend server
mvn spring-boot:run

# The backend will start on http://localhost:8080/api/v1
```

The database schema will be automatically created by Flyway migrations on first run.

A default admin user will be created:
- **Email:** admin@flowboard.com
- **Password:** admin123

### 3. Frontend Setup

```bash
# Install dependencies
npm install

# Start the development server
npm run dev

# The frontend will be available at http://localhost:5173
```

## Testing the Implementation

### 1. Start Both Servers

**Terminal 1 (Backend):**
```bash
cd backend
mvn spring-boot:run
# Wait for: "Started FlowBoardApplication in..."
```

**Terminal 2 (Frontend):**
```bash
npm run dev
# You should see: "VITE v... ready in ... ms"
```

### 2. Test User Story 1: AI Board Generation

1. **Navigate to:** http://localhost:5173/login

2. **Login with demo account:**
   - Email: `admin@flowboard.com`
   - Password: `admin123`

3. **Create a new project:**
   - Click "Create New Project"
   - Enter project name: "Website Redesign"
   - Enter description: "We need to redesign our company website with modern  UI/UX principles. The project involves wireframing, design system setup, high-fidelity mockups, and implementation."
   - Click "Generate AI Board"

4. **View generated board:**
   - The AI engine will generate workflow stages and pre-populate tasks based on your description
   - Review the preview and click "Create Board"
   - The project will be created and you'll be navigated to the Kanban board

5. **Test board functionality:**
   - Create new tasks
   - Move tasks between columns
   - Add new columns
   - Update task details

### 3. Create Another Account (Optional)

1. Go to http://localhost:5173/register
2. Create a new account with:
   - Username: your choice
   - Email: your email
   - Password: minimum 8 characters

3. Create projects from this second account to test the multi-user functionality

## API Endpoints

All endpoints require JWT authentication (except `/auth/*`):

### Authentication
```
POST   /auth/register             - Register a new user
POST   /auth/login                - Login and get JWT token
```

### Projects
```
POST   /projects                  - Create new AI-generated project
GET    /projects                  - List user's projects
GET    /projects/{projectId}      - Get project details
DELETE /projects/{projectId}      - Delete project
```

### Board Management
```
POST   /boards/{boardId}/stages              - Add new stage/column
PUT    /boards/stages/{stageId}              - Update stage
DELETE /boards/stages/{stageId}              - Delete stage

POST   /boards/stages/{stageId}/cards        - Create card/task
PUT    /boards/cards/{cardId}                - Update card
PUT    /boards/cards/{cardId}/move           - Move card to another stage
DELETE /boards/cards/{cardId}                - Delete card
```

## Key Features Implemented

### User Story 1: AI-Powered Board Generation ✅
- [x] Project manager create project description
- [x] AI analyzes description and generates board structure
- [x] Pre-populates tasks based on project context
- [x] Multiple workflow stages automatically created
- [x] Tasks intelligently organized by stage

### Authentication ✅
- [x] User registration with email/password
- [x] JWT-based authentication
- [x] Secure password hashing (bcrypt)
- [x] Role-based access control (ADMIN, MANAGER, MEMBER, VIEWER)

### Project & Board Management ✅
- [x] Create projects with AI generation
- [x] View project details
- [x] Manage team members
- [x] Add/remove/rename columns
- [x] Create/update/delete tasks
- [x] Move tasks between columns

### AI Engine ✅
- [x] Mocked LLM for board generation
- [x] Context-aware task generation
- [x] Keyword-based stage creation
- [x] Extensible for real LLM integration

## Troubleshooting

### Backend won't start

**Error: "Connection to localhost:5432 refused"**
- Ensure PostgreSQL is running: `docker ps` or `pg_isready`
- Check credentials in `backend/src/main/resources/application.yml`

**Error: "Address already in use"**
- Change port in `application.yml`: `server.port: 8081`
- Or kill existing process: `lsof -ti:8080 | xargs kill -9`

### Frontend API calls failing

**Error: "Fetch URL not found/CORS error"**
- Ensure backend is running on port 8080
- Check API URL in `src/app/services/api.ts`
- Verify CORS is enabled in `backend/src/main/java/com/flowboard/config/SecurityConfig.java`

**Error: "401 Unauthorized"**
- Token might be expired or invalid
- Log in again to get a fresh token
- Check token is properly stored in localStorage

### Database migration fails

**Error: "Relations already exist"**
- Run: `docker exec flowboard-postgres dropdb -U flowboard flowboard`
- Then restart the backend to recreate the database

## Development Notes

### Mocked LLM Service
The AI engine is currently mocked and generates contextual tasks based on keywords in the project description. To integrate with real LLM:

1. Update `AIEngine.java` in the `analyzeProjectDescription` method
2. Implement actual API calls to OpenAI or Anthropic
3. Remove the mocking logic and handle real responses

### Next Steps (Future Development)
- [ ] Implement User Story 2: Meeting Summary & Approval
- [ ] Implement User Story 3: Change Review & Approval
- [ ] Add real LLM integration (OpenAI/Anthropic)
- [ ] Implement WebSocket for real-time updates
- [ ] Add notification system
- [ ] Implement change approval workflow
- [ ] Add meeting transcript support
- [ ] Multi-language support

## Support & Documentation

- Backend docs: `backend/README.md`
- Architecture spec: `docs/UNIFIED_BACKEND_ARCHITECTURE.md`
- API spec: `docs/BACKEND_MODULE_SPECIFICATIONS.md`
- Dev specs: `docs/dev_spec_1.md`, `dev_spec_2.md`, `dev_spec_3.md`

## License

Project developed for CS698 - Software Engineering Capstone

## Contact

For questions or issues, refer to the development specifications in the `/docs` directory.
