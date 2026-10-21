import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import test from 'node:test';
import { bootstrapE2eEnvironment } from '../scripts/bootstrap-api.mjs';

const runId = 'sdi-e2e-bootstrap-test';
const generatedPassword = 'fresh-strong-test-credential-7621';

async function withApi(handler, run) {
  const server = createServer(async (request, response) => {
    const chunks = [];
    for await (const chunk of request) chunks.push(chunk);
    const body = Buffer.concat(chunks).toString();
    await handler(request, response, body);
  });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const address = server.address();
  try {
    await run(`http://127.0.0.1:${address.port}/api/v1`);
  } finally {
    await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
  }
}

function json(response, status, data, headers = {}) {
  response.writeHead(status, { 'content-type': 'application/json', ...headers });
  response.end(JSON.stringify({ success: true, data }));
}

test('bootstraps disposable E2E identities and entitlement only through authorized APIs', async () => {
  const calls = [];
  const orgId = '11111111-1111-4111-8111-111111111111';
  const subscriptionId = '22222222-2222-4222-8222-222222222222';
  const staff = new Map();

  await withApi(async (request, response, rawBody) => {
    const body = rawBody ? JSON.parse(rawBody) : {};
    calls.push({ method: request.method, url: request.url, body, headers: request.headers });

    if (request.method === 'GET' && request.url === '/api/v1/auth/csrf') {
      return json(response, 200, { token: 'csrf-mask', headerName: 'X-XSRF-TOKEN' }, {
        'set-cookie': 'XSRF-TOKEN=csrf-cookie; Path=/; SameSite=Lax',
      });
    }
    if (request.method === 'POST' && request.url === '/api/v1/auth/register') {
      assert.equal(request.headers['x-xsrf-token'], 'csrf-mask');
      assert.equal(request.headers.cookie, 'XSRF-TOKEN=csrf-cookie');
      assert.match(body.organizationCode, /^[A-Za-z0-9_-]{3,64}$/);
      return json(response, 201, { organizationId: orgId });
    }
    if (request.method === 'POST' && request.url === '/api/v1/mobile/auth/login') {
      if (body.email === 'bootstrap@example.invalid') {
        return json(response, 200, { step: 'PASSWORD_CHANGE_REQUIRED' });
      }
      if (body.email === 'org-admin@example.invalid') {
        assert.equal(body.password, generatedPassword);
        return json(response, 200, { step: 'AUTHENTICATED', accessToken: 'org-admin-token' });
      }
      const credential = staff.get(body.email);
      assert.ok(credential, `Unexpected login for ${body.email}`);
      if (credential.temporaryPassword) {
        assert.equal(body.password, credential.temporaryPassword);
        return json(response, 200, { step: 'PASSWORD_CHANGE_REQUIRED' });
      }
      assert.equal(body.password, credential.password);
      return json(response, 200, { step: 'AUTHENTICATED', accessToken: `token:${body.email}` });
    }
    if (request.method === 'POST' && request.url === '/api/v1/mobile/auth/password/setup') {
      if (body.email === 'bootstrap@example.invalid') {
        return json(response, 200, { step: 'AUTHENTICATED', accessToken: 'platform-admin-token' });
      }
      const credential = staff.get(body.email);
      assert.ok(credential);
      assert.equal(body.currentPassword, credential.temporaryPassword);
      credential.password = body.password;
      credential.temporaryPassword = null;
      return json(response, 200, { step: 'AUTHENTICATED', accessToken: `token:${body.email}` });
    }
    if (request.method === 'POST' && request.url === `/api/v1/platform/organizations/${orgId}/subscriptions`) {
      assert.equal(request.headers.authorization, 'Bearer platform-admin-token');
      assert.equal(body.billingPeriodMonths, 1);
      return json(response, 201, { id: subscriptionId, organizationId: orgId });
    }
    if (request.method === 'POST' && request.url === '/api/v1/workforce/users') {
      assert.equal(request.headers.authorization, 'Bearer org-admin-token');
      const role = body.roles[0];
      const email = role === 'INSPECTOR' ? 'inspector@example.invalid' : 'engineer@example.invalid';
      const temporaryPassword = `temporary-${role}-password`;
      staff.set(email, { temporaryPassword });
      return json(response, 201, {
        user: { email, roles: body.roles, organizationId: orgId },
        temporaryPassword,
      });
    }
    response.writeHead(404);
    response.end();
  }, async (apiBaseUrl) => {
    const result = await bootstrapE2eEnvironment({
      apiBaseUrl,
      runId,
      bootstrapEmail: 'bootstrap@example.invalid',
      bootstrapPassword: 'initial-bootstrap-password',
      organizationAdminEmail: 'org-admin@example.invalid',
      passwordGenerator: (() => {
        const values = [
          generatedPassword,
          'new-platform-admin-password-9371',
          'new-inspector-password-1842',
          'new-engineer-password-3956',
        ];
        return () => values.shift();
      })(),
    });

    assert.equal(result.organizationId, orgId);
    assert.equal(result.subscriptionId, subscriptionId);
    assert.equal(result.platformAdmin.email, 'bootstrap@example.invalid');
    assert.equal(result.organizationAdmin.email, 'org-admin@example.invalid');
    assert.equal(result.inspector.email, 'inspector@example.invalid');
    assert.equal(result.maintenanceEngineer.email, 'engineer@example.invalid');
    assert.equal(result.inspector.password, 'new-inspector-password-1842');
    assert.equal(result.maintenanceEngineer.password, 'new-engineer-password-3956');
    for (const user of [result.inspector, result.maintenanceEngineer]) {
      assert.equal(staff.get(user.email).temporaryPassword, null);
      assert.equal(staff.get(user.email).password, user.password);
    }

    const registrationIndex = calls.findIndex((call) => call.url === '/api/v1/auth/register');
    const activationIndex = calls.findIndex((call) => call.url.endsWith('/subscriptions'));
    const provisioningIndices = calls
      .map((call, index) => call.url === '/api/v1/workforce/users' ? index : -1)
      .filter((index) => index >= 0);
    assert.ok(registrationIndex >= 0);
    assert.ok(activationIndex > registrationIndex);
    assert.equal(provisioningIndices.length, 2);
    assert.ok(provisioningIndices.every((index) => index > activationIndex));
    assert.equal(calls.some((call) => call.url.includes('/api/v1/internal/')), false);
  });
});

test('does not provision workforce when authorized subscription activation is refused', async () => {
  const requests = [];
  await withApi(async (request, response, rawBody) => {
    const body = rawBody ? JSON.parse(rawBody) : {};
    requests.push({ method: request.method, url: request.url, body });
    if (request.url === '/api/v1/auth/csrf') {
      return json(response, 200, { token: 'csrf-mask', headerName: 'X-XSRF-TOKEN' }, {
        'set-cookie': 'XSRF-TOKEN=csrf-cookie; Path=/; SameSite=Lax',
      });
    }
    if (request.url === '/api/v1/auth/register') return json(response, 201, { organizationId: 'org-1' });
    if (request.url === '/api/v1/mobile/auth/login') {
      return json(response, 200, { step: 'PASSWORD_CHANGE_REQUIRED' });
    }
    if (request.url === '/api/v1/mobile/auth/password/setup') {
      return json(response, 200, { step: 'AUTHENTICATED', accessToken: 'platform-admin-token' });
    }
    if (request.url.endsWith('/subscriptions')) {
      response.writeHead(403, { 'content-type': 'application/problem+json' });
      response.end(JSON.stringify({ status: 403, code: 'ACCESS_DENIED' }));
      return;
    }
    response.writeHead(404);
    response.end();
  }, async (apiBaseUrl) => {
    await assert.rejects(
      bootstrapE2eEnvironment({
        apiBaseUrl,
        runId,
        bootstrapEmail: 'bootstrap@example.invalid',
        bootstrapPassword: 'initial-bootstrap-password',
        organizationAdminEmail: 'org-admin@example.invalid',
        passwordGenerator: (() => {
        const values = [
          generatedPassword,
          'new-platform-admin-password-9371',
          'new-inspector-password-1842',
          'new-engineer-password-3956',
        ];
        return () => values.shift();
      })(),
      }),
      /Subscription activation failed \(403\)/,
    );
  });
  assert.equal(requests.some((request) => request.url === '/api/v1/workforce/users'), false);
});
