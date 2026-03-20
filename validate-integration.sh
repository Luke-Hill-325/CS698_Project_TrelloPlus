#!/bin/bash

# Integration Validation Script
# Validates that frontend and backend are properly integrated

echo "========================================="
echo "  INTEGRATION VALIDATION"
echo "========================================="
echo ""

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

# Track validation results
VALIDATION_PASSED=0
VALIDATION_FAILED=0

# Helper function
validate() {
  local test_name="$1"
  local result="$2"
  
  if [ "$result" = "0" ]; then
    echo -e "${GREEN}✓${NC} $test_name"
    VALIDATION_PASSED=$((VALIDATION_PASSED + 1))
  else
    echo -e "${RED}✗${NC} $test_name"
    VALIDATION_FAILED=$((VALIDATION_FAILED + 1))
  fi
}

# 1. Check Backend TypeScript Compilation
echo "1. Validating Backend TypeScript..."
cd /home/TacticalTaco/CS_AI-Assisted_Software/ws/CS698_Project_TrelloPlus/backend
npm run typecheck > /dev/null 2>&1
validate "Backend TypeScript compiles" "$?"

# 2. Check Frontend API Client Exists
echo ""
echo "2. Checking Frontend API Client..."
if [ -f "/home/TacticalTaco/CS_AI-Assisted_Software/ws/CS698_Project_TrelloPlus/src/app/lib/api.ts" ]; then
  validate "API client exists (api.ts)" "0"
else
  validate "API client exists (api.ts)" "1"
fi

# 3. Check Frontend API Adapter Exists
echo ""
echo "3. Checking Frontend API Adapter..."
if [ -f "/home/TacticalTaco/CS_AI-Assisted_Software/ws/CS698_Project_TrelloPlus/src/app/lib/api-adapter.ts" ]; then
  validate "API adapter exists (api-adapter.ts)" "0"
else
  validate "API adapter exists (api-adapter.ts)" "1"
fi

# 4. Check Frontend Store Uses Adapter
echo ""
echo "4. Checking Frontend Store Integration..."
if grep -q "adaptedApi" /home/TacticalTaco/CS_AI-Assisted_Software/ws/CS698_Project_TrelloPlus/src/app/store/projectStore.ts; then
  validate "projectStore uses adaptedApi" "0"
else
  validate "projectStore uses adaptedApi" "1"
fi

# 5. Check API Base URL Configuration
echo ""
echo "5. Checking API Configuration..."
if grep -q "localhost:3000/api/v1" /home/TacticalTaco/CS_AI-Assisted_Software/ws/CS698_Project_TrelloPlus/src/app/lib/api.ts; then
  validate "API base URL configured" "0"
else
  validate "API base URL configured" "1"
fi

# 6. Check Backend Response Format
echo ""
echo "6. Checking Backend Response Format..."
if grep -q "AIGenerationResponse" /home/TacticalTaco/CS_AI-Assisted_Software/ws/CS698_Project_TrelloPlus/backend/src/types/index.ts; then
  validate "Backend has AIGenerationResponse type" "0"
else
  validate "Backend has AIGenerationResponse type" "1"
fi

# 7. Check Adapter Transform Functions
echo ""
echo "7. Checking Adapter Transformations..."
if grep -q "transformPriority" /home/TacticalTaco/CS_AI-Assisted_Software/ws/CS698_Project_TrelloPlus/src/app/lib/api-adapter.ts; then
  validate "Priority transformation exists" "0"
else
  validate "Priority transformation exists" "1"
fi

if grep -q "transformCard" /home/TacticalTaco/CS_AI-Assisted_Software/ws/CS698_Project_TrelloPlus/src/app/lib/api-adapter.ts; then
  validate "Card transformation exists" "0"
else
  validate "Card transformation exists" "1"
fi

if grep -q "transformStage" /home/TacticalTaco/CS_AI-Assisted_Software/ws/CS698_Project_TrelloPlus/src/app/lib/api-adapter.ts; then
  validate "Stage transformation exists" "0"
else
  validate "Stage transformation exists" "1"
fi

# 8. Check Auth Integration
echo ""
echo "8. Checking Auth Integration..."
if grep -q "localStorage.setItem('token'" /home/TacticalTaco/CS_AI-Assisted_Software/ws/CS698_Project_TrelloPlus/src/app/store/projectStore.ts; then
  validate "Token stored in localStorage" "0"
else
  validate "Token stored in localStorage" "1"
fi

if grep -q "localStorage.getItem('token')" /home/TacticalTaco/CS_AI-Assisted_Software/ws/CS698_Project_TrelloPlus/src/app/lib/api.ts; then
  validate "Token retrieved from localStorage" "0"
else
  validate "Token retrieved from localStorage" "1"
fi

# 9. Check Backend Endpoints
echo ""
echo "9. Checking Backend Endpoints..."
ENDPOINTS=(
  "/auth/register"
  "/auth/login"
  "/auth/me"
  "/ai/generate-board"
  "/projects"
  "/boards"
  "/cards"
)

for endpoint in "${ENDPOINTS[@]}"; do
  if grep -q "'$endpoint'" /home/TacticalTaco/CS_AI-Assisted_Software/ws/CS698_Project_TrelloPlus/src/app/lib/api.ts || \
     grep -q "\"$endpoint\"" /home/TacticalTaco/CS_AI-Assisted_Software/ws/CS698_Project_TrelloPlus/src/app/lib/api.ts || \
     grep -q "\`$endpoint" /home/TacticalTaco/CS_AI-Assisted_Software/ws/CS698_Project_TrelloPlus/src/app/lib/api.ts; then
    validate "Frontend has endpoint: $endpoint" "0"
  else
    validate "Frontend has endpoint: $endpoint" "1"
  fi
done

# 10. Summary
echo ""
echo "========================================="
if [ $VALIDATION_FAILED -eq 0 ]; then
  echo -e "  ${GREEN}ALL VALIDATIONS PASSED!${NC}"
else
  echo -e "  VALIDATION COMPLETE WITH WARNINGS"
fi
echo "========================================="
echo ""
echo "Results:"
echo "  Passed: $VALIDATION_PASSED"
echo "  Failed: $VALIDATION_FAILED"
echo ""

if [ $VALIDATION_FAILED -eq 0 ]; then
  echo "The frontend and backend are properly integrated!"
  echo ""
  echo "To test the integration:"
  echo "  1. Start backend: cd backend && npm run dev"
  echo "  2. Start frontend: npm run dev (in another terminal)"
  echo "  3. Open browser to http://localhost:5173"
  echo "  4. Login and create a project"
  exit 0
else
  echo "Some validations failed. Please review the issues above."
  exit 1
fi
