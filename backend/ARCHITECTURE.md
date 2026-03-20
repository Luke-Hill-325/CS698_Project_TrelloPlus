# FlowBoard Backend Architecture

## Overview

FlowBoard backend is a Spring Boot application using Java 21.

## Technology Stack

| Component | Technology |
|-----------|------------|
| Runtime | Java 21 |
| Framework | Spring Boot 3.2.0 |
| Build Tool | Maven 3.8+ |
| Database | PostgreSQL 16.1 |
| Security | Spring Security + JWT |
| Migrations | Flyway |

## Project Structure

```
backend/
├── src/main/java/com/flowboard/
│   ├── FlowBoardApplication.java    # Main entry point
│   ├── config/                      # Security & app config
│   ├── controller/                  # REST API endpoints
│   ├── dto/                         # Data transfer objects
│   ├── entity/                      # JPA entities
│   ├── repository/                  # Data access layer
│   └── service/                     # Business logic
├── src/main/resources/
│   ├── application.yml              # Configuration
│   └── db/migration/                # Flyway migrations
└── pom.xml                          # Maven dependencies
```

## Running Locally

```bash
# Install dependencies and build
mvn clean install

# Run the application
mvn spring-boot:run

# The backend will start on http://localhost:8080/api/v1
```

## API Endpoints

### Authentication
- `POST /auth/register` - Register a new user
- `POST /auth/login` - Login and get JWT token
- `GET /auth/profile` - Get current user profile

### Projects
- `POST /projects` - Create new project
- `GET /projects` - List user's projects
- `GET /projects/{id}` - Get project details
- `PUT /projects/{id}` - Update project
- `DELETE /projects/{id}` - Delete project

### Board Management
- `POST /boards/{boardId}/stages` - Add stage/column
- `PUT /boards/stages/{stageId}` - Update stage
- `DELETE /boards/stages/{stageId}` - Delete stage
- `POST /boards/stages/{stageId}/cards` - Create card
- `PUT /boards/cards/{cardId}` - Update card
- `PUT /boards/cards/{cardId}/move` - Move card to another stage
- `DELETE /boards/cards/{cardId}` - Delete card

### Team Members
- `GET /projects/{projectId}/members` - Get project members
- `POST /projects/{projectId}/members` - Add team member
- `PUT /projects/{projectId}/members/{userId}` - Update member role
- `DELETE /projects/{projectId}/members/{userId}` - Remove member
