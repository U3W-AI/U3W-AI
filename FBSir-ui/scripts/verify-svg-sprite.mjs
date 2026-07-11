import assert from 'node:assert/strict'

import createSvgIcon from '../vite/plugins/svg-icon.js'

const plugin = createSvgIcon()
const virtualId = plugin.resolveId('virtual:svg-icons-register')
const moduleSource = plugin.load(virtualId)
const spriteMatch = moduleSource.match(/^const sprite = (.*);$/m)

assert.equal(virtualId, '\0virtual:svg-icons-register')
assert.ok(spriteMatch, 'The virtual SVG module must embed a sprite.')

const sprite = JSON.parse(spriteMatch[1])
assert.match(sprite, /<svg id="__fbsir_svg_sprite__"/)
assert.match(sprite, /<symbol id="icon-user"/)
assert.match(sprite, /<symbol id="icon-build"/)
assert.doesNotMatch(sprite, /<!DOCTYPE|<\?xml/i)

const hadDocument = Object.hasOwn(globalThis, 'document')
const originalDocument = globalThis.document
let insertedSprite = ''
globalThis.document = {
  readyState: 'complete',
  getElementById: () => null,
  body: {
    insertAdjacentHTML: (position, markup) => {
      assert.equal(position, 'afterbegin')
      insertedSprite = markup
    }
  }
}
try {
  Function(moduleSource)()
} finally {
  if (hadDocument) {
    globalThis.document = originalDocument
  } else {
    delete globalThis.document
  }
}

assert.equal(insertedSprite, sprite)

console.log(`Verified ${[...sprite.matchAll(/<symbol\b/g)].length} SVG symbols.`)
