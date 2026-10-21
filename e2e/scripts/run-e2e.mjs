import { execFileSync } from 'node:child_process';
import {
  chmodSync,
  existsSync,
  mkdirSync,
  readFileSync,
  writeFileSync,
} from 'node:fs';
import { randomBytes } from 'node:crypto';
import { bootstrapE2eEnvironment, waitForBackend } from './bootstrap-api.mjs';
import { tmpdir } from 'node:os';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const backendRoot = resolve(scriptDir, '../..');
const composeFile = resolve(backendRoot, 'e2e/compose.e2e.yml');
const frontendRoot = resolve(backendRoot, '../sdi-mf1-mf4-frontend');
const runStore = resolve(tmpdir(), 'sdi-mf1-mf4-e2e-runs');
const runIdPattern = /^sdi-e2e-[a-z0-9-]{8,48}$/;

function run(command, args, options = {}) {
  const output = execFileSync(command, args, {
    encoding: 'utf8',
    stdio: options.stdio ?? ['ignore', 'pipe', 'pipe'],
    env: options.env ?? process.env,
  });
  return typeof output === 'string' ? output.trim() : '';
}

function projectName(runId) {
  return runId;
}

function composeArgs(runId, envFile, command) {
  return [
    'compose',
    '-p',
    projectName(runId),
    '-f',
    composeFile,
    '--env-file',
    envFile,
    ...command,
  ];
}

function readEnvFile(path) {
  return Object.fromEntries(
    readFileSync(path, 'utf8')
      .split(/\r?\n/)
      .filter((line) => line && !line.trimStart().startsWith('#'))
      .map((line) => {
        const separator = line.indexOf('=');
        return [line.slice(0, separator), line.slice(separator + 1)];
      }),
  );
}

function readManifest(runId) {
  const envFile = resolve(runStore, `${runId}.env`);
  const manifestFile = resolve(runStore, `${runId}.json`);
  if (!existsSync(envFile) || !existsSync(manifestFile)) {
    throw new Error(`No prepared run manifest/environment exists for ${runId}`);
  }
  const manifest = JSON.parse(readFileSync(manifestFile, 'utf8'));
  if (
    manifest.runId !== runId ||
    manifest.projectName !== projectName(runId) ||
    manifest.composeFile !== composeFile
  ) {
    throw new Error('Run manifest does not authorize this compose project');
  }
  return { envFile, manifestFile, manifest, env: { ...process.env, ...readEnvFile(envFile) } };
}

function freeLoopbackPort() {
  const port = Number(run('node', [
    '-e',
    "const s=require('node:net').createServer();s.listen(0,'127.0.0.1',()=>{console.log(s.address().port);s.close()})",
  ]));
  if (!Number.isInteger(port) || port < 1024) throw new Error('Could not reserve a valid loopback port');
  return port;
}

function randomSecret(bytes = 32) {
  return randomBytes(bytes).toString('base64url');
}

export function composeProjectLabels(containerRows) {
  const projectNames = new Set();
  for (const row of containerRows) {
    const labels = row?.Labels ?? row?.labels;
    if (typeof labels !== 'string' || !labels) continue;
    for (const label of labels.split(',')) {
      const [key, ...parts] = label.split('=');
      if (key === 'com.docker.compose.project' && parts.length > 0) {
        projectNames.add(parts.join('='));
      }
    }
  }
  return [...projectNames].sort((left, right) => left.localeCompare(right));
}

function existingProjectSnapshot() {
  const containerJson = run('docker', [
    'ps', '--format', '{{json .}}',
  ]);
  const rows = containerJson ? containerJson.split(/\r?\n/).map((line) => JSON.parse(line)) : [];
  const projectNames = composeProjectLabels(rows);
  const protectedProjects = projectNames.filter((name) => name.startsWith('smartdroneinspection-'));
  const containers = rows
    .filter((row) => protectedProjects.includes(composeProjectLabels([row])[0]))
    .map((row) => `${row.ID} ${row.Names} ${row.Ports}`)
    .sort((left, right) => left.localeCompare(right));
  const volumeNames = run('docker', ['volume', 'ls', '--format', '{{.Name}}'])
    .split(/\r?\n/)
    .filter(Boolean);
  const volumes = volumeNames
    .filter((name) => protectedProjects.some((project) => name.startsWith(`${project}_`)))
    .sort((left, right) => left.localeCompare(right));
  return { projects: protectedProjects, containers, volumes };
}

function start(runId) {
  if (!runIdPattern.test(runId)) throw new Error('Invalid E2E run id');
  mkdirSync(runStore, { recursive: true });
  const envFile = resolve(runStore, `${runId}.env`);
  const manifestFile = resolve(runStore, `${runId}.json`);
  if (existsSync(envFile) || existsSync(manifestFile)) {
    throw new Error(`Run ${runId} already has local state; choose a fresh run id`);
  }
  const backendPort = freeLoopbackPort();
  const webPort = freeLoopbackPort();
  if (backendPort === webPort) throw new Error('Could not allocate distinct E2E host ports');
  const env = {
    E2E_PROJECT: projectName(runId),
    FRONTEND_CONTEXT: frontendRoot,
    E2E_BACKEND_PORT: String(backendPort),
    E2E_WEB_PORT: String(webPort),
    POSTGRES_DB: `${runId.replaceAll('-', '_')}`,
    POSTGRES_PASSWORD: randomSecret(),
    MINIO_ROOT_USER: `e2e_${runId.slice(-10).replaceAll('-', '')}`,
    MINIO_ROOT_PASSWORD: randomSecret(),
    MINIO_BUCKET: `evidence-${runId.slice(-10).replaceAll('-', '')}`,
    AUTH_JWT_SECRET: randomSecret(),
    AUTH_REFRESH_TOKEN_PEPPER: randomSecret(),
    AUTH_BOOTSTRAP_ENABLED: 'true',
    AUTH_BOOTSTRAP_EMAIL: `admin-${runId.slice(-10).replaceAll('-', '')}@example.invalid`,
    AUTH_BOOTSTRAP_PASSWORD: randomSecret(),
    E2E_ORG_ADMIN_EMAIL: `org-admin-${runId.slice(-10).replaceAll('-', '')}@example.invalid`,
    E2E_ALLOWED_WORKFORCE_ROLES: 'INSPECTOR,MAINTENANCE_ENGINEER',
    AUTH_ALLOWED_ORIGINS: `http://127.0.0.1:${webPort},http://localhost:${webPort}`,
  };
  const before = existingProjectSnapshot();
  writeFileSync(envFile, `${Object.entries(env).map(([key, value]) => `${key}=${value}`).join('\n')}\n`, {
    mode: 0o600,
    flag: 'wx',
  });
  const existingSnapshotFile = resolve(runStore, `${runId}.existing-containers.json`);
  writeFileSync(existingSnapshotFile, `${JSON.stringify(before, null, 2)}\n`, {
    mode: 0o600,
    flag: 'wx',
  });
  chmodSync(existingSnapshotFile, 0o600);
  chmodSync(envFile, 0o600);
  const manifest = {
    runId,
    projectName: projectName(runId),
    composeFile,
    createdAt: new Date().toISOString(),
    hostPorts: { backend: backendPort, web: webPort },
    existingProjectSnapshot: before,
    servicesStarted: false,
  };
  writeFileSync(manifestFile, `${JSON.stringify(manifest, null, 2)}\n`, { mode: 0o600, flag: 'wx' });
  chmodSync(manifestFile, 0o600);

  run('docker', composeArgs(runId, envFile, ['config']), { env: { ...process.env, ...env } });
  process.stdout.write(`Prepared isolated E2E run ${runId}.\n`);
  process.stdout.write(`E2E_BACKEND_URL=http://127.0.0.1:${backendPort}/api/v1\n`);
  process.stdout.write(`E2E_WEB_URL=http://127.0.0.1:${webPort}\n`);
  process.stdout.write(`E2E_FRONTEND_CONTEXT=${frontendRoot}\n`);
  process.stdout.write(`E2E_ORG_ADMIN_EMAIL=${env.E2E_ORG_ADMIN_EMAIL}\n`);
  process.stdout.write('Services are not started. Review the isolation test before invoking start-services.\n');
}

function assertRunIsolated(runId, state) {
  const now = existingProjectSnapshot();
  if (JSON.stringify(now) !== JSON.stringify(state.manifest.existingProjectSnapshot)) {
    throw new Error('Existing smartdroneinspection-backend containers/volumes changed during the E2E run; refusing cleanup.');
  }
  const ids = run('docker', ['ps', '-aq', '--filter', `label=com.docker.compose.project=${projectName(runId)}`]);
  return ids ? ids.split(/\r?\n/).filter(Boolean) : [];
}

function writeProtectedEnvFile(path, env) {
  const lines = Object.entries(env).map(([key, value]) => `${key}=${value}`);
  const contents = `${lines.join('\n')}\n`;
  writeFileSync(path, contents, { mode: 0o600 });
  chmodSync(path, 0o600);
}

function persistBootstrapCredentials(state, bootstrap) {
  const env = readEnvFile(state.envFile);
  Object.assign(env, {
    E2E_PLATFORM_ADMIN_EMAIL: bootstrap.platformAdmin.email,
    E2E_PLATFORM_ADMIN_PASSWORD: bootstrap.platformAdmin.password,
    E2E_ORG_ADMIN_PASSWORD: bootstrap.organizationAdmin.password,
    E2E_INSPECTOR_EMAIL: bootstrap.inspector.email,
    E2E_INSPECTOR_PASSWORD: bootstrap.inspector.password,
    E2E_MAINTENANCE_ENGINEER_EMAIL: bootstrap.maintenanceEngineer.email,
    E2E_MAINTENANCE_ENGINEER_PASSWORD: bootstrap.maintenanceEngineer.password,
  });
  writeProtectedEnvFile(state.envFile, env);
}

function bootstrapSummary(bootstrap) {
  return {
    organizationId: bootstrap.organizationId,
    subscriptionId: bootstrap.subscriptionId,
    accounts: {
      platformAdmin: bootstrap.platformAdmin.email,
      organizationAdmin: bootstrap.organizationAdmin.email,
      inspector: bootstrap.inspector.email,
      maintenanceEngineer: bootstrap.maintenanceEngineer.email,
    },
  };
}

function removeServices(runId, state) {
  const ownedIds = run('docker', ['ps', '-aq', '--filter', `label=com.docker.compose.project=${projectName(runId)}`]);
  if (ownedIds) {
    run('docker', composeArgs(runId, state.envFile, ['down', '--volumes', '--remove-orphans']), {
      env: state.env,
      stdio: 'inherit',
    });
  }
}

async function startServices(runId) {
  const state = readManifest(runId);
  if (state.manifest.servicesStarted) throw new Error(`Services already started for ${runId}`);
  const env = { ...state.env };
  run('docker', composeArgs(runId, state.envFile, ['up', '-d', '--build']), { env, stdio: 'inherit' });
  const before = state.manifest.existingProjectSnapshot;
  const now = existingProjectSnapshot();
  if (JSON.stringify(now) !== JSON.stringify(before)) {
    throw new Error('Existing project resources changed while starting E2E; do not continue.');
  }
  const updated = { ...state.manifest, servicesStarted: true };
  writeFileSync(state.manifestFile, `${JSON.stringify(updated, null, 2)}\n`, { mode: 0o600 });
  process.stdout.write(`E2E services started for ${runId}.\n`);
  process.stdout.write(`Backend API: http://127.0.0.1:${updated.hostPorts.backend}/api/v1\n`);
  process.stdout.write(`Web: http://127.0.0.1:${updated.hostPorts.web}\n`);
  process.stdout.write(`Flutter Web API base: http://127.0.0.1:${updated.hostPorts.backend}/api/v1\n`);
  process.stdout.write('Bootstrap credentials are stored only in the run-local protected env file; do not print them.\n');
  const apiBaseUrl = `http://127.0.0.1:${updated.hostPorts.backend}/api/v1`;
  await waitForBackend(apiBaseUrl);
  const bootstrap = await bootstrapE2eEnvironment({
    apiBaseUrl,
    runId,
    bootstrapEmail: env.AUTH_BOOTSTRAP_EMAIL,
    bootstrapPassword: env.AUTH_BOOTSTRAP_PASSWORD,
    organizationAdminEmail: env.E2E_ORG_ADMIN_EMAIL,
  });
  persistBootstrapCredentials(state, bootstrap);
  const completed = { ...updated, bootstrap: bootstrapSummary(bootstrap) };
  writeFileSync(state.manifestFile, `${JSON.stringify(completed, null, 2)}\n`, { mode: 0o600 });
  process.stdout.write('E2E organization, subscription and workforce accounts bootstrapped through authorized APIs.\n');
}

function stopServices(runId) {
  const state = readManifest(runId);
  if (!state.manifest.servicesStarted) throw new Error(`Refusing stop: services were not recorded as started for ${runId}`);
  const ownedIds = assertRunIsolated(runId, state);
  if (ownedIds.length === 0) throw new Error(`No containers are labeled as owned by ${runId}; refusing broad cleanup`);
  run('docker', composeArgs(runId, state.envFile, ['down', '--volumes', '--remove-orphans']), {
    env: state.env,
    stdio: 'inherit',
  });
  const remaining = run('docker', ['ps', '-aq', '--filter', `label=com.docker.compose.project=${projectName(runId)}`]);
  if (remaining) throw new Error(`E2E containers remain after scoped cleanup for ${runId}`);
  assertRunIsolated(runId, state);
  process.stdout.write(`Stopped only E2E project ${runId}; existing project identities are unchanged.\n`);
}

function isCliEntryPoint() {
  return process.argv[1] != null && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
}

if (isCliEntryPoint()) {
  const [action, requestedRunId] = process.argv.slice(2);
  if (!['prepare', 'start-services', 'stop-services'].includes(action ?? '')) {
    process.stderr.write('Usage: node e2e/scripts/run-e2e.mjs <prepare|start-services|stop-services> sdi-e2e-<run-id>\n');
    process.exitCode = 2;
  } else {
    try {
      const runId = requestedRunId;
      if (!runIdPattern.test(runId ?? '')) throw new Error('Provide an explicit sdi-e2e-<run-id>');
      if (action === 'prepare') start(runId);
      else if (action === 'start-services') await startServices(runId);
      else stopServices(runId);
    } catch (error) {
      process.stderr.write(`${error.message}\n`);
      process.exitCode = 1;
    }
  }
}
