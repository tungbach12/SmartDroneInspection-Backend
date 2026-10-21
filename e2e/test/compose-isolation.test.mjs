import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { composeProjectLabels } from '../scripts/run-e2e.mjs';

const directory = dirname(fileURLToPath(import.meta.url));
const backendRoot = resolve(directory, '../..');
const composeFile = resolve(backendRoot, 'e2e/compose.e2e.yml');
const currentCompose = resolve(backendRoot, 'docker-compose.yml');
const expectedFrontendContext = resolve(backendRoot, '../sdi-mf1-mf4-frontend');
const testRunId = 'sdi-e2e-isolation-test';
const forbiddenPorts = new Set([80, 5432, 8000, 8080, 9000, 9001]);
const runIdPattern = /^sdi-e2e-[a-z0-9-]{8,48}$/;
const runner = resolve(backendRoot, 'e2e/scripts/run-e2e.mjs');

function composeConfig(file, project, extraEnv = {}) {
  return JSON.parse(
    execFileSync(
      'docker',
      ['compose', '-f', file, '-p', project, 'config', '--format', 'json'],
      {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
        env: { ...process.env, ...extraEnv },
      },
    ),
  );
}

test('MF1-MF4 E2E compose is isolated from the existing local stack', () => {
  const existing = composeConfig(currentCompose, 'smartdroneinspection-backend');
  const e2e = composeConfig(composeFile, testRunId, {
    E2E_PROJECT: testRunId,
    E2E_BACKEND_PORT: '40181',
    E2E_WEB_PORT: '40182',
    FRONTEND_CONTEXT: expectedFrontendContext,
    POSTGRES_DB: 'sdi_e2e_isolation_test',
    POSTGRES_PASSWORD: 'isolation-test-only-password',
    MINIO_ROOT_USER: 'e2e_isolation_test',
    MINIO_ROOT_PASSWORD: 'isolation-test-only-minio-password',
    MINIO_BUCKET: 'evidence-isolation-test',
    AUTH_JWT_SECRET: 'isolation-test-only-jwt-secret',
    AUTH_REFRESH_TOKEN_PEPPER: 'isolation-test-only-refresh-pepper',
    AUTH_BOOTSTRAP_EMAIL: 'admin-isolation@example.invalid',
    AUTH_BOOTSTRAP_PASSWORD: 'isolation-test-only-admin-password',
    AUTH_ALLOWED_ORIGINS: 'http://127.0.0.1:40182,http://127.0.0.1:40183',
  });
  const existingPorts = new Set(
    Object.values(existing.services ?? {}).flatMap((service) =>
      (service.ports ?? []).map((port) => Number(port.published)),
    ),
  );
  assert.equal(e2e.services.backend.ports?.[0]?.published, '40181');
  assert.equal(e2e.services.web.ports?.[0]?.published, '40182');
  for (const port of [40181, 40182]) {
    assert.ok(!existingPorts.has(port), `E2E cannot publish host port ${port} used by the existing project`);
  }

  assert.ok(e2e.services?.postgres, 'E2E must define its own Postgres service');
  assert.ok(e2e.services?.minio, 'E2E must define its own MinIO service');
  assert.ok(e2e.services?.backend, 'E2E must define its own backend service');
  assert.ok(e2e.services?.web, 'E2E must define its own web service');

  for (const [serviceName, service] of Object.entries(e2e.services)) {
    assert.equal(service.container_name, undefined, `${serviceName} must not set container_name`);
    for (const published of service.ports ?? []) {
      assert.ok(
        !forbiddenPorts.has(Number(published.published)),
        `${serviceName} must not claim existing/default host port ${published.published}`,
      );
    }
    if (serviceName === 'web') {
      assert.equal(
        resolve(backendRoot, service.build?.context ?? ''),
        expectedFrontendContext,
        'E2E web must build from the isolated sibling Frontend worktree',
      );
    }
  }

  const existingVolumes = new Set(Object.values(existing.volumes ?? {}).map((volume) => volume.name));
  assert.ok(Object.keys(e2e.volumes ?? {}).length > 0, 'E2E must use disposable named volumes');
  for (const [key, volume] of Object.entries(e2e.volumes ?? {})) {
    assert.ok(!existingVolumes.has(volume.name), `E2E volume ${key} must not reuse an existing volume`);
    assert.ok(
      volume.name.startsWith('sdi-e2e-isolation-test-'),
      `E2E volume ${key} must be run-scoped`,
    );
  }

  const existingNetworks = new Set(Object.values(existing.networks ?? {}).map((network) => network.name));
  assert.ok(Object.keys(e2e.networks ?? {}).length > 0, 'E2E must use a project-scoped network');
  for (const [key, network] of Object.entries(e2e.networks ?? {})) {
    assert.ok(!existingNetworks.has(network.name), `E2E network ${key} must not reuse an existing network`);
  }

  const after = composeConfig(currentCompose, 'smartdroneinspection-backend');
  assert.deepEqual(after, existing, 'resolving the E2E model must not alter existing compose resources');
});


test('run-scoped compose project ids stay within the supported naming bound', () => {
  const runId = `sdi-e2e-${'a'.repeat(48)}`;
  assert.ok(runId.length <= 56);
  assert.match(runId, runIdPattern);
  assert.doesNotMatch(`sdi-e2e-${'a'.repeat(49)}`, runIdPattern);
});

test('existing compose projects are discovered only from exact Docker labels', () => {
  assert.deepEqual(
    composeProjectLabels([
      { Labels: 'com.docker.compose.project=smartdroneinspection-backend,other=value' },
      { Labels: 'com.docker.compose.project=sdi-e2e-old' },
      { Labels: 'com.docker.compose.project-extra=smartdroneinspection-evil' },
      { Names: 'smartdroneinspection-postgres' },
      { Labels: '' },
    ]).filter((name) => name.startsWith('smartdroneinspection-')),
    ['smartdroneinspection-backend'],
  );
});

test('E2E runner rejects a non-run-scoped project id before invoking Docker', () => {
  const result = spawnSync(process.execPath, [runner, 'stop-services', 'smartdroneinspection-backend'], {
    encoding: 'utf8',
  });

  assert.equal(result.status, 1);
  assert.match(result.stderr, /Provide an explicit sdi-e2e-<run-id>/);
  assert.equal(result.stdout, '');
});
