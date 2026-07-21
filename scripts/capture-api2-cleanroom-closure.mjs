import { createHash } from 'node:crypto'
import { existsSync, mkdirSync, readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

function arg(name, fallback = '') {
  const index = process.argv.indexOf(name)
  return index >= 0 && process.argv[index + 1] ? process.argv[index + 1] : fallback
}

function sha256(file) {
  return createHash('sha256').update(readFileSync(file)).digest('hex')
}

function walkFiles(root) {
  const files = []
  const visit = relative => {
    const absolute = path.join(root, relative)
    for (const entry of readdirSync(absolute, { withFileTypes: true })) {
      const next = relative ? path.join(relative, entry.name) : entry.name
      if (entry.isDirectory()) visit(next)
      else files.push(next.replace(/\\/g, '/'))
    }
  }
  visit('')
  return files.sort()
}

function resolveImport(root, importer, specifier) {
  const base = path.resolve(root, path.dirname(importer), specifier)
  const candidates = [base, `${base}.mjs`, `${base}.js`, `${base}.cjs`, `${base}.json`, path.join(base, 'index.mjs'), path.join(base, 'index.js')]
  return candidates.find(file => existsSync(file)) || null
}

function localSpecifier(value) {
  return value.startsWith('./') || value.startsWith('../')
}

function extractImports(source) {
  const imports = []
  const pattern = /(?:from\s*|import\s*\()\s*["']([^"']+)["']/g
  for (const match of source.matchAll(pattern)) imports.push(match[1])
  return [...new Set(imports)]
}

function extractPowerShellRefs(source) {
  const refs = []
  const pattern = /(?:['"])(tools[\\/][^'"\r\n]+?\.(?:mjs|js|json|ps1)|(?:package\.json|api2-deploy-manifest\.json|data[\\/]current-service-release\.json))(?:['"])/gi
  for (const match of source.matchAll(pattern)) refs.push(match[1].replace(/\\/g, '/'))
  return [...new Set(refs)]
}

function readJson(file) {
  if (!existsSync(file)) return { value: null, exists: false, parseable: false, error: 'missing' }
  try {
    return { value: JSON.parse(readFileSync(file, 'utf8')), exists: true, parseable: true, error: null }
  } catch (error) {
    return { value: null, exists: true, parseable: false, error: error instanceof Error ? error.message : String(error) }
  }
}

const runtimeInputSpecs = [
  { path: 'package.json', class: 'mandatory', source: 'tools/build-api2-package.mjs:22' },
  { path: 'data/live-baseline.json', class: 'mandatory', source: 'tools/serve.mjs:160' },
  { path: 'data/fbss-state.seed.json', class: 'mandatory', source: 'tools/serve.mjs:161' },
  { path: 'data/current-service-release.json', class: 'mandatory', source: 'tools/serve.mjs:164', overrideEnv: 'FBSS_CURRENT_SERVICE_RELEASE_PATH' },
  { path: 'data/workbuddy-upgrade-demand-queue.json', class: 'mandatory', source: 'tools/serve.mjs:165' },
  { path: 'data/fbs130-industry-starter-experiment.json', class: 'optional', source: 'tools/serve.mjs:174' },
  { path: 'data/fubangshou-backend-upgrade-contracts-20260617.json', class: 'optional', source: 'tools/serve.mjs:180' },
  { path: 'data/fbs130-test-accounts.local.json', class: 'optional', source: 'tools/serve.mjs:183' },
  { path: 'reports/api2-admin-capability-scan-latest.json', class: 'optional', source: 'tools/serve.mjs:178' },
  { path: 'reports/service-traction-hourly-client-analysis-latest.json', class: 'optional', source: 'tools/serve.mjs:1078' },
  { path: 'reports/fbs130-product-intelligence-ops-test-latest.json', class: 'optional', source: 'tools/serve.mjs:167' },
  { path: 'reports/fbs130-independent-client-behavior-tracking-latest.json', class: 'optional', source: 'tools/serve.mjs:169' },
  { path: 'reports/fbs130-workbuddy-ops-evidence-goal-status-latest.json', class: 'optional', source: 'tools/serve.mjs:170' },
  { path: 'reports/fbs130-industry-natural-watch-latest.json', class: 'optional', source: 'tools/serve.mjs:171' },
  { path: 'reports/fbs130-taskboard-alignment-latest.json', class: 'optional', source: 'tools/serve.mjs:172' },
  { path: 'reports/workbuddy-industry-131-expert-center-consistency-audit-latest.json', class: 'optional', source: 'tools/serve.mjs:173' },
  { path: 'reports/fbs130-ops-innovation-brief-latest.json', class: 'optional', source: 'tools/serve.mjs:166' },
  { path: 'reports/fbs130-realtime-attribution-ops-brief-latest.json', class: 'optional', source: 'tools/serve.mjs:168' },
  { path: 'reports/fbs130-natural-reactivation-packet-latest.json', class: 'optional', source: 'tools/serve.mjs:175' },
  { path: 'reports/fbs130-channel-expansion-packet-latest.json', class: 'optional', source: 'tools/serve.mjs:176' },
  { path: 'reports/fbs130-reactivation-frontstage-runbook-latest.json', class: 'optional', source: 'tools/serve.mjs:177' },
  { path: 'reports/fbs130-edge-backend-traversal-latest.json', class: 'optional', source: 'tools/serve.mjs:179' },
  { path: 'reports/fubangshou-backend-readonly-adapters-latest.json', class: 'optional', source: 'tools/serve.mjs:181' },
  { path: 'reports/fubangshou-backend-page-skeletons-latest.json', class: 'optional', source: 'tools/serve.mjs:182' }
]

const mutableInputSpecs = [
  { path: '.fbss-runtime-state.json', source: 'tools/serve.mjs:185', reason: 'runtime mutable state' },
  { path: 'data/host-forwarding-ack-challenges.json', source: 'tools/serve.mjs:188', reason: 'challenge and nonce replay state' },
  { path: 'data/host-forwarding-ack-challenges.json.lock', source: 'tools/serve.mjs:191', reason: 'challenge state lock' },
  { path: '/var/lib/fbss-phase1/runtime-state.json', source: 'tools/serve.mjs:185', reason: 'host runtime mutable state' },
  { path: '/var/log/nginx/access.log', source: 'tools/serve.mjs:205', reason: 'external access log' }
]

function inspectInput(root, spec) {
  const external = path.isAbsolute(spec.path) && !spec.path.startsWith(root)
  const absolute = external ? spec.path : path.join(root, spec.path)
  const exists = !external && existsSync(absolute) && statSync(absolute).isFile()
  return {
    path: spec.path,
    source: spec.source,
    overrideEnv: spec.overrideEnv || null,
    exists,
    bytes: exists ? statSync(absolute).size : null,
    sha256: exists ? sha256(absolute) : null,
    disposition: exists ? 'materialized' : 'runtime-input-not-materialized'
  }
}

function main() {
  const root = path.resolve(arg('--root', path.join(scriptRoot, 'work', 'api2-cleanroom', 'p1-005-active-20260722', 'source')))
  const outputPath = arg('--output', '')
  const manifestPath = path.resolve(root, arg('--manifest', 'root/api2-deploy-manifest.json'))
  const provenancePath = path.resolve(root, arg('--provenance', 'root/api2-candidate-provenance.json'))
  const roots = arg('--roots', 'tools/serve.mjs,tools/build-api2-package.mjs,tools/service-traction-strong-signature-normalizer-smoke.mjs')
    .split(',').map(value => value.trim()).filter(Boolean)
  const manifestRead = readJson(manifestPath)
  const provenanceRead = readJson(provenancePath)
  const manifest = manifestRead.value || {}
  const provenance = provenanceRead.value || {}
  const queue = [...roots]
  const visited = new Set()
  const present = []
  const missing = []
  const edges = []
  const external = []
  while (queue.length) {
    const relative = queue.shift().replace(/\\/g, '/')
    if (visited.has(relative)) continue
    visited.add(relative)
    const absolute = path.join(root, relative)
    if (!existsSync(absolute) || !statSync(absolute).isFile()) {
      missing.push({ importer: null, specifier: relative, resolved: relative })
      continue
    }
    const source = readFileSync(absolute, 'utf8')
    present.push({ path: relative, bytes: statSync(absolute).size, sha256: sha256(absolute) })
    for (const specifier of extractImports(source)) {
      if (!localSpecifier(specifier)) {
        external.push({ importer: relative, specifier })
        continue
      }
      const resolved = resolveImport(root, relative, specifier)
      const resolvedRelative = resolved ? path.relative(root, resolved).replace(/\\/g, '/') : null
      edges.push({ importer: relative, specifier, resolved: resolvedRelative })
      if (resolved) queue.push(resolvedRelative)
      else missing.push({ importer: relative, specifier, resolved: null })
    }
  }

  const powershellRoots = ['tools/deploy-api2-sidecar-clean-source.ps1', 'tools/deploy-api2-sidecar-release.ps1']
  const powershellRefs = []
  for (const relative of powershellRoots) {
    const absolute = path.join(root, relative)
    if (!existsSync(absolute)) {
      missing.push({ importer: relative, specifier: relative, resolved: null, kind: 'powershell-root' })
      continue
    }
    const source = readFileSync(absolute, 'utf8')
    for (const ref of extractPowerShellRefs(source)) {
      const absoluteRef = path.join(root, ref)
      const presentRef = existsSync(absoluteRef)
      powershellRefs.push({ importer: relative, reference: ref, present: presentRef })
      if (!presentRef) {
        const kind = /api2-deploy-manifest|package-freshness|direct-host-readonly-preflight/.test(ref)
          ? 'package-output-required'
          : 'powershell-reference'
        missing.push({ importer: relative, specifier: ref, resolved: null, kind })
      }
    }
  }

  const inventory = walkFiles(root)
  const expectedCriticalFiles = Array.isArray(manifest.sourceProvenance?.criticalDeployFiles)
    ? manifest.sourceProvenance.criticalDeployFiles
    : (Array.isArray(provenance.sourceProvenance?.criticalDeployFiles) ? provenance.sourceProvenance.criticalDeployFiles : [])
  const provenanceStatus = provenance.base?.gitProvenance?.status || 'missing'
  const manifestExpected = Number(manifest.sourceSelection?.criticalDeploySnapshot?.fileCount || 0)
  const snapshotHash = String(manifest.sourceSelection?.criticalDeploySnapshot?.sha256 || '').toLowerCase()
  const provenanceSnapshotHash = String(manifest.sourceProvenance?.criticalDeploySnapshotSha256 || '').toLowerCase()
  const manifestConsistency = {
    listPresent: expectedCriticalFiles.length > 0,
    fileCountMatches: manifestExpected > 0 && expectedCriticalFiles.length === manifestExpected,
    snapshotHashMatches: Boolean(snapshotHash) && snapshotHash === provenanceSnapshotHash
  }
  const criticalPresent = expectedCriticalFiles.filter(relative => existsSync(path.join(root, relative)))
    .map(relative => ({ path: relative, bytes: statSync(path.join(root, relative)).size, sha256: sha256(path.join(root, relative)) }))
  const criticalMissing = expectedCriticalFiles.filter(relative => !existsSync(path.join(root, relative))).map(pathValue => ({ path: pathValue }))
  const staticStatus = missing.length ? 'NO_GO_MISSING_LOCAL_IMPORTS_OR_DEPLOY_REFS' : 'PASS'
  const criticalStatus = !manifestRead.exists || !manifestRead.parseable || !manifestConsistency.listPresent
    ? 'NO_GO_MANIFEST_MISSING_OR_INVALID'
    : (!manifestConsistency.fileCountMatches || !manifestConsistency.snapshotHashMatches
      ? 'NO_GO_MANIFEST_PROVENANCE_DRIFT'
      : (criticalMissing.length || criticalPresent.length !== manifestExpected ? 'NO_GO_CRITICAL_INVENTORY_INCOMPLETE' : 'PASS'))
  const deployDecision = staticStatus === 'PASS' && criticalStatus === 'PASS' && provenanceStatus === 'verified'
    ? 'CANDIDATE_ONLY_REQUIRES_SIGNED_RELEASE_GATE'
    : 'NO_GO_READ_ONLY_CAPTURE_ONLY'
  const report = {
    schema: 'fbsir.api2.cleanroom.closure-manifest/v1',
    parser: 'heuristic-literal-import-regex/v1',
    observedAt: new Date().toISOString(),
    boundary: 'local_cleanroom_read_only_no_build_no_upload_no_release_write',
    root,
    manifestLoad: {
      path: manifestPath,
      exists: manifestRead.exists,
      parseable: manifestRead.parseable,
      hasCriticalList: expectedCriticalFiles.length > 0,
      error: manifestRead.error
    },
    provenanceLoad: {
      path: provenancePath,
      exists: provenanceRead.exists,
      parseable: provenanceRead.parseable,
      error: provenanceRead.error
    },
    roots,
    inventory: { fileCount: inventory.length, files: inventory },
    staticClosure: {
      status: staticStatus,
      presentFileCount: present.length,
      missingCount: missing.length,
      present,
      missing,
      edges,
      external,
      missingByKind: missing.reduce((counts, item) => {
        const kind = item.kind || (item.resolved === null ? 'local-import' : 'unknown')
        counts[kind] = (counts[kind] || 0) + 1
        return counts
      }, {}),
      dynamicUnresolved: ['runtime fetch/process.env and child-process boundaries are outside literal ESM closure']
    },
    powershellClosure: { roots: powershellRoots, references: powershellRefs },
    criticalInventory: {
      expectedCount: manifestExpected,
      presentCount: criticalPresent.length,
      missingCount: criticalMissing.length,
      present: criticalPresent,
      missing: criticalMissing,
      status: criticalStatus,
      manifestSha256: manifest.sourceSelection?.criticalDeploySnapshot?.sha256 || null,
      inventorySource: 'manifest.sourceProvenance.criticalDeployFiles'
    },
    manifestConsistency,
    provenance: {
      status: provenanceStatus,
      gitHead: provenance.base?.gitProvenance?.commit || null,
      tree: provenance.base?.gitProvenance?.tree || null,
      cannotProve: provenance.base?.gitProvenance?.cannotProve || null
    },
    runtimeMutableExcluded: provenance.base?.runtimeMutablePaths || provenance.runtimeMutablePaths || [],
    runtimeInputs: {
      mandatory: runtimeInputSpecs.filter(spec => spec.class === 'mandatory').map(spec => inspectInput(root, spec)),
      optional: runtimeInputSpecs.filter(spec => spec.class === 'optional').map(spec => inspectInput(root, spec)),
      specCoverage: {
        declaredServePathCount: runtimeInputSpecs.length,
        capturedSpecCount: runtimeInputSpecs.length,
        complete: true
      },
      mutableExcluded: mutableInputSpecs.map(spec => ({ ...inspectInput(root, spec), reason: spec.reason })),
      external: [
        { name: 'FBSS_MCP_UPSTREAM_URL', default: 'http://127.0.0.1:8001/mcp', source: 'tools/serve.mjs:196' },
        { name: 'FBSS_CONSOLE_AUTH_VALIDATE_URL', default: 'http://127.0.0.1:8080/getInfo', source: 'tools/serve.mjs:197' },
        { name: 'FBSS_API_EVENT_STRUCTURED_LOG_PATH', default: null, source: 'tools/serve.mjs:223' }
      ]
    },
    fullServicePackageClosure: staticStatus === 'PASS' && criticalStatus === 'PASS' ? 'not_proven_without_signed_build_receipt' : 'not_proven',
    deployDecision
  }
  const serialized = `${JSON.stringify(report, null, 2)}\n`
  if (outputPath) {
    const resolvedOutputPath = path.resolve(outputPath)
    mkdirSync(path.dirname(resolvedOutputPath), { recursive: true })
    writeFileSync(resolvedOutputPath, serialized, 'utf8')
  }
  process.stdout.write(serialized)
}

main()
