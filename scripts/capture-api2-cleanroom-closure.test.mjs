import test from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs'
import os from 'node:os'
import path from 'node:path'

const script = path.resolve('scripts/capture-api2-cleanroom-closure.mjs')

function write(root, relative, content) {
  const file = path.join(root, relative)
  mkdirSync(path.dirname(file), { recursive: true })
  writeFileSync(file, content, 'utf8')
}

test('captures manifest consistency, runtime inputs, and per-file critical inventory evidence', () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'api2-closure-'))
  try {
    write(root, 'tools/serve.mjs', "import './lib/runtime.mjs'; process.env.FBSS_API2_BASE_URL\n")
    write(root, 'tools/build-api2-package.mjs', "import 'node:fs'\n")
    write(root, 'tools/service-traction-strong-signature-normalizer-smoke.mjs', "console.log('smoke')\n")
    write(root, 'tools/deploy-api2-sidecar-clean-source.ps1', "'tools/build-api2-package.mjs'\n")
    write(root, 'tools/deploy-api2-sidecar-release.ps1', "'package.json'\n")
    write(root, 'tools/lib/runtime.mjs', 'export const runtime = true\n')
    write(root, 'package.json', '{"name":"fixture"}\n')
    write(root, 'data/live-baseline.json', '{}\n')
    const critical = [
      'package.json',
      'tools/serve.mjs',
      'tools/build-api2-package.mjs',
      'tools/service-traction-strong-signature-normalizer-smoke.mjs',
      'tools/lib/runtime.mjs'
    ]
    write(root, 'root/api2-deploy-manifest.json', JSON.stringify({
      sourceSelection: {
        criticalDeploySnapshot: { fileCount: critical.length, sha256: 'a'.repeat(64) }
      },
      sourceProvenance: {
        criticalDeploySnapshotSha256: 'a'.repeat(64),
        criticalDeployFiles: critical
      }
    }))
    write(root, 'root/api2-candidate-provenance.json', JSON.stringify({
      base: { gitProvenance: { status: 'unknown' } }
    }))

    const output = execFileSync(process.execPath, [script, '--root', root], { encoding: 'utf8' })
    const report = JSON.parse(output)
    assert.equal(report.manifestLoad.hasCriticalList, true)
    assert.equal(report.manifestConsistency.snapshotHashMatches, true)
    assert.equal(report.manifestConsistency.fileCountMatches, true)
    assert.equal(report.criticalInventory.presentCount, critical.length)
    assert.equal(typeof report.criticalInventory.present[0].sha256, 'string')
    assert.ok(report.runtimeInputs.mandatory.some(item => item.path === 'data/live-baseline.json' && item.exists))
    assert.ok(report.runtimeInputs.mandatory.some(item => item.path === 'data/current-service-release.json' && !item.exists))
    assert.ok(report.runtimeInputs.mutableExcluded.some(item => item.path.includes('host-forwarding-ack-challenges')))
    assert.equal(report.runtimeInputs.specCoverage.complete, true)
    assert.equal(report.deployDecision, 'NO_GO_READ_ONLY_CAPTURE_ONLY')
  } finally {
    rmSync(root, { recursive: true, force: true })
  }
})
