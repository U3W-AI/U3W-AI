import fs from 'node:fs'
import path from 'node:path'

const VIRTUAL_MODULE_ID = 'virtual:svg-icons-register'
const RESOLVED_VIRTUAL_MODULE_ID = `\0${VIRTUAL_MODULE_ID}`

function listSvgFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true })
    .flatMap((entry) => {
      const fullPath = path.join(directory, entry.name)
      if (entry.isDirectory()) {
        return listSvgFiles(fullPath)
      }
      return entry.isFile() && entry.name.endsWith('.svg') ? [fullPath] : []
    })
    .sort()
}

function readSvgSymbol(filePath, iconDirectory) {
  const source = fs.readFileSync(filePath, 'utf8')
    .replace(/<\?xml[\s\S]*?\?>/gi, '')
    .replace(/<!DOCTYPE[\s\S]*?>/gi, '')
    .trim()
  const openingTag = source.match(/<svg\b([^>]*)>/i)

  if (!openingTag) {
    throw new Error(`Invalid SVG icon: ${filePath}`)
  }

  const attributes = openingTag[1]
  const viewBoxMatch = attributes.match(/\bviewBox\s*=\s*(["'])(.*?)\1/i)
  const widthMatch = attributes.match(/\bwidth\s*=\s*(["']?)([\d.]+)\1/i)
  const heightMatch = attributes.match(/\bheight\s*=\s*(["']?)([\d.]+)\1/i)
  const viewBox = viewBoxMatch?.[2] || (widthMatch && heightMatch
    ? `0 0 ${widthMatch[2]} ${heightMatch[2]}`
    : '')
  const content = source
    .replace(/^<svg\b[^>]*>/i, '')
    .replace(/<\/svg>\s*$/i, '')
  const relativeDirectory = path.relative(iconDirectory, path.dirname(filePath))
  const iconName = path.basename(filePath, '.svg')
  const symbolName = relativeDirectory
    ? `${relativeDirectory.split(path.sep).join('-')}-${iconName}`
    : iconName
  const viewBoxAttribute = viewBox ? ` viewBox="${viewBox}"` : ''

  return `<symbol id="icon-${symbolName}"${viewBoxAttribute}>${content}</symbol>`
}

function createSprite(iconDirectory) {
  const symbols = listSvgFiles(iconDirectory)
    .map((filePath) => readSvgSymbol(filePath, iconDirectory))
    .join('')
  return `<svg id="__fbsir_svg_sprite__" aria-hidden="true" style="position:absolute;width:0;height:0;overflow:hidden">${symbols}</svg>`
}

/**
 * Keeps the established `virtual:svg-icons-register` and `#icon-*` contract
 * without relying on the unmaintained vite-plugin-svg-icons dependency.
 */
export default function createSvgIcon() {
  const iconDirectory = path.resolve(process.cwd(), 'src/assets/icons/svg')

  return {
    name: 'fbsir-local-svg-sprite',
    resolveId(id) {
      return id === VIRTUAL_MODULE_ID ? RESOLVED_VIRTUAL_MODULE_ID : null
    },
    load(id) {
      if (id !== RESOLVED_VIRTUAL_MODULE_ID) {
        return null
      }

      const sprite = createSprite(iconDirectory)
      return `const sprite = ${JSON.stringify(sprite)};
const mount = () => {
  if (!document.getElementById('__fbsir_svg_sprite__')) {
    document.body.insertAdjacentHTML('afterbegin', sprite);
  }
};
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', mount, { once: true });
} else {
  mount();
}`
    },
    configureServer(server) {
      server.watcher.add(iconDirectory)
      server.watcher.on('all', (_event, filePath) => {
        if (filePath.startsWith(iconDirectory) && filePath.endsWith('.svg')) {
          server.ws.send({ type: 'full-reload' })
        }
      })
    }
  }
}
