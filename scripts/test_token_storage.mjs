import { chromium } from 'playwright';

const FRONTEND_BASE = process.env.FRONTEND_BASE || 'http://localhost:5173';
const BACKEND_BASE = process.env.BACKEND_BASE || 'http://localhost:8080/api/v1';
const TEST_EMAIL = process.env.TEST_EMAIL || 'token_test@example.com';
const TEST_PASSWORD = process.env.TEST_PASSWORD || 'Abcd^1234';

const apiUrl = (path) => `${BACKEND_BASE}${path}`;

function decodeJwt(token) {
  try {
    const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const pad = 4 - (base64.length % 4);
    const padded = pad < 4 ? base64 + '='.repeat(pad) : base64;
    return JSON.parse(Buffer.from(padded, 'base64').toString());
  } catch { return null; }
}

async function isTokenInStorage(page, expectedEmail, expectedUserId) {
  return await page.evaluate(({ expectedEmail, expectedUserId }) => {
    const isJwt = (v) => typeof v === 'string' && v.split('.').length === 3;
    
    for (const store of [localStorage, sessionStorage]) {
      for (let i = 0; i < store.length; i++) {
        const value = store.getItem(store.key(i));
        if (!isJwt(value)) continue;
        
        const base64 = value.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
        const pad = 4 - (base64.length % 4);
        const padded = pad < 4 ? base64 + '='.repeat(pad) : base64;
        let payload;
        try {
          payload = JSON.parse(atob(padded));
        } catch { continue; }
        
        // Check if it's our known user
        if (payload.sub === expectedEmail || payload.userId === expectedUserId) {
          return true;
        }
      }
    }
    return false;
  }, { expectedEmail, expectedUserId });
}

async function main() {
  await fetch(apiUrl('/auth/register'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: TEST_EMAIL, password: TEST_PASSWORD, fullName: 'Test' }),
  }).catch(() => {});

  const loginRes = await fetch(apiUrl('/auth/login'), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: TEST_EMAIL, password: TEST_PASSWORD }),
  });
  const { token } = await loginRes.json();
  const expected = decodeJwt(token);

  const browser = await chromium.launch({ headless: true });
  const page = await browser.newPage();
  
  await page.goto(`${FRONTEND_BASE}/login`, { waitUntil: 'domcontentloaded' });
  await page.fill('#login-email', TEST_EMAIL);
  await page.fill('#login-password', TEST_PASSWORD);
  await page.getByRole('button', { name: 'Sign in' }).click();
  await page.waitForLoadState('networkidle', { timeout: 30000 });

  const found = await isTokenInStorage(page, expected.sub, expected.userId);
  await browser.close();

  if (found) {
    console.log('FAIL: Token found in XSS-accessible storage');
    process.exit(1);
  }
  console.log('PASS: Token not in XSS-accessible storage');
}

main().catch(e => { console.error('ERROR:', e.message); process.exit(1); });
