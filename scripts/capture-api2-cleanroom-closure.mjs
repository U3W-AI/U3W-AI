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
  return existsSync(file) ? JSON.parse(readFileSync(file, 'utf8')) : null
}

function main() {
  const root = path.resolve(arg('--root', path.join(scriptRoot, 'work', 'api2-cleanroom', 'p1-005-active-20260722', 'source')))
  const outputPath = arg('--output', '')
  const manifestPath = path.resolve(root, arg('--manifest', 'root/api2-deploy-manifest.json'))
  const provenancePath = path.resolve(root, arg('--provenance', 'root/api2-candidate-provenance.json'))
  const roots = arg('--roots', 'tools/serve.mjs,tools/build-api2-package.mjs,tools/service-traction-strong-signature-normalizer-smoke.mjs')
    .split(',').map(value => value.trim()).filter(Boolean)
  const manifest = readJson(manifestPath) || {}
  const provenance = readJson(provenancePath) || {}
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
      if (!presentRef) missing.push({ importer: relative, specifier: ref, resolved: null, kind: 'powershell-reference' })
    }
  }

  const inventory = walkFiles(root)
  const expectedCriticalFiles = Array.isArray(manifest.sourceProvenance?.criticalDeployFiles)
    ? manifest.sourceProvenance.criticalDeployFiles
    : (Array.isArray(provenance.sourceProvenance?.criticalDeployFiles) ? provenance.sourceProvenance.criticalDeployFiles : [])
  const criticalPresent = expectedCriticalFiles.filter(relative => existsSync(path.join(root, relative)))
  const criticalMissing = expectedCriticalFiles.filter(relative => !existsSync(path.join(root, relative)))
  const provenanceStatus = provenance.base?.gitProvenance?.status || 'missing'
  const manifestExpected = Number(manifest.sourceSelection?.criticalDeploySnapshot?.fileCount || expectedCriticalFiles.length || 0)
  const staticStatus = missing.length ? 'NO_GO_MISSING_LOCAL_IMPORTS_OR_DEPLOY_REFS' : 'PASS'
  const criticalStatus = manifestExpected > 0 && expectedCriticalFiles.length === 0
    ? 'NO_GO_CRITICAL_INVENTORY_LIST_MISSING'
    : (criticalMissing.length || criticalPresent.length !== manifestExpected ? 'NO_GO_CRITICAL_INVENTORY_INCOMPLETE' : 'PASS')
  const deployDecision = staticStatus === 'PASS' && criticalStatus === 'PASS' && provenanceStatus === 'verified'
    ? 'CANDIDATE_ONLY_REQUIRES_SIGNED_RELEASE_GATE'
    : 'NO_GO_READ_ONLY_CAPTURE_ONLY'
  const report = {
    schema: 'fbsir.api2.cleanroom.closure-manifest/v1',
    parser: 'heuristic-literal-import-regex/v1',
    observedAt: new Date().toISOString(),
    boundary: 'local_cleanroom_read_only_no_build_no_upload_no_release_write',
    root,
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
      manifestSha256: manifest.sourceSelection?.criticalDeploySnapshot?.sha256 || null
    },
    provenance: {
      status: provenanceStatus,
      gitHead: provenance.base?.gitProvenance?.commit || null,
      tree: provenance.base?.gitProvenance?.tree || null,
      cannotProve: provenance.base?.gitProvenance?.cannotProve || null
    },
    runtimeMutableExcluded: provenance.base?.runtimeMutablePaths || provenance.runtimeMutablePaths || [],
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
