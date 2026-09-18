(function (root, factory) {
    var markdownMath = factory()

    if (typeof module === "object" && module.exports) {
        module.exports = markdownMath
    }

    root.hasMarkdownMath = markdownMath.hasMarkdownMath
    root.installMarkdownMath = markdownMath.installMarkdownMath

    if (root.marked && root.katex) {
        markdownMath.installMarkdownMath(root.marked, root.katex)
    }
}(typeof window !== "undefined" ? window : globalThis, function () {
    var inlineMathPattern = /\\\(([\s\S]+?)\\\)/
    var displayMathPattern = /(^|\n)[ \t]{0,3}\\\[[\s\S]+?\\\](?=[ \t]*(?:\n|$))/

    function hasMarkdownMath(text) {
        return typeof text === "string"
            && (inlineMathPattern.test(text) || displayMathPattern.test(text))
    }

    function renderMath(katexApi, text, displayMode) {
        return katexApi.renderToString(text, {
            displayMode: displayMode,
            throwOnError: false,
            strict: "ignore",
            trust: false
        })
    }

    function installMarkdownMath(markedApi, katexApi) {
        if (!markedApi || !katexApi || markedApi.__molaMarkdownMathInstalled) {
            return false
        }

        markedApi.use({
            extensions: [
                {
                    name: "molaDisplayMath",
                    level: "block",
                    start: function (source) {
                        var index = source.indexOf("\\[")
                        return index < 0 ? undefined : index
                    },
                    tokenizer: function (source) {
                        var match = /^ {0,3}\\\[[ \t]*\n?([\s\S]*?)\n?[ \t]*\\\](?:[ \t]*(?:\n+|$))/.exec(source)
                        if (match) {
                            return {
                                type: "molaDisplayMath",
                                raw: match[0],
                                text: match[1].trim()
                            }
                        }
                    },
                    renderer: function (token) {
                        return '<div class="mola-math-display">'
                            + renderMath(katexApi, token.text, true)
                            + '</div>\n'
                    }
                },
                {
                    name: "molaInlineMath",
                    level: "inline",
                    start: function (source) {
                        var index = source.indexOf("\\(")
                        return index < 0 ? undefined : index
                    },
                    tokenizer: function (source) {
                        var match = /^\\\(([\s\S]+?)\\\)/.exec(source)
                        if (match) {
                            return {
                                type: "molaInlineMath",
                                raw: match[0],
                                text: match[1]
                            }
                        }
                    },
                    renderer: function (token) {
                        return '<span class="mola-math-inline">'
                            + renderMath(katexApi, token.text, false)
                            + '</span>'
                    }
                }
            ]
        })
        markedApi.__molaMarkdownMathInstalled = true
        return true
    }

    return {
        hasMarkdownMath: hasMarkdownMath,
        installMarkdownMath: installMarkdownMath
    }
}))
