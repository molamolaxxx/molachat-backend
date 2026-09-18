const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const marked = require('../../main/resources/static/js/utils/marked.min.js')
const katex = require('../../main/resources/static/vendor/katex/katex.min.js')
const markdownMath = require('../../main/resources/static/js/utils/markdown-math.js')

markdownMath.installMarkdownMath(marked, katex)

const indexSource = fs.readFileSync(path.resolve(
    __dirname, '../../main/resources/templates/index.html'
), 'utf8')

test('renders inline LaTeX without losing Markdown delimiters', () => {
    const html = marked.parse(String.raw`梯度位于 \(\mathbf{x}\) 处。`)

    assert.match(html, /class="mola-math-inline"/)
    assert.match(html, /class="katex"/)
    assert.match(html, /mathvariant="bold"/)
    assert.doesNotMatch(html, />\(\\mathbf\{x\}\)</)
})

test('renders display matrices as KaTeX blocks', () => {
    const markdown = String.raw`梯度为：

\[
\nabla f(\mathbf{x})=
\begin{bmatrix}
\frac{\partial f}{\partial x_1}\\
\vdots\\
\frac{\partial f}{\partial x_n}
\end{bmatrix}
\]
`
    const html = marked.parse(markdown)

    assert.match(html, /class="mola-math-display"/)
    assert.match(html, /class="katex-display"/)
    assert.match(html, /<mtable/)
})

test('recognizes formula-only messages as Markdown content', () => {
    assert.equal(markdownMath.hasMarkdownMath(String.raw`\(x_1+x_2\)`), true)
    assert.equal(markdownMath.hasMarkdownMath(String.raw`\[x_1+x_2\]`), true)
    assert.equal(markdownMath.hasMarkdownMath('普通文本'), false)
})

test('does not render LaTeX delimiters inside fenced code', () => {
    const markdown = '```text\n\\(not math\\)\n```'
    const html = marked.parse(markdown)

    assert.doesNotMatch(html, /class="katex"/)
    assert.match(html, /\\\(not math\\\)/)
})

test('loads KaTeX before the Markdown math integration and message renderer', () => {
    const katexIndex = indexSource.indexOf('vendor/katex/katex.min.js?v=0.18.1')
    const mathIndex = indexSource.indexOf('js/utils/markdown-math.js?v=202609122139')
    const messageIndex = indexSource.indexOf('js/chat/message.js?v=202609122139')

    assert.ok(katexIndex >= 0)
    assert.ok(katexIndex < mathIndex)
    assert.ok(mathIndex < messageIndex)
    assert.match(indexSource, /vendor\/katex\/katex\.min\.css\?v=0\.18\.1/)
})
