const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const {
    parseFilePreviewTarget,
    getFileViewerViewportGeometry
} = require('../../main/resources/static/js/chat/file-viewer.js')

const viewerSource = fs.readFileSync(path.resolve(
    __dirname, '../../main/resources/static/js/chat/file-viewer.js'
), 'utf8')
const viewerStyles = fs.readFileSync(path.resolve(
    __dirname, '../../main/resources/static/css/styles.css'
), 'utf8')
const indexSource = fs.readFileSync(path.resolve(
    __dirname, '../../main/resources/templates/index.html'
), 'utf8')

test('parses Windows and Linux local file links', () => {
    assert.deepEqual(parseFilePreviewTarget('C:/Users/mola/Test.java'), {
        target: 'C:/Users/mola/Test.java', requestedLine: null, remote: false
    })
    assert.deepEqual(parseFilePreviewTarget('file:///home/mola/Test.java#L128'), {
        target: 'file:///home/mola/Test.java', requestedLine: 128, remote: false
    })
    assert.deepEqual(parseFilePreviewTarget('/home/mola/Test.java:42'), {
        target: '/home/mola/Test.java', requestedLine: 42, remote: false
    })
})

test('keeps remote port separate from line number', () => {
    assert.deepEqual(parseFilePreviewTarget('https://example.com:8443/Test.java#L8'), {
        target: 'https://example.com:8443/Test.java', requestedLine: 8, remote: true
    })
})

test('accepts relative text files and ignores non-file protocols', () => {
    assert.deepEqual(parseFilePreviewTarget('docs/design.md'), {
        target: 'docs/design.md', requestedLine: null, remote: false
    })
    assert.equal(parseFilePreviewTarget('mailto:user@example.com'), null)
    assert.equal(parseFilePreviewTarget('javascript:alert(1)'), null)
})

test('opens a skeleton before scheduling any file rendering', () => {
    assert.match(viewerSource, /ready:\s*schedulePendingRender/)
    assert.match(viewerSource, /queueFileRender\(result\.data, null, parsed\.target\)/)
    assert.match(viewerSource, /showLoading\([\s\S]*?\$modal\.modal\("open"\)/)
    assert.doesNotMatch(viewerSource, /renderFile\(result\.data\)/)
    assert.match(viewerSource, /requestAnimationFrame\(function \(\) \{[\s\S]*requestAnimationFrame\(function \(\) \{[\s\S]*renderFile\(data, mode\)/)
})

test('local preview failure only shows toast without closing a viewer', () => {
    const failureStart = viewerSource.indexOf('function handleFailure(parsed, fallback)')
    const fallbackStart = viewerSource.indexOf('function prepareRemoteFallback()', failureStart)
    const failureSource = viewerSource.slice(failureStart, fallbackStart)

    assert.match(failureSource, /showToast\("当前链接不支持展示"/)
    assert.doesNotMatch(failureSource, /\$modal\.modal\("close"\)/)
})

test('file viewer keeps long source content inside a full-height scroll layout', () => {
    assert.match(indexSource, /class="file-viewer__code-scroll">\s*<div class="file-viewer__code-layout">/)
    assert.match(viewerStyles, /\.file-viewer__code-layout\s*{[^}]*display:\s*flex;[^}]*min-width:\s*100%;[^}]*min-height:\s*100%;/s)
    assert.match(viewerStyles, /\.file-viewer__gutter\s*{[^}]*overflow:\s*visible;/s)
    assert.match(viewerStyles, /\.file-viewer__code\s*{[^}]*min-width:\s*0;/s)
    assert.doesNotMatch(viewerStyles, /\.file-viewer__code\s*{[^}]*min-width:\s*100%;/s)
})

test('file viewer exposes a labelled close button', () => {
    assert.match(indexSource, /class="file-viewer__close btn-flat"[^>]*aria-label="关闭文件查看器"[^>]*>\s*<i[^>]*>close<\/i>\s*<\/button>/)
    assert.doesNotMatch(indexSource, /file-viewer__close[\s\S]*?<span>关闭<\/span>/)
})

test('Markdown and HTML open in preview mode even when a line is requested', () => {
    const renderStart = viewerSource.indexOf('function renderFile(data, mode)')
    const sourceStart = viewerSource.indexOf('function renderSource(data)', renderStart)
    const renderFileSource = viewerSource.slice(renderStart, sourceStart)

    assert.match(renderFileSource, /data\.renderMode === "MARKDOWN" \|\| data\.renderMode === "HTML"/)
    assert.match(renderFileSource, /\$mode\.show\(\);\s*renderPreview\(data\);/)
    assert.doesNotMatch(renderFileSource, /if\s*\(data\.requestedLine\)\s*renderSource\(data\)/)
})

test('preview iframe is block-level and remains hidden outside preview mode', () => {
    assert.match(viewerStyles, /\.file-viewer__preview\s*{[^}]*display:\s*block;/s)
    assert.match(viewerStyles, /\.file-viewer__preview\[hidden\]\s*{[^}]*display:\s*none;/s)
})

test('all file render modes use full-height skeletons and progressive reveal', () => {
    assert.match(viewerSource, /paragraph < 7/)
    assert.match(viewerSource, /line < 32/)
    assert.match(viewerSource, /renderSource\(data\)[\s\S]*revealViewerContent\(\$source\)/)
    assert.match(viewerSource, /load\.fileViewer[\s\S]*revealViewerContent\(\$preview\)/)
    assert.match(viewerSource, /previewRevealTimer[\s\S]*}, 1400\)/)
    assert.match(viewerStyles, /\.file-viewer__skeleton--article\s*{[^}]*justify-content:\s*space-between;/s)
    assert.match(viewerStyles, /\.file-viewer__skeleton--source\s*{[^}]*grid-template-rows:\s*repeat\(32,/s)
    assert.match(viewerStyles, /\.file-viewer__content--visible\s*{[^}]*opacity:\s*1;/s)
    assert.doesNotMatch(indexSource, /正在读取文件/)
})

test('file viewer cache versions include the progressive renderer', () => {
    assert.match(indexSource, /css\/styles\.css\?v=202609122139/)
    assert.match(indexSource, /js\/chat\/file-viewer\.js\?v=202608280008/)
})

test('web previews use a revocable Blob URL while App previews keep srcdoc', () => {
    assert.match(viewerSource, /!\(typeof isAppNow === "function" && isAppNow\(\)\)\s*&& global\.Blob/s)
    assert.match(viewerSource, /new global\.Blob\(\[html\],\s*{\s*type:\s*"text\/html;charset=UTF-8"/s)
    assert.match(viewerSource, /global\.URL\.createObjectURL/)
    assert.match(viewerSource, /global\.URL\.revokeObjectURL\(objectUrl\)/)
    assert.match(viewerSource, /else\s*{\s*\$preview\.removeAttr\("src"\)\.attr\("srcdoc", html\);\s*}/s)
    assert.match(viewerSource, /isAppNow\(\)\)\s*{\s*\$preview\.removeAttr\("src"\)\.attr\("srcdoc", ""\);/s)
})

test('viewer geometry cancels body zoom while preserving viewport margins', () => {
    assert.deepEqual(getFileViewerViewportGeometry(1879, 1013, 1.59), {
        top: 1013 * .03 / 1.59,
        height: 1013 * .94 / 1.59
    })
    assert.deepEqual(getFileViewerViewportGeometry(800, 900, 1.2), {
        top: 900 * .02 / 1.2,
        height: 900 * .96 / 1.2
    })
    assert.deepEqual(getFileViewerViewportGeometry(390, 844, 1), {
        top: 0,
        height: 844
    })
})
