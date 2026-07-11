import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const uiRoot = path.resolve(scriptDir, '..')
const repoRoot = path.resolve(uiRoot, '..')
const sqlRoot = path.join(repoRoot, 'sql')
const viewsRoot = path.join(uiRoot, 'src', 'views')

// These are resolved by the dynamic-router runtime rather than src/views/*.vue.
const builtinComponents = new Set(['Layout', 'ParentView', 'InnerLink'])
const viewComponentPattern = /^[A-Za-z0-9_-]+(?:\/[A-Za-z0-9_-]+)+$/

// The fixtures cover both supported INSERT shapes: implicit sys_menu columns and
// explicit column lists, including a later dated migration.
const extractionFixtures = new Set([
  'system/user/index',
  'tool/build/index',
  'business/airobotmessage/wecomWebhook/index',
  'business/fbs/scenePack/index',
  'business/fbs/myApikey/index'
])

function stripSqlComments(sql) {
  let output = ''
  let state = 'normal'
  for (let i = 0; i < sql.length; i++) {
    const current = sql[i]
    const next = sql[i + 1]
    if (state === 'string') {
      output += current
      if (current === '\\' && next) {
        output += next
        i++
      } else if (current === "'" && next === "'") {
        output += next
        i++
      } else if (current === "'") {
        state = 'normal'
      }
      continue
    }
    if (state === 'line-comment') {
      if (current === '\n') {
        output += '\n'
        state = 'normal'
      } else {
        output += ' '
      }
      continue
    }
    if (state === 'block-comment') {
      if (current === '*' && next === '/') {
        output += '  '
        i++
        state = 'normal'
      } else {
        output += current === '\n' ? '\n' : ' '
      }
      continue
    }
    if (current === "'") {
      output += current
      state = 'string'
    } else if ((current === '-' && next === '-') || current === '#') {
      output += current === '#' ? ' ' : '  '
      if (current !== '#') i++
      state = 'line-comment'
    } else if (current === '/' && next === '*') {
      output += '  '
      i++
      state = 'block-comment'
    } else {
      output += current
    }
  }
  return output
}

function splitStatements(sql) {
  const statements = []
  let start = 0
  let inString = false
  for (let i = 0; i < sql.length; i++) {
    const current = sql[i]
    const next = sql[i + 1]
    if (inString) {
      if (current === '\\' && next) {
        i++
      } else if (current === "'" && next === "'") {
        i++
      } else if (current === "'") {
        inString = false
      }
    } else if (current === "'") {
      inString = true
    } else if (current === ';') {
      statements.push({ text: sql.slice(start, i + 1), start })
      start = i + 1
    }
  }
  if (sql.slice(start).trim()) statements.push({ text: sql.slice(start), start })
  return statements
}

function extractParenthesized(text, start) {
  let depth = 0
  let inString = false
  for (let i = start; i < text.length; i++) {
    const current = text[i]
    const next = text[i + 1]
    if (inString) {
      if (current === '\\' && next) {
        i++
      } else if (current === "'" && next === "'") {
        i++
      } else if (current === "'") {
        inString = false
      }
      continue
    }
    if (current === "'") {
      inString = true
    } else if (current === '(') {
      depth++
    } else if (current === ')' && --depth === 0) {
      return { content: text.slice(start + 1, i), end: i + 1 }
    }
  }
  return null
}

function splitTopLevelCsv(text) {
  const fields = []
  let start = 0
  let depth = 0
  let inString = false
  for (let i = 0; i < text.length; i++) {
    const current = text[i]
    const next = text[i + 1]
    if (inString) {
      if (current === '\\' && next) {
        i++
      } else if (current === "'" && next === "'") {
        i++
      } else if (current === "'") {
        inString = false
      }
      continue
    }
    if (current === "'") inString = true
    else if (current === '(') depth++
    else if (current === ')') depth--
    else if (current === ',' && depth === 0) {
      fields.push(text.slice(start, i).trim())
      start = i + 1
    }
  }
  fields.push(text.slice(start).trim())
  return fields
}

function parseSqlLiteral(expression) {
  const value = expression?.trim()
  if (!value || /^null$/i.test(value)) return null
  if (!value.startsWith("'") || !value.endsWith("'")) return undefined
  return value.slice(1, -1).replace(/''/g, "'").replace(/\\'/g, "'")
}

function parseMenuInsert(statement) {
  const header = /^\s*insert\s+into\s+`?sys_menu`?\s*/i.exec(statement)
  if (!header) return []
  let cursor = header[0].length
  let columns = null
  if (statement[cursor] === '(') {
    const columnBlock = extractParenthesized(statement, cursor)
    if (!columnBlock) return []
    columns = splitTopLevelCsv(columnBlock.content)
      .map(column => column.replace(/`/g, '').trim().toLowerCase())
    cursor = columnBlock.end
  }
  const valuesMatch = /\bvalues\b/i.exec(statement.slice(cursor))
  if (!valuesMatch) return []
  cursor += valuesMatch.index + valuesMatch[0].length

  const componentIndex = columns ? columns.indexOf('component') : 5
  const menuTypeIndex = columns ? columns.indexOf('menu_type') : 10
  if (componentIndex < 0) return []

  const rows = []
  while (cursor < statement.length) {
    const rowStart = statement.indexOf('(', cursor)
    if (rowStart < 0) break
    const rowBlock = extractParenthesized(statement, rowStart)
    if (!rowBlock) break
    const fields = splitTopLevelCsv(rowBlock.content)
    rows.push({
      component: parseSqlLiteral(fields[componentIndex]),
      menuType: menuTypeIndex >= 0 ? parseSqlLiteral(fields[menuTypeIndex]) : undefined
    })
    cursor = rowBlock.end
  }
  return rows
}

const failures = []
const components = new Map()
const sqlFiles = fs.readdirSync(sqlRoot)
  .filter(file => file.toLowerCase().endsWith('.sql'))
  .sort()

for (const sqlFile of sqlFiles) {
  const sourcePath = path.join(sqlRoot, sqlFile)
  const original = fs.readFileSync(sourcePath, 'utf8')
  const sql = stripSqlComments(original)
  for (const statement of splitStatements(sql)) {
    const line = sql.slice(0, statement.start).split('\n').length
    for (const row of parseMenuInsert(statement.text)) {
      const component = row.component
      if (!component || builtinComponents.has(component)) continue
      // Position-based extraction plus this shape check prevents permissions,
      // HTTP routes and menu paths from being mistaken for Vue components.
      if (!viewComponentPattern.test(component)) continue
      if (row.menuType && row.menuType !== 'C') {
        failures.push(`${sqlFile}:${line} non-page menu type ${row.menuType} declares component ${component}`)
        continue
      }
      if (!components.has(component)) components.set(component, [])
      components.get(component).push(`${sqlFile}:${line}`)
    }
  }
}

for (const fixture of extractionFixtures) {
  if (!components.has(fixture)) {
    failures.push(`parser fixture was not extracted from sql/*.sql: ${fixture}`)
  }
}

for (const [component, sources] of components) {
  const viewPath = path.join(viewsRoot, ...component.split('/')) + '.vue'
  if (!fs.existsSync(viewPath)) {
    failures.push(`${sources.join(', ')} component ${component} is missing src/views/${component}.vue`)
  }
}

function collectVueFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const fullPath = path.join(directory, entry.name)
    if (entry.isDirectory()) return collectVueFiles(fullPath)
    return entry.isFile() && entry.name.endsWith('.vue') ? [fullPath] : []
  })
}

function lineAt(text, index) {
  return text.slice(0, index).split('\n').length
}

// Element Plus 3 removes these legacy aliases. Keeping the source free of them
// also avoids noisy warnings during the browser menu sweep today.
for (const vueFile of collectVueFiles(viewsRoot)) {
  const source = fs.readFileSync(vueFile, 'utf8')
  const relativePath = path.relative(uiRoot, vueFile).replaceAll('\\', '/')
  for (const match of source.matchAll(/<el-button\b[\s\S]*?>/g)) {
    if (/\bsize\s*=\s*["']mini["']/.test(match[0])) {
      failures.push(`${relativePath}:${lineAt(source, match.index)} uses removed el-button size=mini`)
    }
    if (/\btype\s*=\s*["']text["']/.test(match[0])) {
      failures.push(`${relativePath}:${lineAt(source, match.index)} uses deprecated el-button type=text`)
    }
    if (/\btype\s*=\s*["']link["']/.test(match[0])) {
      failures.push(`${relativePath}:${lineAt(source, match.index)} must use the boolean el-button link prop`)
    }
  }
  for (const match of source.matchAll(/<el-radio(?!-group)(?:-button)?\b[\s\S]*?>/g)) {
    if (/(?:^|\s):?label\s*=/.test(match[0])) {
      failures.push(`${relativePath}:${lineAt(source, match.index)} uses deprecated radio label-as-value`)
    }
  }
  for (const match of source.matchAll(/<el-checkbox(?!-group)(?:-button)?\b[\s\S]*?>/g)) {
    const hasLegacyLabel = /(?:^|\s):?label\s*=/.test(match[0])
    const hasExplicitValue = /(?:^|\s):?value\s*=/.test(match[0])
    if (hasLegacyLabel && !hasExplicitValue) {
      failures.push(`${relativePath}:${lineAt(source, match.index)} uses deprecated checkbox label-as-value`)
    }
  }
}

const brandAssertions = [
  ['index.html', /<title>\s*福帮手后台系统\s*<\/title>/],
  ['src/views/login.vue', /alt="福帮手AI主机"/],
  ['src/settings.js', /footerContent:\s*'Copyright © 2026 福帮手\. All Rights Reserved\.'/],
  ['.env.development', /VITE_APP_TITLE\s*=\s*福帮手后台管理/],
  ['.env.staging', /VITE_APP_TITLE\s*=\s*福帮手后台管理/],
  ['.env.production', /VITE_APP_TITLE\s*=\s*福帮手后台管理/]
]
for (const [relativePath, pattern] of brandAssertions) {
  const text = fs.readFileSync(path.join(uiRoot, relativePath), 'utf8')
  if (!pattern.test(text)) failures.push(`${relativePath} does not satisfy the current FBSir branding contract`)
  if (/微信福帮手|WxFbsir/i.test(text)) failures.push(`${relativePath} contains a retired visible brand`)
}

if (failures.length) {
  console.error('Menu component and branding verification failed:')
  for (const failure of failures) console.error(`- ${failure}`)
  process.exitCode = 1
} else {
  console.log(`Verified ${components.size} SQL menu components across ${sqlFiles.length} SQL files.`)
  console.log('Verified login branding and Copyright © 2026 福帮手.')
  console.log('Verified current Element Plus button and radio contracts.')
}
