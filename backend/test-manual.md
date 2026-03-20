# Manual API Testing Guide

If the test script doesn't work, you can test the API manually with these curl commands.

## Prerequisites

Start the server:
```bash
cd /home/TacticalTaco/CS_AI-Assisted_Software/ws/CS698_Project_TrelloPlus/backend
npm run dev
```

Wait for the server banner to appear, then run these commands in a new terminal.

---

## 1. Health Check

```bash
curl http://localhost:3000/api/v1/health
```

Expected response:
```json
{"success":true,"data":{"status":"healthy","timestamp":"2026-03-10T...","version":"1.0.0"}}
```

---

## 2. Register User

```bash
curl -X POST http://localhost:3000/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123","firstName":"Test","lastName":"User"}'
```

---

## 3. Login

```bash
curl -X POST http://localhost:3000/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password123"}'
```

Save the token from the response for subsequent requests.

---

## 4. Get Current User

Replace `<token>` with your actual token:

```bash
curl http://localhost:3000/api/v1/auth/me \
  -H "Authorization: Bearer <token>"
```

---

## 5. AI Generate Board Preview (No Auth Required)

```bash
curl -X POST http://localhost:3000/api/v1/ai/generate-board \
  -H "Content-Type: application/json" \
  -d '{
    "projectName": "E-commerce Platform",
    "description": "Build a scalable e-commerce platform with React and Node.js including user auth, product catalog, shopping cart, and payments",
    "templateHint": "SOFTWARE_DEVELOPMENT",
    "stageCount": 5,
    "cardsPerStage": 3
  }'
```

---

## 6. Create Project with AI Board

```bash
curl -X POST http://localhost:3000/api/v1/projects \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "name": "E-commerce Platform",
    "description": "Build a scalable e-commerce platform with React and Node.js",
    "templateHint": "SOFTWARE_DEVELOPMENT",
    "stageCount": 5,
    "cardsPerStage": 3
  }'
```

Save the `project.id` from the response.

---

## 7. Get Project Details

```bash
curl http://localhost:3000/api/v1/projects/<project_id> \
  -H "Authorization: Bearer <token>"
```

---

## 8. Get Board

```bash
curl http://localhost:3000/api/v1/boards/<board_id> \
  -H "Authorization: Bearer <token>"
```

---

## 9. Create Card

```bash
curl -X POST http://localhost:3000/api/v1/cards \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "stageId": "<stage_id>",
    "title": "Implement Authentication",
    "description": "Add JWT-based authentication",
    "priority": "HIGH",
    "type": "TASK",
    "estimatedHours": 8
  }'
```

---

## 10. Move Card

```bash
curl -X PUT http://localhost:3000/api/v1/cards/<card_id>/move \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{
    "stageId": "<new_stage_id>",
    "orderIndex": 0
  }'
```

---

## Troubleshooting

### "Connection refused" error
The server is not running. Start it with `npm run dev`.

### "Unauthorized" error
Your token is missing or invalid. Login again to get a new token.

### Empty response
The server might be busy or the endpoint doesn't exist. Check the server logs.

### CORS errors (in browser)
Use curl or Postman instead of browser console for testing.
