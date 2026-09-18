const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const messageSource = fs.readFileSync(path.resolve(
    __dirname, '../../main/resources/static/js/chat/message.js'
), 'utf8')
const indexSource = fs.readFileSync(path.resolve(
    __dirname, '../../main/resources/templates/index.html'
), 'utf8')
const stylesSource = fs.readFileSync(path.resolve(
    __dirname, '../../main/resources/static/css/styles.css'
), 'utf8')

test('historical truncated messages load full content on demand', () => {
    assert.match(messageSource, /if \(message\.contentTruncated\)/)
    assert.match(messageSource, /\/chat\/session\/message\/content/)
    assert.match(messageSource, /sessionId:\s*requestedSessionId/)
    assert.match(messageSource, /messageId:\s*message\.id/)
    assert.match(messageSource, /token:\s*localStorage\.getItem\("token"\)/)
})

test('loaded full content is kept on the existing message DOM', () => {
    assert.match(messageSource, /mainDocChild\.fullText\s*=\s*result\.data/)
    assert.match(messageSource, /if \(mainDocChild\.fullText != null\)/)
    assert.match(messageSource, /mainDocChild\.loadingFullContent/)
})

test('inline and cached content bypass predicted sizes and open at natural height', () => {
    assert.match(messageSource,
        /if \(mainDocChild\.fullText != null\) \{\s*openNaturalMessageDetail\(mainDocChild\.fullText\)/)
    assert.match(messageSource,
        /if \(message\.contentTruncated\)[\s\S]*?return\s*}\s*openNaturalMessageDetail\(content\)/)
    assert.match(messageSource,
        /openNaturalMessageDetail = function \(content\) \{\s*clearMessageDetailTransition\(\)\s*buildHighlightContent\(content\)\s*\$viewModal\.modal\('open'\)/)
})

test('stale full-content responses cannot replace another message modal', () => {
    assert.match(messageSource,
        /\$viewContent\[0\]\.triggerMessageId === mainDoc\.messageId/)
})

test('full content rendering waits until the modal entrance animation completes', () => {
    assert.match(messageSource, /\$viewContent\[0\]\.detailModalReady = false/)
    assert.match(messageSource, /\$viewContent\[0\]\.pendingFullContentRender = function\(\)/)
    assert.match(messageSource, /ready: function \(modal, trigger\)[\s\S]*pendingFullContentRender\(\)/)
    assert.match(messageSource, /complete: function \(\)[\s\S]*pendingFullContentRender = null/)
})

test('full content uses a skeleton placeholder and progressively reveals the result', () => {
    assert.doesNotMatch(messageSource, /正文加载中/)
    assert.match(messageSource, /showFullContentSkeleton\(\)/)
    assert.match(messageSource, /renderLoadedFullContent\(mainDocChild\.fullText\)/)
    assert.match(messageSource, /aria-busy/)
    assert.match(stylesSource, /\.message-detail-skeleton__line/)
    assert.match(messageSource, /paragraphIndex < 6/)
    assert.match(stylesSource, /\.message-detail-skeleton__paragraph/)
    assert.match(stylesSource, /\.message-detail-skeleton__line--first[\s\S]*margin-left:\s*2rem/)
    assert.match(stylesSource, /\.message-detail-skeleton[\s\S]*justify-content:\s*space-between[\s\S]*height:\s*100%/)
    assert.match(stylesSource, /@keyframes message-detail-skeleton-wave/)
    assert.match(stylesSource, /#viewContentScroll\.message-detail-loading #viewContent/)
    assert.match(messageSource, /\$viewModal\.addClass\("message-detail-adaptive"\)/)
    assert.match(stylesSource,
        /#message-view-modal\.message-detail-adaptive\s*{[^}]*height:\s*calc\(40rem \+ 80px\)/)
    assert.match(indexSource, /id="viewContentScroll"[\s\S]*max-height:\s*40rem/)
    assert.match(stylesSource, /#message-view-modal\.message-detail-adaptive \.modal-content[\s\S]*height:\s*calc\(100% - 56px\)/)
    assert.doesNotMatch(messageSource, /detailSize|message-detail-size-/)
    assert.doesNotMatch(stylesSource, /message-detail-size-/)
    assert.match(messageSource, /requestAnimationFrame\(function \(\)[\s\S]*buildHighlightContentInner\(content\)/)
    assert.match(stylesSource, /prefers-reduced-motion/)
})

test('network-loaded content is measured offscreen before the lightweight shell resizes', () => {
    assert.match(messageSource, /prepareMessageDetailMeasurement\(renderedContent\)/)
    assert.match(messageSource, /cloneNode\(true\)/)
    assert.match(messageSource, /visibility:hidden[\s\S]*left:-100000px/)
    assert.match(messageSource, /measureContent\.innerHTML = renderedContent/)
    assert.match(messageSource,
        /querySelectorAll\("\[id\]"\)[\s\S]*if \(element !== measureContent\)[\s\S]*element\.removeAttribute\("id"\)/)
    assert.match(messageSource, /measureModal\.offsetHeight/)
    assert.match(messageSource, /Math\.min\(initialModalHeight,[\s\S]*measuredModalHeight \|\| initialModalHeight/)
    assert.doesNotMatch(messageSource, /Math\.max\((180|80),/)
    assert.match(messageSource, /document\.createDocumentFragment\(\)/)
    assert.match(messageSource, /startMessageDetailHeightResize\(targetModalHeight, targetScrollHeight, preparedContent\)/)
    assert.match(messageSource, /message-detail-resizing/)
    assert.match(stylesSource, /#message-view-modal\.message-detail-resizing[\s\S]*transition:\s*height 280ms/)
    assert.match(messageSource, /transitionend\.messageDetailResize[\s\S]*mountPreparedMessageDetail\(preparedContent\)/)
    assert.match(messageSource, /var resizeFinished = false[\s\S]*if \(resizeFinished\)[\s\S]*resizeFinished = true/)
    assert.match(messageSource, /appendChild\(preparedContent\)[\s\S]*detailRevealReadyFrame = requestAnimationFrame/)
    assert.match(messageSource, /message-detail-content-ready/)
    assert.match(messageSource, /finishMessageDetailTransition/)
    assert.match(stylesSource, /#viewContentScroll\.message-detail-content-ready \.message-detail-skeleton[\s\S]*opacity:\s*0/)
    assert.match(stylesSource, /#viewContentScroll\.message-detail-content-ready #viewContent[\s\S]*opacity:\s*1/)
    assert.match(messageSource, /\$viewContentScroll\.children\("\.message-detail-skeleton"\)\.remove\(\)/)
    assert.match(messageSource, /waitForMessageDetailImages[\s\S]*detailMeasureImageTimer[\s\S]*800/)
    assert.match(messageSource, /removeMessageDetailMeasurement\(\)/)
})

test('markdown rendering skips whole-document automatic language detection', () => {
    assert.match(messageSource,
        /buildHighlightContentInner = function \(content\) \{[\s\S]*if \(isMarkdown\(content\) \|\| hasMarkdownMath\(content\)\)[\s\S]*return marked\.parse\(content\)[\s\S]*hljs\.highlightAuto\(content\)/)
})

test('message script cache version is refreshed', () => {
    assert.match(indexSource,
        /js\/chat\/message\.js\?v=202609122139/)
    assert.match(indexSource,
        /css\/styles\.css\?v=202609122139/)
})
