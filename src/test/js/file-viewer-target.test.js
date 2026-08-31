const test = require('node:test')
const assert = require('node:assert/strict')
const { parseFilePreviewTarget } = require('../../main/resources/static/js/chat/file-viewer.js')

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
