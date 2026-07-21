import { createHash } from 'node:crypto'
import { cpSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(__dirname, '..')
const defaultBaseRoot = path.join(repoRoot, 'work', 'api2-cleanroom', 'p1-005-active-20260722', 'source')
const defaultOutputRoot = path.join(repoRoot, 'work', 'api2-cleanroom', 'p1-005-candidate-20260722', 'source')
const EXPECTED_BASE_SHA256 = {
  'src/service-traction-product-signature-normalizer.js': '1e05ac0be0eaafd8517f99766cc1a8da2479cc4d17c3238f2d3fa63cff3abbef',
  'src/service-traction-observability.js': 'f61f6de1b1114dc1143cb191edfb6aa5cbf399a4af146d7acf0e2eec3b9e8786'
}
const REQUIRED_RELATIVE_FILES = [
  'src/fbss-listed-runtime-catalog.js',
  'src/service-traction-traffic-classification.js',
  'src/service-traction-product-signature-normalizer.js',
  'src/service-traction-observability.js'
]

function arg(name, fallback) {
  const index = process.argv.indexOf(name)
  return index >= 0 && process.argv[index + 1] ? process.argv[index + 1] : fallback
}

function sha256(file) {
  return createHash('sha256').update(readFileSync(file)).digest('hex')
}

function replaceOnce(source, needle, replacement, label) {
  const index = source.indexOf(needle)
  if (index < 0) throw new Error(`patch_anchor_missing:${label}`)
  if (source.indexOf(needle, index + needle.length) >= 0) throw new Error(`patch_anchor_ambiguous:${label}`)
  return `${source.slice(0, index)}${replacement}${source.slice(index + needle.length)}`
}

function writeUtf8(file, content) {
  mkdirSync(path.dirname(file), { recursive: true })
  writeFileSync(file, content, 'utf8')
}

function patchNormalizer(source) {
  source = replaceOnce(source,
    "} from './fbss-listed-runtime-catalog.js'\n",
    "} from './fbss-listed-runtime-catalog.js'\nimport { isExactIndependentBoardIdentity, registrationGateForRow } from './fbss-independent-board-product-registration.js'\n",
    'normalizer_import')
  source = replaceOnce(source,
    "const PRODUCT_RULES = [\n",
    "const PRODUCT_RULES = [\n  {\n    productId: 'fbsir_eight_seat_board',\n    aliases: ['fbsir-eight-seat-board'],\n    requireDirectIdentity: true,\n    independentBoardExact: true\n  },\n",
    'normalizer_rule')
  source = replaceOnce(source,
    "function ruleMatches(row = {}, rule = {}) {\n  const values = signatureValues(row)\n  if (!includesAny(values, rule.aliases)) return false\n",
    "function ruleMatches(row = {}, rule = {}) {\n  const values = signatureValues(row)\n  if (rule.independentBoardExact && !isExactIndependentBoardIdentity(row)) return false\n  if (!includesAny(values, rule.aliases)) return false\n",
    'normalizer_exact_gate')
  source = replaceOnce(source,
    "  return {\n    productSignatureProductId: productId,\n",
    "  const registration = registrationGateForRow({ productSignatureProductId: productId })\n  return {\n    productSignatureProductId: productId,\n",
    'normalizer_result_registration')
  source = replaceOnce(source,
    "    productSignatureCannotPromoteProductCreditReason: reason\n",
    "    productSignatureCannotPromoteProductCreditReason: reason,\n    productRegistrationStatus: registration.registrationStatus,\n    productRegistrationAuthority: registration.registrationAuthority,\n    productAttributionMode: registration.attributionMode,\n    productRegistrationGateReason: registration.reason\n",
    'normalizer_result_fields')
  source = replaceOnce(source,
    "    productSignatureProductCreditCandidate: productCreditCandidate,\n",
    "    productSignatureProductCreditCandidate: registration.isIndependentBoard ? false : productCreditCandidate,\n    productSignatureReportOnlyCandidate: registration.isIndependentBoard && productCreditCandidate,\n",
    'normalizer_report_only_candidate')
  return source
}

function patchObservability(source) {
  source = replaceOnce(source,
    "} from './service-traction-traffic-classification.js'\n",
    "} from './service-traction-traffic-classification.js'\nimport { registrationGateForRow } from './fbss-independent-board-product-registration.js'\n",
    'observability_import')
  source = replaceOnce(source,
    "  const productCreditCandidate = rowFamily === 'natural_product_candidate' && strongProductSignature\n",
    "  const productCreditCandidate = rowFamily === 'natural_product_candidate' &&\n    strongProductSignature &&\n    row.productSignatureProductCreditCandidate !== false\n",
    'observability_candidate_gate')
  source = replaceOnce(source,
    "  const hostForwardingAck = strictHostForwardingAck(row)\n  const productCreditEligible = productCreditCandidate && hostForwardingAck?.verified === true\n",
    "  const hostForwardingAck = strictHostForwardingAck(row)\n  const registrationGate = registrationGateForRow({\n    productSignatureProductId: row.productSignatureProductId || row.productId,\n    productVersion: row.productVersion || row.runtimeProductVersion || row.listedProductVersion || row.packageVersion\n  })\n  const productCreditEligible = productCreditCandidate &&\n    hostForwardingAck?.verified === true &&\n    registrationGate.authoritativeCreditEnabled === true\n",
    'observability_credit_gate')
  source = replaceOnce(source,
    "    productCreditProvenanceState: hostForwardingAck?.state || 'verified_host_ack_missing',\n    productCreditProvenanceReason: hostForwardingAck?.reason || (productCreditCandidate ? 'verified_host_ack_and_server_anchor_required' : ''),\n",
    "    productCreditProvenanceState: hostForwardingAck?.state || 'verified_host_ack_missing',\n    productCreditProvenanceReason: registrationGate.reason || hostForwardingAck?.reason || (productCreditCandidate ? 'verified_host_ack_and_server_anchor_required' : ''),\n    productRegistrationStatus: registrationGate.registrationStatus,\n    productRegistrationAuthority: registrationGate.registrationAuthority,\n    productAttributionMode: registrationGate.attributionMode,\n    productRegistrationGateReason: registrationGate.reason,\n",
    'observability_registration_fields')
  source = replaceOnce(source,
    "  const trace = normalizeTraceCorrelationFields(row)\n\n  return compactObject({\n",
    "  const trace = normalizeTraceCorrelationFields(row)\n  const sampleCount = Math.max(1, numberValue(row.sampleCount || row.rowCount || 1))\n\n  return compactObject({\n",
    'observability_sample_weight')
  source = replaceOnce(source,
    "    sampleCount: Math.max(1, numberValue(row.sampleCount || row.rowCount || 1)),\n",
    "    sampleCount,\n    productNaturalDenominatorWeight: registrationGate.isIndependentBoard ? 0 : sampleCount,\n",
    'observability_denominator_weight')
  return source
}

function importClosure(root, relative, visited = new Set()) {
  const normalized = relative.replace(/\\/g, '/')
  if (visited.has(normalized)) return
  visited.add(normalized)
  const file = path.join(root, normalized)
  if (!existsSync(file)) throw new Error(`candidate_module_missing:${normalized}`)
  const source = readFileSync(file, 'utf8')
  const imports = [...source.matchAll(/from\s+['"](\.\.?\/[^'"]+)['"]/g)].map(match => match[1])
  for (const specifier of imports) {
    const target = path.normalize(path.join(path.dirname(normalized), specifier)).replace(/\\/g, '/')
    importClosure(root, target, visited)
  }
  return visited
}

function main() {
  const baseRoot = path.resolve(arg('--base-root', defaultBaseRoot))
  const outputRoot = path.resolve(arg('--output-root', defaultOutputRoot))
  for (const relative of REQUIRED_RELATIVE_FILES) {
    const file = path.join(baseRoot, relative)
    if (!existsSync(file)) throw new Error(`base_file_missing:${relative}`)
    const expected = EXPECTED_BASE_SHA256[relative]
    if (expected && sha256(file) !== expected) throw new Error(`base_sha256_drift:${relative}`)
  }
  cpSync(baseRoot, outputRoot, { recursive: true, force: true })
  const registrationSource = path.join(repoRoot, 'candidates', 'api2', 'p1-005', 'src', 'fbss-independent-board-product-registration.js')
  writeUtf8(path.join(outputRoot, 'src', 'fbss-independent-board-product-registration.js'), readFileSync(registrationSource, 'utf8'))
  const smokeSource = path.join(repoRoot, 'tools', 'independent-board-p1-005-candidate-smoke.mjs')
  writeUtf8(path.join(outputRoot, 'tools', 'independent-board-p1-005-candidate-smoke.mjs'), readFileSync(smokeSource, 'utf8'))
  const normalizer = path.join(outputRoot, 'src', 'service-traction-product-signature-normalizer.js')
  const observability = path.join(outputRoot, 'src', 'service-traction-observability.js')
  writeUtf8(normalizer, patchNormalizer(readFileSync(normalizer, 'utf8').replace(/\r\n/g, '\n')))
  writeUtf8(observability, patchObservability(readFileSync(observability, 'utf8').replace(/\r\n/g, '\n')))
  const closure = new Set()
  for (const root of ['src/service-traction-product-signature-normalizer.js', 'src/service-traction-observability.js']) {
    for (const item of importClosure(outputRoot, root)) closure.add(item)
  }
  const targets = [
    { path: 'src/fbss-independent-board-product-registration.js', action: 'add', baseSha256: null },
    { path: 'src/service-traction-product-signature-normalizer.js', action: 'modify', baseSha256: EXPECTED_BASE_SHA256['src/service-traction-product-signature-normalizer.js'] },
    { path: 'src/service-traction-observability.js', action: 'modify', baseSha256: EXPECTED_BASE_SHA256['src/service-traction-observability.js'] },
    { path: 'tools/independent-board-p1-005-candidate-smoke.mjs', action: 'add', baseSha256: null }
  ].map(target => ({
    ...target,
    candidateSha256: existsSync(path.join(outputRoot, target.path)) ? sha256(path.join(outputRoot, target.path)) : null,
    bytes: existsSync(path.join(outputRoot, target.path)) ? readFileSync(path.join(outputRoot, target.path)).byteLength : 0
  }))
  const report = {
    schema: 'fbsir.independent-board.api2-p1-005-candidate/v1',
    boundary: 'local_cleanroom_candidate_only_no_active_release_write',
    baseRoot,
    outputRoot,
    baseRelease: '202607152006-p1-004-runtime-state-14e20a6d.staged',
    baseProvenance: 'hash_anchored_remote_capture_git_provenance_unknown',
    targetCount: targets.length,
    targets,
    moduleClosure: {
      status: 'pass',
      scope: 'p1-005-normalizer-observability-static-imports',
      fileCount: closure.size,
      files: [...closure].sort(),
      dynamicSmokeRoot: 'tools/independent-board-p1-005-candidate-smoke.mjs',
      fullServicePackageClosure: 'not_proven_missing_serve_dependency_capture'
    },
    defaultOff: { registrationStatus: 'PENDING_HOST_REGISTRATION', authoritativeCreditEnabled: false, publicRouteEnabled: false, candidateEnabled: false },
    status: 'candidate_built_not_deployable_until_git_backed_or_approved_reconstruction_provenance'
  }
  writeUtf8(path.join(outputRoot, 'api2-p1-005-candidate-manifest.json'), `${JSON.stringify(report, null, 2)}\n`)
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`)
}

main()
