#!/bin/bash

# TrelloPlus Backend API Test Script
# This script tests all major endpoints of the backend

BASE_URL="http://localhost:3000/api/v1"
TOKEN=""
PROJECT_ID=""
BOARD_ID=""
STAGE_ID=""
CARD_ID=""

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "========================================="
echo "  TRELLOPLUS BACKEND API TEST"
echo "========================================="
echo ""

# Check if server is running
echo "Checking if server is running at $BASE_URL..."
if ! curl -s --max-time 5 "${BASE_URL}/health" > /dev/null 2>&1; then
  echo -e "${RED}✗${NC} Server is not running!"
  echo ""
  echo "Please start the server first with:"
  echo "  cd /home/TacticalTaco/CS_AI-Assisted_Software/ws/CS698_Project_TrelloPlus/backend"
  echo "  npm run dev"
  echo ""
  exit 1
fi
echo -e "${GREEN}✓${NC} Server is running"
echo ""

# Helper function for HTTP requests
# Usage: request METHOD ENDPOINT [DATA] [AUTH_TOKEN]
request() {
  local method=$1
  local endpoint=$2
  local data="${3:-}"
  local auth="${4:-}"
  
  local curl_opts="-s --max-time 10"
  
  if [ "$method" = "GET" ]; then
    if [ -n "$auth" ]; then
      curl $curl_opts -H "Authorization: Bearer $auth" "${BASE_URL}${endpoint}"
    else
      curl $curl_opts "${BASE_URL}${endpoint}"
    fi
  else
    if [ -n "$auth" ]; then
      curl $curl_opts -X "$method" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $auth" \
        -d "$data" \
        "${BASE_URL}${endpoint}"
    else
      curl $curl_opts -X "$method" \
        -H "Content-Type: application/json" \
        -d "$data" \
        "${BASE_URL}${endpoint}"
    fi
  fi
}

# Track test results
TESTS_PASSED=0
TESTS_FAILED=0

# Helper to check test result
check_result() {
  local test_name="$1"
  local response="$2"
  local extract_id="${3:-}"
  local id_var="${4:-}"
  
  if echo "$response" | grep -q '"success":true'; then
    echo -e "${GREEN}✓${NC} $test_name passed"
    TESTS_PASSED=$((TESTS_PASSED + 1))
    
    # Extract ID if requested
    if [ -n "$extract_id" ] && [ -n "$id_var" ]; then
      local extracted=$(echo "$response" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
      eval "$id_var='$extracted'"
    fi
    return 0
  else
    echo -e "${RED}✗${NC} $test_name failed"
    echo "  Response: $(echo "$response" | head -c 200)"
    TESTS_FAILED=$((TESTS_FAILED + 1))
    return 1
  fi
}

# Test 1: Health Check
echo "1. Testing Health Check..."
HEALTH=$(request "GET" "/health")
check_result "Health check" "$HEALTH"

# Test 2: Register User
echo ""
echo "2. Testing User Registration..."
REGISTER=$(request "POST" "/auth/register" '{"email":"testuser@example.com","password":"password123","firstName":"Test","lastName":"User"}')
# Registration might fail if user exists, so be lenient
if echo "$REGISTER" | grep -q '"success":true'; then
  echo -e "${GREEN}✓${NC} Registration passed"
  TESTS_PASSED=$((TESTS_PASSED + 1))
elif echo "$REGISTER" | grep -q 'already exists'; then
  echo -e "${YELLOW}⚠${NC} User already exists (continuing...)"
  TESTS_PASSED=$((TESTS_PASSED + 1))
else
  check_result "Registration" "$REGISTER"
fi

# Test 3: Login
echo ""
echo "3. Testing Login..."
LOGIN=$(request "POST" "/auth/login" '{"email":"testuser@example.com","password":"password123"}')
if check_result "Login" "$LOGIN" "extract" "TOKEN"; then
  echo "   Token: $TOKEN"
fi

# Skip remaining tests if login failed
if [ -z "$TOKEN" ]; then
  echo ""
  echo -e "${RED}Login failed - cannot continue with authenticated tests${NC}"
  exit 1
fi

# Test 4: Get Current User
echo ""
echo "4. Testing Get Current User..."
ME=$(request "GET" "/auth/me" "" "$TOKEN")
check_result "Get current user" "$ME"

# Test 5: AI Generate Board Preview
echo ""
echo "5. Testing AI Board Preview..."
AI_PREVIEW=$(request "POST" "/ai/generate-board" '{"projectName":"E-commerce Website","description":"Build a scalable e-commerce platform with user authentication, product catalog, shopping cart, and payment processing","templateHint":"SOFTWARE_DEVELOPMENT","stageCount":5,"cardsPerStage":3}')
if check_result "AI preview" "$AI_PREVIEW"; then
  STAGE_COUNT=$(echo "$AI_PREVIEW" | grep -o '"stageCount":[0-9]*' | head -1 | cut -d':' -f2)
  echo "   Generated $STAGE_COUNT stages"
fi

# Test 6: Create Project with AI Board
echo ""
echo "6. Testing Create Project with AI Board..."
PROJECT=$(request "POST" "/projects" '{"name":"E-commerce Website","description":"Build a scalable e-commerce platform with user authentication, product catalog, shopping cart, and payment processing","templateHint":"SOFTWARE_DEVELOPMENT","stageCount":5,"cardsPerStage":3}' "$TOKEN")
if check_result "Project creation" "$PROJECT" "extract" "PROJECT_ID"; then
  echo "   Project ID: ${PROJECT_ID:0:20}..."
fi

# Skip if project creation failed
if [ -z "$PROJECT_ID" ]; then
  echo ""
  echo -e "${RED}Project creation failed - skipping dependent tests${NC}"
fi

# Test 7: List Projects
echo ""
echo "7. Testing List Projects..."
PROJECTS=$(request "GET" "/projects" "" "$TOKEN")
if check_result "List projects" "$PROJECTS"; then
  COUNT=$(echo "$PROJECTS" | grep -o '"id":"' | wc -l)
  echo "   Found $COUNT projects"
fi

# Test 8: Get Project Details (only if we have project ID)
if [ -n "$PROJECT_ID" ]; then
  echo ""
  echo "8. Testing Get Project Details..."
  PROJECT_DETAIL=$(request "GET" "/projects/$PROJECT_ID" "" "$TOKEN")
  if check_result "Get project details" "$PROJECT_DETAIL"; then
    BOARD_ID=$(echo "$PROJECT_DETAIL" | grep -o '"id":"[^"]*"' | sed -n '2p' | cut -d'"' -f4)
    if [ -n "$BOARD_ID" ]; then
      echo "   Board ID: ${BOARD_ID:0:20}..."
    fi
  fi
fi

# Test 9: Get Board (only if we have board ID)
if [ -n "$BOARD_ID" ]; then
  echo ""
  echo "9. Testing Get Board..."
  BOARD=$(request "GET" "/boards/$BOARD_ID" "" "$TOKEN")
  if check_result "Get board" "$BOARD"; then
    STAGE_ID=$(echo "$BOARD" | grep -o '"id":"[^"]*"' | sed -n '3p' | cut -d'"' -f4)
    if [ -n "$STAGE_ID" ]; then
      echo "   Stage ID: ${STAGE_ID:0:20}..."
    fi
  fi
fi

# Test 10: Create Card (only if we have stage ID)
if [ -n "$STAGE_ID" ]; then
  echo ""
  echo "10. Testing Create Card..."
  CARD_PAYLOAD="{\"stageId\":\"$STAGE_ID\",\"title\":\"Implement User Authentication\",\"description\":\"Add JWT-based authentication with refresh tokens\",\"priority\":\"HIGH\",\"type\":\"TASK\",\"estimatedHours\":8}"
  CARD=$(request "POST" "/cards" "$CARD_PAYLOAD" "$TOKEN")
  if check_result "Card creation" "$CARD" "extract" "CARD_ID"; then
    echo "   Card ID: ${CARD_ID:0:20}..."
  fi
fi

# Test 11: Update Card (only if we have card ID)
if [ -n "$CARD_ID" ]; then
  echo ""
  echo "11. Testing Update Card..."
  UPDATE=$(request "PUT" "/cards/$CARD_ID" '{"priority":"CRITICAL","title":"Implement Secure User Authentication"}' "$TOKEN")
  check_result "Card update" "$UPDATE"
fi

# Test 12: Get Card (only if we have card ID)
if [ -n "$CARD_ID" ]; then
  echo ""
  echo "12. Testing Get Card..."
  GET_CARD=$(request "GET" "/cards/$CARD_ID" "" "$TOKEN")
  check_result "Get card" "$GET_CARD"
fi

# Summary
echo ""
echo "========================================="
if [ $TESTS_FAILED -eq 0 ]; then
  echo -e "  ${GREEN}ALL TESTS PASSED!${NC}"
else
  echo -e "  ${YELLOW}TESTS COMPLETED WITH WARNINGS${NC}"
fi
echo "========================================="
echo ""
echo "Results:"
echo "  Passed: $TESTS_PASSED"
echo "  Failed: $TESTS_FAILED"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
  echo -e "${GREEN}Your backend is ready to use!${NC}"
  echo "API Base URL: $BASE_URL"
  exit 0
else
  echo -e "${YELLOW}Some tests failed, but the backend may still be functional.${NC}"
  exit 1
fi
