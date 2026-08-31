(function (global) {
    "use strict";

    function decodeLink(value) {
        try {
            return decodeURI(value);
        } catch (ignored) {
            return value;
        }
    }

    function parseFilePreviewTarget(rawHref) {
        if (!rawHref) return null;
        var target = decodeLink(String(rawHref).trim().replace(/^<|>$/g, ""));
        if (!target || /^javascript:/i.test(target) || /^mailto:/i.test(target)
                || (target.charAt(0) === "#" && !/^#L\d+$/i.test(target))) return null;

        var requestedLine = null;
        var hashMatch = target.match(/#(?:L|line-)(\d+)$/i);
        if (hashMatch) {
            requestedLine = parseInt(hashMatch[1], 10);
            target = target.slice(0, hashMatch.index);
        }
        var remote = /^https?:\/\//i.test(target);
        if (!remote) {
            var suffixMatch = target.match(/:(\d+)$/);
            if (suffixMatch && !/^[A-Za-z]:\d+$/.test(target)) {
                requestedLine = parseInt(suffixMatch[1], 10);
                target = target.slice(0, suffixMatch.index);
            }
        }
        var local = /^file:\/\//i.test(target) || /^[A-Za-z]:[\\/]/.test(target)
                || /^\//.test(target) || /^\.\.?(?:[\\/]|$)/.test(target)
                || (!/^[a-z][a-z0-9+.-]*:/i.test(target)
                && /\.[A-Za-z0-9_-]{1,12}(?:[?#].*)?$/.test(target));
        if (!remote && !local) return null;
        return { target: target, requestedLine: requestedLine, remote: remote };
    }

    global.parseFilePreviewTarget = parseFilePreviewTarget;
    if (typeof module !== "undefined" && module.exports) {
        module.exports = { parseFilePreviewTarget: parseFilePreviewTarget };
    }

    if (typeof global.jQuery === "undefined") return;

    global.jQuery(function () {
        var $ = global.jQuery;
        var $modal = $("#file-viewer-modal");
        var $loading = $modal.find(".file-viewer__loading");
        var $source = $modal.find(".file-viewer__source");
        var $preview = $modal.find(".file-viewer__preview");
        var $mode = $modal.find(".file-viewer__mode");
        var $title = $modal.find(".file-viewer__title");
        var $meta = $modal.find(".file-viewer__meta");

        $modal.modal({
            dismissible: true,
            opacity: .25,
            in_duration: 220,
            out_duration: 160,
            starting_top: "3%",
            ending_top: "3%",
            complete: clearViewer
        });

        $modal.find(".file-viewer__close").on("click", function () {
            $modal.modal("close");
        });

        $mode.on("click", function () {
            var data = $modal[0].previewData;
            if (!data) return;
            if ($preview.prop("hidden")) renderPreview(data);
            else renderSource(data);
        });

        $(document).on("click", "#viewContent a", function (event) {
            var parsed = parseFilePreviewTarget(this.getAttribute("href"));
            if (!parsed) return;
            event.preventDefault();
            event.stopPropagation();
            openFileViewer(parsed);
        });

        function openFileViewer(parsed) {
            var activeSessionId = typeof getActiveSessionId === "function"
                    ? getActiveSessionId() : null;
            if (!activeSessionId || typeof getActiveChatter !== "function"
                    || !getActiveChatter()) {
                showToast("当前链接不支持展示", 1400);
                return;
            }
            var fallback = parsed.remote ? prepareRemoteFallback() : null;
            showLoading(parsed.target);
            $modal.modal("open");
            $.ajax({
                url: getPrefix() + "/chat/robot/file-preview",
                type: "POST",
                dataType: "json",
                contentType: "application/json; charset=UTF-8",
                timeout: 11000,
                data: JSON.stringify({
                    chatterId: getChatterId(),
                    token: localStorage.getItem("token"),
                    sessionId: activeSessionId,
                    target: parsed.target,
                    requestedLine: parsed.requestedLine
                }),
                success: function (result) {
                    if (!result || result.status !== 0 || !result.data) {
                        handleFailure(parsed, fallback);
                        return;
                    }
                    if (fallback && !fallback.closed) fallback.close();
                    renderFile(result.data);
                },
                error: function () {
                    handleFailure(parsed, fallback);
                }
            });
        }

        function showLoading(target) {
            clearViewer();
            $title.text(fileName(target) || "文件预览").attr("title", target);
            $meta.text("正在读取");
            $loading.show();
            $mode.hide();
        }

        function renderFile(data) {
            $modal[0].previewData = data;
            $title.text(data.displayName || "文件预览");
            var details = [data.source === "REMOTE" ? "远程" : "本地"];
            if (data.charset) details.push(data.charset);
            if (data.lineCount != null) details.push(data.lineCount + " 行");
            if (data.requestedLine) details.push("定位 L" + data.requestedLine);
            $meta.text(details.join(" · "));
            $loading.hide();
            if (data.renderMode === "MARKDOWN" || data.renderMode === "HTML") {
                $mode.show();
                if (data.requestedLine) renderSource(data);
                else renderPreview(data);
            } else {
                $mode.hide();
                renderSource(data);
            }
        }

        function renderSource(data) {
            $preview.prop("hidden", true).attr("srcdoc", "");
            $source.prop("hidden", false);
            $mode.text("预览");
            var content = data.content || "";
            var highlighted;
            try {
                highlighted = hljs.highlightAuto(content).value;
            } catch (ignored) {
                highlighted = escapeHtml(content);
            }
            $source.find("code").html(highlighted);
            var lineCount = Math.max(1, data.lineCount || content.split("\n").length);
            var numbers = [];
            for (var line = 1; line <= lineCount; line++) numbers.push(line);
            $source.find(".file-viewer__gutter").text(numbers.join("\n"));
            var $scroll = $source.find(".file-viewer__code-scroll");
            var $focus = $source.find(".file-viewer__line-focus");
            $scroll.scrollTop(0).scrollLeft(0);
            $focus.hide();
            if (data.requestedLine && data.requestedLine <= lineCount) {
                var lineHeight = getInnerWidth() <= 600 ? 20 : 21;
                var top = 14 + (data.requestedLine - 1) * lineHeight;
                $focus.css("top", top + "px").show();
                requestAnimationFrame(function () {
                    $scroll.scrollTop(Math.max(0, top - $scroll.innerHeight() * .35));
                });
            } else if (data.requestedLine && data.requestedLine > lineCount) {
                showToast("目标行超出文件范围", 1400);
            }
        }

        function renderPreview(data) {
            $source.prop("hidden", true);
            $preview.prop("hidden", false);
            $mode.text("源码");
            var base = data.baseUrl
                    ? '<base href="' + escapeAttribute(data.baseUrl) + '">' : "";
            var csp = '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; '
                    + 'style-src \'unsafe-inline\' http: https:; img-src data: http: https:; '
                    + 'font-src data: http: https:; script-src \'none\'; connect-src \'none\'; '
                    + 'object-src \'none\'; frame-src \'none\'; form-action \'none\';">';
            if (data.renderMode === "MARKDOWN") {
                var markdown = marked.parse(data.content || "");
                var markdownCss = '<style>body{max-width:980px;margin:0 auto;padding:24px 28px;'
                        + 'color:#263746;font:15px/1.7 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}'
                        + 'pre{overflow:auto;padding:14px;border-radius:8px;background:#f5f7f9}'
                        + 'code{font-family:Consolas,Monaco,monospace}table{display:block;overflow:auto;'
                        + 'border-collapse:collapse}th,td{padding:7px 11px;border:1px solid #dce5eb}'
                        + 'img{max-width:100%}a{color:#287fb7}</style>';
                $preview.attr("sandbox", "allow-same-origin").attr("srcdoc",
                        '<!doctype html><html><head>' + csp + base + markdownCss
                        + '</head><body>' + markdown + '</body></html>');
                $preview.off("load.fileViewer").on("load.fileViewer", function () {
                    try {
                        $(this.contentDocument).off("click.fileViewer", "a")
                                .on("click.fileViewer", "a", function (event) {
                                    var parsed = parseFilePreviewTarget(this.getAttribute("href"));
                                    if (!parsed) return;
                                    event.preventDefault();
                                    openFileViewer(parsed);
                                });
                    } catch (ignored) { }
                });
            } else {
                $preview.off("load.fileViewer").attr("sandbox", "");
                var html = data.content || "";
                var guards = csp + base;
                if (/<head(?:\s[^>]*)?>/i.test(html)) {
                    html = html.replace(/<head(\s[^>]*)?>/i, function (head) {
                        return head + guards;
                    });
                } else {
                    html = '<!doctype html><html><head>' + guards
                            + '</head><body>' + html + '</body></html>';
                }
                $preview.attr("srcdoc", html);
            }
        }

        function handleFailure(parsed, fallback) {
            $modal.modal("close");
            if (parsed.remote) openRemote(parsed.target, fallback);
            else showToast("当前链接不支持展示", 1600);
        }

        function prepareRemoteFallback() {
            if (typeof isAppNow === "function" && isAppNow()) return null;
            try {
                var popup = global.open("", "_blank");
                if (popup) {
                    popup.document.title = "正在识别文件";
                    popup.document.body.textContent = "正在识别文件，请稍候…";
                }
                return popup;
            } catch (ignored) {
                return null;
            }
        }

        function openRemote(url, fallback) {
            if (fallback && !fallback.closed) {
                fallback.location.replace(url);
                return;
            }
            try {
                if (global.cordova && global.cordova.InAppBrowser) {
                    global.cordova.InAppBrowser.open(url, "_system");
                } else {
                    global.open(url, "_blank", "noopener");
                }
            } catch (ignored) {
                showToast("请在浏览器中打开该链接", 1800);
            }
        }

        function clearViewer() {
            $modal[0].previewData = null;
            $loading.hide();
            $source.prop("hidden", true).find("code").empty();
            $source.find(".file-viewer__gutter").empty();
            $source.find(".file-viewer__line-focus").hide();
            $preview.prop("hidden", true).off("load.fileViewer").attr("srcdoc", "");
            $mode.hide();
            $meta.empty();
        }

        function fileName(target) {
            var normalized = String(target || "").replace(/[?#].*$/, "").replace(/\\/g, "/");
            return normalized.slice(normalized.lastIndexOf("/") + 1);
        }

        function escapeHtml(value) {
            return String(value).replace(/&/g, "&amp;").replace(/</g, "&lt;")
                    .replace(/>/g, "&gt;");
        }

        function escapeAttribute(value) {
            return escapeHtml(value).replace(/"/g, "&quot;").replace(/'/g, "&#39;");
        }
    });
})(typeof window !== "undefined" ? window : this);
