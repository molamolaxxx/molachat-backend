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

test('stale full-content responses cannot replace another message modal', () => {
    assert.match(messageSource,
        /\$viewContent\[0\]\.triggerMessageId === mainDoc\.messageId/)
})

test('message script cache version is refreshed', () => {
    assert.match(indexSource,
        /js\/chat\/message\.js\?v=202608272303/)
})
