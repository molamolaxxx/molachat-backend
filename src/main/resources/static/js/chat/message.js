// 消息发送
$(document).ready(function () {
    var $send = $(".send"),
        $chatInput = $(".chat__input")[0],
        $viewContent = $("#viewContent"),
        $viewContentScroll = $("#message-view-modal #viewContentScroll"),
        $viewModal = $("#message-view-modal"),
        $copyViewBtn = $('#copyViewBtn'),
        $chatMsg = $(".chat__messages")[0];
    // 配置marked采用高亮
    const originalCodes = [];
    marked.setOptions({
        highlight: function (code, lang) {
            const res = hljs.highlightAuto(code + (getInnerWidth() <= 600 ? "\n " : ""))
            // code 参数即为原始代码内容
            originalCodes.push(code);
            return res.value;
        },
        langPrefix: 'hljs '
    });

    // dom初始化位置
    $viewModal.css("max-width", 1000)
    if (getInnerWidth() > 1000) {
        $viewModal.css("left", (getInnerWidth() - $viewModal.innerWidth()) / 2)
    }

    addResizeEventListener(function () {
        if (getInnerWidth() > 1000) {
            $viewModal.css("left", (getInnerWidth() - $viewModal.innerWidth()) / 2)
        } else {
            $viewModal.css("left", 0)
        }
    })

    //const
    const SEND_MESSAGE = 595;

    // 聊天时间显示dom
    var lastTime = -1;
    timeDom = function (time) {
        if (time - lastTime < 60000 && time - lastTime > 0) {
            lastTime = time
            return
        }
        lastTime = time

        var timeDom = document.createElement("div");
        $(timeDom).addClass("time");
        // 将时间戳转化成yyyy-mm-dd格式
        timeDom.innerText = times(time)
        return timeDom
    }


    //<img class="contact__photo" src="img/mola.png" style="float: right;display: inline;margin-right: 0rem;">
    //message dom
    messageDom = function (message, isMain) {
        // 时间dom
        let timeDoc = timeDom(message.createTime)
        if (timeDoc) {
            $chatMsg.append(timeDoc)
        }
        let content = message.content
        //拼装dom
        var mainDoc = document.createElement("div");
        $(mainDoc).addClass("chat__msgRow");

        mainDoc.messageId = message.id

        var mainDocChild = document.createElement("div")

        var imgDoc = document.createElement("img");

        if (isMain) {
            //头像img

            imgDoc.src = getChatterImage();
            $(imgDoc).addClass("contact__photo");
            $(imgDoc).css('float', 'right');
            $(imgDoc).css('display', 'inline');
            $(imgDoc).css('margin-right', '0rem');

            $(mainDocChild).css('margin-right', '0.5rem');
            $(mainDocChild).addClass("chat__message notMine");
        } else {

            imgDoc.src = getActiveChatter().imgUrl;
            $(imgDoc).addClass("contact__photo");
            $(imgDoc).css('float', 'left');
            $(imgDoc).css('display', 'inline');
            $(imgDoc).css('margin-right', '0rem');

            $(mainDocChild).css('margin-left', '0.5rem');
            $(mainDocChild).addClass("chat__message mine");
        }
        $(mainDocChild).css('position', 'relative');

        // 服务端定义样式
        if (message.showStyleProps) {
            Object.keys(message.showStyleProps).forEach(key => {
                $(mainDocChild).css(key, message.showStyleProps[key]);
            });
        }

        mainDoc.append(imgDoc);
        // mainDocChild.innerHTML = twemoji.parse(content,{"folder":"svg","ext":".svg","base":"asset/","size":15});
        if (!message.streamId) {
            mainDocChild.innerText = message.contentTruncated || content.length > 200
                ? content.slice(0, 200) + "\n...."
                : content
        } else {
            mainDocChild.innerText = content;
            mainDocChild.fullText = content;
        }
        mainDoc.append(mainDocChild);
        mainDoc.mainDocChild = mainDocChild;
        // 明细view
        if (content.length > 80 || message.contentTruncated || message.streamId) {
            var copyIcon = document.createElement("span");
            $(copyIcon).addClass("copy_icon");
            $(copyIcon).css('position', 'absolute');
            $(copyIcon).css('right', '0');
            $(copyIcon).css('bottom', '0');
            $(copyIcon).css('padding', '4px');
            $(copyIcon).css('cursor', 'pointer');
            if (!message.streamId) {
                const onClickCallback = (e) => {
                    // 流消息会自动刷新模态框，不用重置
                    $viewContent[0].triggerMessageId = mainDoc.messageId
                    if (mainDocChild.fullText != null) {
                        openNaturalMessageDetail(mainDocChild.fullText)
                        return
                    }
                    if (message.contentTruncated) {
                        showFullContentSkeleton()
                        $viewContent[0].detailModalReady = false
                        $viewContent[0].detailModalOpening = true
                        $viewContent[0].pendingFullContentRender = null
                        $viewModal.modal('open')
                        if (mainDocChild.loadingFullContent) {
                            return
                        }
                        mainDocChild.loadingFullContent = true
                        var requestedSessionId = message.sessionId
                        $.ajax({
                            url: getPrefix() + "/chat/session/message/content",
                            type: "get",
                            dataType: "json",
                            timeout: 10000,
                            data: {
                                sessionId: requestedSessionId,
                                messageId: message.id,
                                chatterId: getChatterId(),
                                token: localStorage.getItem("token")
                            },
                            success: function(result) {
                                if (!result || result.data == null) {
                                    handleFullContentFailure()
                                    return
                                }
                                mainDocChild.fullText = result.data
                                message.contentTruncated = false
                                if ($viewContent[0].triggerMessageId === mainDoc.messageId) {
                                    if ($viewContent[0].detailModalReady) {
                                        renderLoadedFullContent(mainDocChild.fullText)
                                    } else if ($viewContent[0].detailModalOpening) {
                                        $viewContent[0].pendingFullContentRender = function() {
                                            if ($viewContent[0].triggerMessageId === mainDoc.messageId) {
                                                renderLoadedFullContent(mainDocChild.fullText)
                                            }
                                        }
                                    }
                                }
                            },
                            error: handleFullContentFailure,
                            complete: function() {
                                mainDocChild.loadingFullContent = false
                            }
                        })
                        return
                    }
                    openNaturalMessageDetail(content)
                }

                function handleFullContentFailure() {
                    if ($viewContent[0].triggerMessageId === mainDoc.messageId) {
                        var renderFailure = function() {
                            if ($viewContent[0].triggerMessageId === mainDoc.messageId) {
                                renderLoadedFullContent("正文加载失败，请稍后重试")
                            }
                        }
                        if ($viewContent[0].detailModalReady) {
                            renderFailure()
                        } else if ($viewContent[0].detailModalOpening) {
                            $viewContent[0].pendingFullContentRender = renderFailure
                        }
                        showToast("正文加载失败，请稍后重试", 1500)
                    }
                }
                $(copyIcon).on('click', onClickCallback)
                $(mainDocChild).on('click', onClickCallback)
            }

            $(mainDocChild).css("cursor", "pointer")
            copyIcon.innerHTML = '<i class="material-icons" style="font-size: 15px;color: #868e8a;">launch</i>'
            if (!message.streamId) {
                mainDocChild.append(copyIcon)
            }
        }
        return mainDoc;
    }

    addCopyButtonToPre = function (doc) {
        // 为所有pre元素添加复制按钮
        doc.querySelectorAll('pre').forEach(pre => {
            const copyBtn = document.createElement('button');
            copyBtn.className = 'copy-btn';
            copyBtn.textContent = 'Copy';
            copyBtn.toCopy = originalCodes.shift()
            copyBtn.onclick = () => copyText(copyBtn.toCopy);
            pre.appendChild(copyBtn);/*  */
        });
        // 清空代码缓存
        originalCodes.splice(0, originalCodes.length)
    }


    buildHighlightContent = function (content) {
        $viewContent[0].innerHTML = buildHighlightContentInner(content)
        addCopyButtonToPre($viewContent[0])
    }

    openNaturalMessageDetail = function (content) {
        clearMessageDetailTransition()
        buildHighlightContent(content)
        $viewModal.modal('open')
    }

    showFullContentSkeleton = function () {
        clearMessageDetailTransition()
        $viewModal.addClass("message-detail-adaptive")
        $viewContent.removeClass("view-content")
        $viewContent[0].innerHTML = ""
        $viewContentScroll.addClass("message-detail-loading")
        $viewContentScroll.attr("aria-busy", "true")
        $viewContentScroll.attr("aria-label", "正在加载正文")
        var skeletonHtml = '<span class="message-detail-skeleton" aria-hidden="true">'
        for (var paragraphIndex = 0; paragraphIndex < 6; paragraphIndex++) {
            skeletonHtml += '<span class="message-detail-skeleton__paragraph">' +
                '<span class="message-detail-skeleton__line message-detail-skeleton__line--first"></span>' +
                '<span class="message-detail-skeleton__line message-detail-skeleton__line--middle"></span>' +
                '<span class="message-detail-skeleton__line message-detail-skeleton__line--last"></span>' +
                '</span>'
        }
        $viewContentScroll.append(skeletonHtml + '</span>')
    }

    renderLoadedFullContent = function (content) {
        clearTimeout($viewContent[0].detailRenderTimer)
        cancelAnimationFrame($viewContent[0].detailRenderFrame)
        $viewContent[0].detailRenderFrame = requestAnimationFrame(function () {
            $viewContent[0].detailRenderTimer = setTimeout(function () {
                if (!$viewContent[0].detailModalOpening) {
                    return
                }
                var renderedContent = buildHighlightContentInner(content)
                prepareMessageDetailMeasurement(renderedContent)
            }, 0)
        })
    }

    prepareMessageDetailMeasurement = function (renderedContent) {
        if (!$viewContent[0].detailModalOpening) {
            return
        }
        removeMessageDetailMeasurement()
        var measureModal = $viewModal[0].cloneNode(true)
        var measureScroll = measureModal.querySelector("#viewContentScroll")
        var measureContent = measureModal.querySelector("#viewContent")
        if (!measureScroll || !measureContent) {
            return
        }

        measureModal.classList.remove("message-detail-adaptive", "message-detail-resizing")
        measureModal.classList.add("message-detail-measure")
        measureModal.style.cssText = "display:block;position:fixed;visibility:hidden;pointer-events:none;" +
            "left:-100000px;right:auto;top:0;bottom:auto;margin:0;opacity:0;" +
            "width:" + $viewModal[0].offsetWidth + "px;height:auto;max-width:none;max-height:none;" +
            "overflow:visible;contain:layout style paint;"
        measureScroll.classList.remove("message-detail-loading", "message-detail-prepared",
            "message-detail-content-ready")
        measureScroll.style.height = "auto"
        measureScroll.style.maxHeight = "none"
        measureScroll.style.overflow = "visible"
        measureContent.style.visibility = "visible"
        measureContent.style.opacity = "1"
        measureContent.innerHTML = renderedContent
        Array.prototype.forEach.call(measureModal.querySelectorAll(".message-detail-skeleton"),
            function (skeleton) {
                skeleton.parentNode.removeChild(skeleton)
        })
        Array.prototype.forEach.call(measureModal.querySelectorAll("[id]"), function (element) {
            // Keep #viewContent so the offscreen clone receives exactly the same
            // Markdown/tool-card styles as the visible modal during measurement.
            if (element !== measureContent) {
                element.removeAttribute("id")
            }
        })
        document.body.appendChild(measureModal)
        $viewContent[0].detailMeasureModal = measureModal

        waitForMessageDetailImages(measureContent, function () {
            if (!$viewContent[0].detailModalOpening
                || $viewContent[0].detailMeasureModal !== measureModal) {
                return
            }
            $viewContent[0].detailMeasureFrame = requestAnimationFrame(function () {
                $viewContent[0].detailMeasureReadyFrame = requestAnimationFrame(function () {
                    completeMessageDetailMeasurement(measureModal, measureContent)
                })
            })
        })
    }

    waitForMessageDetailImages = function (measureContent, callback) {
        var images = Array.prototype.filter.call(measureContent.querySelectorAll("img"), function (image) {
            return !image.complete
        })
        if (images.length === 0) {
            callback()
            return
        }
        var remaining = images.length
        var finished = false
        var finish = function () {
            if (finished) {
                return
            }
            remaining--
            if (remaining <= 0) {
                finished = true
                clearTimeout($viewContent[0].detailMeasureImageTimer)
                callback()
            }
        }
        images.forEach(function (image) {
            image.addEventListener("load", finish, {once: true})
            image.addEventListener("error", finish, {once: true})
        })
        $viewContent[0].detailMeasureImageTimer = setTimeout(function () {
            if (!finished) {
                finished = true
                callback()
            }
        }, 800)
    }

    completeMessageDetailMeasurement = function (measureModal, measureContent) {
        if (!$viewContent[0].detailModalOpening
            || $viewContent[0].detailMeasureModal !== measureModal) {
            return
        }
        var initialModalHeight = $viewModal[0].offsetHeight
        var initialScrollHeight = $viewContentScroll[0].offsetHeight
        var measuredModalHeight = measureModal.offsetHeight
        var targetModalHeight = Math.min(initialModalHeight,
            measuredModalHeight || initialModalHeight)
        var targetScrollHeight = Math.max(0,
            targetModalHeight - (initialModalHeight - initialScrollHeight))
        var preparedContent = document.createDocumentFragment()
        while (measureContent.firstChild) {
            preparedContent.appendChild(measureContent.firstChild)
        }
        removeMessageDetailMeasurement()
        startMessageDetailHeightResize(targetModalHeight, targetScrollHeight, preparedContent)
    }

    startMessageDetailHeightResize = function (targetModalHeight, targetScrollHeight, preparedContent) {
        var initialModalHeight = $viewModal[0].offsetHeight
        var initialScrollHeight = $viewContentScroll[0].offsetHeight
        $viewModal.css("height", initialModalHeight + "px")
        $viewContentScroll.css("height", initialScrollHeight + "px")
        $viewModal.addClass("message-detail-resizing")
        $viewModal[0].offsetHeight

        var resizeFinished = false
        var finishResize = function () {
            if (resizeFinished) {
                return
            }
            resizeFinished = true
            if (!$viewContent[0].detailModalOpening) {
                return
            }
            clearTimeout($viewContent[0].detailResizeTimer)
            $viewModal.off("transitionend.messageDetailResize")
            $viewModal.removeClass("message-detail-resizing")
            mountPreparedMessageDetail(preparedContent)
        }
        if (initialModalHeight - targetModalHeight <= 8
            || (window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches)) {
            $viewModal.css("height", targetModalHeight + "px")
            $viewContentScroll.css("height", targetScrollHeight + "px")
            requestAnimationFrame(finishResize)
            return
        }
        $viewModal.off("transitionend.messageDetailResize")
        $viewModal.on("transitionend.messageDetailResize", function (event) {
            if (event.target === $viewModal[0] && event.originalEvent.propertyName === "height") {
                finishResize()
            }
        })
        $viewContent[0].detailResizeTimer = setTimeout(finishResize, 360)
        $viewContent[0].detailResizeFrame = requestAnimationFrame(function () {
            $viewModal.css("height", targetModalHeight + "px")
            $viewContentScroll.css("height", targetScrollHeight + "px")
        })
    }

    mountPreparedMessageDetail = function (preparedContent) {
        if (!$viewContent[0].detailModalOpening) {
            return
        }
        $viewContent[0].innerHTML = ""
        $viewContent[0].appendChild(preparedContent)
        addCopyButtonToPre($viewContent[0])
        $viewContentScroll.addClass("message-detail-prepared")
        $viewContent[0].detailRevealFrame = requestAnimationFrame(function () {
            $viewContent[0].detailRevealReadyFrame = requestAnimationFrame(function () {
                if (!$viewContent[0].detailModalOpening) {
                    return
                }
                $viewContentScroll.addClass("message-detail-content-ready")
                if (window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
                    finishMessageDetailTransition()
                    return
                }
                $viewContent[0].detailRevealTimer = setTimeout(finishMessageDetailTransition, 280)
            })
        })
    }

    finishMessageDetailTransition = function () {
        if (!$viewContent[0].detailModalOpening
            || !$viewContentScroll.hasClass("message-detail-prepared")) {
            return
        }
        clearTimeout($viewContent[0].detailRevealTimer)
        clearTimeout($viewContent[0].detailResizeTimer)
        $viewContentScroll.children(".message-detail-skeleton").remove()
        $viewContentScroll.removeClass("message-detail-loading message-detail-prepared message-detail-content-ready")
        $viewContentScroll.removeAttr("aria-busy aria-label")
    }

    removeMessageDetailMeasurement = function () {
        clearTimeout($viewContent[0].detailMeasureImageTimer)
        cancelAnimationFrame($viewContent[0].detailMeasureFrame)
        cancelAnimationFrame($viewContent[0].detailMeasureReadyFrame)
        var measureModal = $viewContent[0].detailMeasureModal
        if (measureModal && measureModal.parentNode) {
            measureModal.parentNode.removeChild(measureModal)
        }
        $viewContent[0].detailMeasureModal = null
    }

    clearMessageDetailTransition = function () {
        clearTimeout($viewContent[0].detailRenderTimer)
        clearTimeout($viewContent[0].detailResizeTimer)
        clearTimeout($viewContent[0].detailRevealTimer)
        cancelAnimationFrame($viewContent[0].detailRenderFrame)
        cancelAnimationFrame($viewContent[0].detailResizeFrame)
        cancelAnimationFrame($viewContent[0].detailRevealFrame)
        cancelAnimationFrame($viewContent[0].detailRevealReadyFrame)
        $viewModal.off("transitionend.messageDetailResize")
        removeMessageDetailMeasurement()
        $viewModal.removeClass("message-detail-adaptive message-detail-resizing")
        $viewModal.css("height", "")
        $viewContentScroll.css("height", "")
        $viewContentScroll.children(".message-detail-skeleton").remove()
        $viewContentScroll.removeClass("message-detail-loading message-detail-prepared message-detail-content-ready")
        $viewContentScroll.removeAttr("aria-busy aria-label")
    }

    buildHighlightContentInner = function (content) {
        if (isMarkdown(content) || hasMarkdownMath(content)) {
            $viewContent.removeClass("view-content")
            $copyViewBtn[0].copyContent = content
            return marked.parse(content)
        }

        const codeObj = hljs.highlightAuto(content)
        // 主流语言，显示用pre方便看
        let isCommonCode = codeObj.language === 'java' ||
            codeObj.language === 'python' ||
            codeObj.language === 'cpp' ||
            codeObj.language === 'kotlin' ||
            codeObj.language === 'c' ||
            codeObj.language === 'csharp' ||
            codeObj.language === 'javascript' ||
            codeObj.language === 'xml' ||
            codeObj.language === 'php' ||
            codeObj.language === 'perl'
        // 只有关键字的文本，不需要按照代码格式展示
        isCommonCode = isCommonCode && (content.includes("{") || content.includes("}") || content.includes(":"))
        if (isCommonCode) {
            $viewContent.addClass("view-content")
        } else {
            $viewContent.removeClass("view-content")
        }

        $copyViewBtn[0].copyContent = content
        const divBlock = document.createElement("div");
        divBlock.innerHTML = content
        hljs.highlightElement(divBlock)
        return divBlock.innerHTML
    }

    //增加一条我方记录
    addMessage = function ($chatMsg, content, isMain) {

        //拼装dom
        var mainDoc = messageDom({
            content: content,
            createTime: (new Date()).valueOf()
        }, isMain);

        //添加dom
        $chatMsg.append(mainDoc);

        //滚动
        scrollToChatContainerBottom(100)
    }

    //删除所有记录
    deleteAllMessage = function () {
        $chatMsg.remove();
    }

    // 判断输入是否完成
    var isInputFinished = true
    $($chatInput).bind("keyup", function (ev) {
        if (ev.keyCode == "13" && isInputFinished) {
            $send.click();
        }
    });
    // 判断输入是否结束
    $chatInput.addEventListener('compositionstart', function (e) {
        isInputFinished = false;
    }, false)
    $chatInput.addEventListener('compositionend', function (e) {
        setTimeout(() => {
            isInputFinished = true
        }, 100)
    }, false)


    $(document).on("click", ".send", function () {
        //获取文本框内容
        var content = $chatInput.value;
        if (content === "") {
            // swal("stop!","输入不能为空","warning");
            showToast("输入不能为空", 1000)
            return;
        }

        if (isSessionLoading()) {
            showToast("会话加载中，请稍候", 1000)
            return;
        }
        
        if (queryStreamDom(getActiveSessionId())
            && (!getActiveChatter() || getActiveChatter().robotGroup !== 'acp')) {
            showToast("当前状态无法发送新消息，请先终止当前会话", 1000)
            return;
        }

        //清空文本框
        $chatInput.value = "";

        //自动获取焦点
        $chatInput.focus();

        //显示在屏幕上，滚动
        sendMessageInner(content)
    });

    // 明细模态框初始化
    $viewModal.modal({
        dismissible: true, // Modal can be dismissed by clicking outside of the modal
        opacity: .2, // Opacity of modal background
        in_duration: 300, // Transition in duration
        out_duration: 200, // Transition out duration
        starting_top: '4%', // Starting top style attribute
        ending_top: '100%', // Ending top style attribute
        ready: function (modal, trigger) { // Callback for Modal open. Modal and trigger parameters available.
            $viewContent[0].detailModalReady = true
            var pendingFullContentRender = $viewContent[0].pendingFullContentRender
            $viewContent[0].pendingFullContentRender = null
            if (pendingFullContentRender) {
                pendingFullContentRender()
            }
            fetchContextUsage()
        },
        complete: function () {
            $viewContent[0].detailModalReady = false
            $viewContent[0].detailModalOpening = false
            $viewContent[0].pendingFullContentRender = null
            clearMessageDetailTransition()
            originalCodes.splice(0, originalCodes.length)
            $('#contextUsageWrapper').hide()
        }
    });

    $copyViewBtn.on('click', function (e) {
        copyText(this.copyContent)
    })

    var $editModal = $("#message-edit-modal")
    var $openEditBtn = $("#open-text-btn")

    $editModal.modal({
        dismissible: true, // Modal can be dismissed by clicking outside of the modal
        opacity: .2, // Opacity of modal background
        in_duration: 300, // Transition in duration
        out_duration: 200, // Transition out duration
        starting_top: '4%', // Starting top style attribute
        ending_top: '100%', // Ending top style attribute
    });

    // dom初始化位置
    $editModal.css("max-width", 800)
    if (getInnerWidth() > 800) {
        $editModal.css("left", (getInnerWidth() - $editModal.innerWidth()) / 2)
    }

    addResizeEventListener(function () {
        if (getInnerWidth() > 800) {
            $editModal.css("left", (getInnerWidth() - $editModal.innerWidth()) / 2)
        } else {
            $editModal.css("left", 0)
        }
    })

    var $chatEditor = $("#chatEditor")
    $chatEditor.keydown(function (e) {
        if (e.keyCode === 9) { // tab was pressed
            // get caret position/selection
            var start = this.selectionStart;
            var end = this.selectionEnd;

            var $this = $(this);
            var value = $this.val();

            // set textarea value to: text before caret + tab + text after caret
            $this.val(value.substring(0, start) +
                "\t" +
                value.substring(end));

            // put caret at right position again (add one for the tab)
            this.selectionStart = this.selectionEnd = start + 1;

            // prevent the focus lose
            e.preventDefault();
        }
    });
    $openEditBtn.on('click', function () {
        $editModal.removeData('cmd');
        $editModal.removeData('cmdDesc');
        $('#cmdDescContainer .cmd-card').hide();
        $editModal.modal('open')
        if ($chatEditor.val() === '' && $chatInput.value !== '') {
            $chatEditor.val($chatInput.value)
        }
    })

    var $editCompleteBtn = $("#editCompleteBtn")
    $editCompleteBtn.on('click', function () {
        // 获取cmd并拼接
        let content = $chatEditor.val()
        const cmd = $editModal.data('cmd');
        if (cmd) {
            content = cmd + " " + content;
        }

        if (content === "") {
            // swal("stop!","输入不能为空","warning");
            showToast("输入不能为空", 1000)
            return;
        }

        if (queryStreamDom(getActiveSessionId())
            && (!getActiveChatter() || getActiveChatter().robotGroup !== 'acp')) {
            showToast("当前状态无法发送新消息", 1000)
            return;
        }

        //清空文本框
        $chatEditor.val('')
        $chatInput.value = "";

        // 清空cmd和cmdDesc
        $editModal.removeData('cmd');
        $editModal.removeData('cmdDesc');
        $('#cmdDescContainer .cmd-card').hide();

        sendMessageInner(content)
    })

    // 支持 Enter 触发发送，Shift+Enter 触发换行（App 内回车始终换行）
    $chatEditor.on('keydown', function (e) {
        if (e.keyCode === 13) {
            if (isAppNow()) {
                // App 内回车不发送，允许默认换行行为
                return;
            }
            if (e.shiftKey) {
                // Shift+Enter 允许默认换行行为
            } else {
                // Enter 触发发送，并阻止默认换行
                e.preventDefault();
                $editCompleteBtn.click();
            }
        }
    });

    sendMessageInner = function (content) {
        // 编辑器等其他发送入口也必须避免使用上一个 sessionId。
        if (isSessionLoading()) {
            showToast("会话加载中，请稍候", 1000)
            return
        }

        //显示在屏幕上，滚动
        addMessage($chatMsg, content, true);

        //获取socket
        var socket = getSocket();
        //构建message对象
        var action = new Object();
        action.code = SEND_MESSAGE;
        action.msg = "ok";
        var data = new Object();
        data.chatterId = getChatterId();
        data.sessionId = getActiveSessionId();
        data.content = content;
        action.data = data;

        socket.send(JSON.stringify(action));
    }

    fetchContextUsage = function () {
        var $wrapper = $('#contextUsageWrapper')
        var chatter = getActiveChatter()
        if (!chatter || (chatter.robotGroup !== 'acp' && chatter.robotGroup !== 'team-acp')) {
            $wrapper.hide()
            return
        } else {
            var circle = document.getElementById('contextUsageCircle')
            var text = document.getElementById('contextUsageText')
            circle.setAttribute('stroke-dasharray', '0 100')
            circle.setAttribute('stroke', '#4caf50')
            text.textContent = ''
            $wrapper.show()
        }
        $.ajax({
            url: getPrefix() + "/chat/robot/context-usage/" + getActiveSessionId(),
            type: "get",
            dataType: "json",
            timeout: 3000,
            success: function (result) {
                if (!result || (result.data == null)) {
                    $wrapper.hide()
                    return
                }
                var pct = Math.max(0, Math.min(Math.round(result.data), 100))
                var circle = document.getElementById('contextUsageCircle')
                var text = document.getElementById('contextUsageText')
                requestAnimationFrame(function () {
                    circle.setAttribute('stroke-dasharray', pct + ' 100')
                    circle.setAttribute('stroke', pct < 60 ? '#4caf50' : pct < 85 ? '#ff9800' : '#f44336')
                    text.textContent = pct + '%'
                })
                $wrapper.attr('title', '上下文用量: ' + pct + '%')
            },
            error: function () {
                $wrapper.hide()
            }
        })
    }

    popupAndSendCmd = function(cmd, args, cmdDesc) {
        const bytes = Uint8Array.from(atob(args), c => c.charCodeAt(0));
        args = new TextDecoder().decode(bytes);
        // 存储cmd和cmdDesc到editModal
        $editModal.data('cmd', cmd);
        $editModal.data('cmdDesc', cmdDesc);
        // 显示cmdDesc
        $('#cmdDescContainer .cmd-card').show();
        $('#cmdDescContainer .cmd-card').text(cmdDesc);
        $editModal.modal('open')
        if (args && args.length !== 0) {
            $chatEditor.val(args)
        }
    }
});
