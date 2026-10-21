import { randomBytes } from 'node:crypto';

function baseUrl(value) {
  const url = new URL(value);
  if (url.protocol !== 'http:' && url.protocol !== 'https:') {
    throw new Error('E2E API URL must use HTTP or HTTPS');
  }
  return url.toString().replace(/\/$/, '');
}

function unwrap(response, label) {
  if (!response.ok) {
    throw new Error(`${label} failed (${response.status})`);
  }
  const payload = response.data;
  if (payload && typeof payload === 'object' && 'data' in payload) return payload.data;
  return payload;
}

function password() {
  return randomBytes(24).toString('base64url');
}

export async function waitForBackend(apiBaseUrl, { attempts = 60, delayMs = 2_000 } = {}) {
  const url = new URL('/actuator/health', baseUrl(apiBaseUrl));
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      const response = await fetch(url, { signal: AbortSignal.timeout(3_000) });
      if (response.ok) return;
    } catch {
      // The isolated services may need time to finish starting.
    }
    if (attempt < attempts - 1) {
      await new Promise((resolve) => setTimeout(resolve, delayMs));
    }
  }
  throw new Error(`Backend did not become healthy at ${url.origin}`);
}

async function apiRequest(base, path, options = {}) {
  const response = await fetch(`${base}${path}`, {
    ...options,
    headers: {
      accept: 'application/json',
      ...(options.body ? { 'content-type': 'application/json' } : {}),
      ...options.headers,
    },
  });
  let data = null;
  try {
    data = await response.json();
  } catch {
    // Keep a missing/invalid JSON response visible through the caller's validation.
  }
  return { ok: response.ok, status: response.status, data, headers: response.headers };
}

function setCookieValues(headers) {
  if (typeof headers.getSetCookie === 'function') return headers.getSetCookie();
  const cookie = headers.get('set-cookie');
  return cookie ? [cookie] : [];
}

async function browserPost(base, path, body, cookieJar) {
  const csrf = await apiRequest(base, '/auth/csrf');
  const csrfData = unwrap(csrf, 'CSRF token request');
  if (typeof csrfData?.token !== 'string' || !csrfData.token) {
    throw new Error('CSRF token response is incomplete');
  }
  const cookie = setCookieValues(csrf.headers)
    .map((value) => value.split(';', 1)[0])
    .filter((value) => value.startsWith('XSRF-TOKEN='))
    .join('; ');
  if (!cookie) throw new Error('CSRF token response is missing the XSRF-TOKEN cookie');
  if (cookie) cookieJar.value = cookie;
  const headerName = csrfData.headerName === 'X-CSRF-TOKEN' ? 'X-CSRF-TOKEN' : 'X-XSRF-TOKEN';
  return apiRequest(base, path, {
    method: 'POST',
    body: JSON.stringify(body),
    headers: {
      [headerName]: csrfData.token,
      ...(cookieJar.value ? { cookie: cookieJar.value } : {}),
    },
  });
}

async function changeInitialPassword(base, email, currentPassword, newPassword) {
  const response = await apiRequest(base, '/mobile/auth/password/setup', {
    method: 'POST',
    body: JSON.stringify({ email, currentPassword, password: newPassword }),
  });
  const auth = unwrap(response, 'Initial password setup');
  if (auth.step !== 'AUTHENTICATED' || typeof auth.accessToken !== 'string' || !auth.accessToken) {
    throw new Error('Initial password setup did not authenticate the account');
  }
  return { email, password: newPassword, accessToken: auth.accessToken };
}

async function prepareProvisionedUser(base, user, newPassword) {
  const firstLogin = await login(base, user.email, user.temporaryPassword);
  if (firstLogin.step !== 'PASSWORD_CHANGE_REQUIRED') {
    throw new Error(`Provisioned ${user.role} did not require an initial password change`);
  }
  const updated = await changeInitialPassword(
    base,
    user.email,
    user.temporaryPassword,
    newPassword,
  );
  return { email: user.email, password: updated.password, accessToken: updated.accessToken };
}

async function login(base, email, userPassword) {
  const response = await apiRequest(base, '/mobile/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password: userPassword }),
  });
  return unwrap(response, `Login for ${email}`);
}

async function provision(base, token, email, fullName, role) {
  const response = await apiRequest(base, '/workforce/users', {
    method: 'POST',
    headers: { authorization: `Bearer ${token}` },
    body: JSON.stringify({ email, fullName, roles: [role] }),
  });
  const result = unwrap(response, `Provision ${role}`);
  if (typeof result?.temporaryPassword !== 'string' || !result.temporaryPassword) {
    throw new Error(`Provision ${role} response is missing its temporary password`);
  }
  return {
    email: result.user.email,
    role,
    temporaryPassword: result.temporaryPassword,
  };
}

/** Creates the isolated organization and role credentials using application APIs only. */
export async function bootstrapE2eEnvironment({
  apiBaseUrl,
  runId,
  bootstrapEmail,
  bootstrapPassword,
  organizationAdminEmail,
  passwordGenerator = password,
}) {
  const base = baseUrl(apiBaseUrl);
  const cookieJar = { value: '' };
  const organizationCode = `E2E-${runId.replace(/[^a-z0-9]/gi, '').slice(-24)}`;
  const organizationAdminPassword = passwordGenerator();
  const platformAdminPassword = passwordGenerator();
  const registration = unwrap(
    await browserPost(
      base,
      '/auth/register',
      {
        email: organizationAdminEmail,
        fullName: 'E2E Organization Administrator',
        organizationName: `E2E Organization ${runId.slice(-12)}`,
        organizationCode,
        password: organizationAdminPassword,
      },
      cookieJar,
    ),
    'Organization registration',
  );
  if (typeof registration?.organizationId !== 'string') {
    throw new Error('Organization registration response is missing its id');
  }

  const initialAdminLogin = await login(base, bootstrapEmail, bootstrapPassword);
  if (initialAdminLogin.step !== 'PASSWORD_CHANGE_REQUIRED') {
    throw new Error('Bootstrap Platform ADMIN did not require the initial password change');
  }
  const platformAdmin = await changeInitialPassword(
    base,
    bootstrapEmail,
    bootstrapPassword,
    platformAdminPassword,
  );

  const subscription = unwrap(
    await apiRequest(base, `/platform/organizations/${registration.organizationId}/subscriptions`, {
      method: 'POST',
      headers: { authorization: `Bearer ${platformAdmin.accessToken}` },
      body: JSON.stringify({
        billingPeriodMonths: 1,
        externalReference: runId,
        reason: 'Disposable E2E organization entitlement',
      }),
    }),
    'Subscription activation',
  );
  if (subscription?.organizationId !== registration.organizationId) {
    throw new Error('Subscription response organization does not match the registered organization');
  }

  const organizationAdminLogin = await login(base, organizationAdminEmail, organizationAdminPassword);
  if (organizationAdminLogin.step !== 'AUTHENTICATED' || !organizationAdminLogin.accessToken) {
    throw new Error('Organization ADMIN did not authenticate after registration');
  }

  const inspectorEmail = `inspector-${runId.slice(-10)}@example.invalid`;
  const engineerEmail = `engineer-${runId.slice(-10)}@example.invalid`;
  const inspectorAccount = await provision(
    base,
    organizationAdminLogin.accessToken,
    inspectorEmail,
    'E2E Inspector',
    'INSPECTOR',
  );
  const maintenanceEngineerAccount = await provision(
    base,
    organizationAdminLogin.accessToken,
    engineerEmail,
    'E2E Maintenance Engineer',
    'MAINTENANCE_ENGINEER',
  );
  const inspector = await prepareProvisionedUser(base, inspectorAccount, passwordGenerator());
  const maintenanceEngineer = await prepareProvisionedUser(
    base,
    maintenanceEngineerAccount,
    passwordGenerator(),
  );

  return {
    organizationId: registration.organizationId,
    subscriptionId: subscription.id,
    platformAdmin,
    organizationAdmin: {
      email: organizationAdminEmail,
      password: organizationAdminPassword,
      accessToken: organizationAdminLogin.accessToken,
    },
    inspector,
    maintenanceEngineer,
  };
}
