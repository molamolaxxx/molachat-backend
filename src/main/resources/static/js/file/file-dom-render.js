/**
 * 文件dom渲染器，根据不同的文件类型渲染
 */
$(document).ready(function () {

    $chatMsg = $(".chat__messages")[0];
    chatDom = $(".chat")[0]
    // 图片放大器
    var holder = $("#imgHolder")
    var open = false
    var lock = false;

    /**
     * 获取render
     */
    getRender = function (url) {
        if (isImg(url)) {
            return imageFileRender;
        }
        return normalFileRender;
    }

    function normalFileRender(dataForRender) {
        let {
            message,
            isUpload,
            isMain,
            uploadId,
            url,
            snapshotUrl,
            chatter
        } = dataForRender
        // 时间dom
        let timeDoc = timeDom(message.createTime)
        if (timeDoc) {
            $chatMsg.append(timeDoc)
        }
        let filename = message.fileName
        if (url !== 'javascript:;') {
            url = getPrefix() + url
        }
        if (snapshotUrl !== 'javascript:;') {
            snapshotUrl = getPrefix() + snapshotUrl
        }
        var mainDoc = document.createElement("div");
        $(mainDoc).addClass("chat__msgRow");

        var mainDocChild = document.createElement("div");

        var imgDoc = document.createElement("img");

        if (isMain) {
            //头像img
            imgDoc.src = getChatterImage();
            $(imgDoc).addClass("contact__photo");
            $(imgDoc).css('float', 'right');
            $(imgDoc).css('display', 'inline');
            $(imgDoc).css('margin-right', '0rem');

            $(mainDocChild).css('margin-right', '0.5rem');
            $(mainDocChild).css('text-align', 'center');
            $(mainDocChild).addClass("chat__message notMine");
        } else {
            imgDoc.src = getActiveChatter().imgUrl;
            $(imgDoc).addClass("contact__photo");
            $(imgDoc).css('float', 'left');
            $(imgDoc).css('display', 'inline');
            $(imgDoc).css('margin-right', '0rem');

            $(mainDocChild).css('margin-left', '0.5rem');
            $(mainDocChild).css('text-align', 'center');
            $(mainDocChild).addClass("chat__message mine");
        }

        mainDoc.append(imgDoc)

        // 群聊 名称显示
        if (chatter && !isMain) {
            // 名称
            var showName = document.createElement("div")
            showName.innerText = chatter.name
            $(showName).addClass("chat-common-left")
            mainDoc.append(showName)
            // 头像
            imgDoc.src = chatter.imgUrl;
        }

        //添加取消图片
        var cancelImg = document.createElement("img");
        cancelImg.id = "cancel" + uploadId
        cancelImg.src = "img/close-circle.svg"
        $(cancelImg).css("width", "1.2rem")
        $(cancelImg).css("float", "right")

        //添加文件图片
        var imgLink = document.createElement("a");
        // imgLink.href = url;
        imgLink.target = "_blank";
        var fileImg = document.createElement("img");
        fileImg.src = "img/file.svg"
        $(fileImg).css("width", "6rem")
        fileImg.id = "img" + uploadId
        imgLink.append(fileImg);

        if (isUpload && uploadId != "ready") {
            $(fileImg).css("margin-left", "1.2rem")
            mainDocChild.append(cancelImg)
        }
        mainDocChild.append(imgLink)

        //添加进度条
        if (isUpload) {
            var progress = document.createElement("div");
            $(progress).addClass("progress");
            progressData = document.createElement("div");
            progressData.id = uploadId;
            $(progressData).addClass("determinate");
            if (uploadId == "ready") {
                $(progressData).css("width", "100%")
            } else {
                $(progressData).css("width", "0%")
            }

            progress.append(progressData);
            mainDocChild.append(progress);
        }

        var fileSrc = document.createElement("a");
        $(fileSrc).css("display", "block");
        $(fileSrc).css("text-align", "center");
        fileSrc.innerText = filename;
        fileSrc.href = url;
        if (url !== 'javascript:;') {
            fileSrc.target = "_blank";
        }
        fileSrc.id = "src" + uploadId;
        mainDocChild.append(fileSrc);
        mainDoc.append(mainDocChild);
        return mainDoc;
    }


    function imageFileRender(dataForRender) {
        let {
            message,
            isUpload,
            isMain,
            uploadId,
            url,
            snapshotUrl,
            chatter
        } = dataForRender
        // 时间dom
        let timeDoc = timeDom(message.createTime)
        if (timeDoc) {
            $chatMsg.append(timeDoc)
        }
        let filename = message.fileName
        if (url !== 'javascript:;') {
            url = getPrefix() + url
        }
        if (snapshotUrl !== 'javascript:;') {
            snapshotUrl = getPrefix() + snapshotUrl
        }
        var mainDoc = document.createElement("div");
        $(mainDoc).addClass("chat__msgRow");

        var mainDocChild = document.createElement("div");

        var imgDoc = document.createElement("img");
        if (isMain) {
            //头像img
            imgDoc.src = getChatterImage();
            $(imgDoc).addClass("contact__photo");
            $(imgDoc).css('float', 'right');
            $(imgDoc).css('display', 'inline');
            $(imgDoc).css('margin-right', '0rem');
            $(mainDocChild).css('margin-right', '0.5rem');
            $(mainDocChild).css('text-align', 'center');
            $(mainDocChild).addClass("chat__message notMine");
        } else {
            imgDoc.src = getActiveChatter().imgUrl;
            $(imgDoc).addClass("contact__photo");
            $(imgDoc).css('float', 'left');
            $(imgDoc).css('display', 'inline');
            $(imgDoc).css('margin-right', '0rem');
            $(mainDocChild).css('margin-left', '0.5rem');
            $(mainDocChild).css('text-align', 'center');
            $(mainDocChild).addClass("chat__message mine");
        }
        mainDoc.append(imgDoc)
        // 群聊 名称显示
        if (chatter && !isMain) {
            // 名称
            var showName = document.createElement("div")
            showName.innerText = chatter.name
            $(showName).addClass("chat-common-left")
            mainDoc.append(showName)
            // 头像
            imgDoc.src = chatter.imgUrl;
        }

        //添加取消图片
        var cancelImg = document.createElement("img");
        cancelImg.id = "cancel" + uploadId
        cancelImg.src = "img/close-circle.svg"
        $(cancelImg).css("width", "1.2rem")
        $(cancelImg).css("float", "right")

        //添加文件图片
        var imgLink = document.createElement("a");
        // imgLink.href = url;
        imgLink.target = "_blank";
        var fileImg = document.createElement("img");
        // 是图片
        fileImg.src = snapshotUrl
        // 同步图片到holder，显示
        $(fileImg).on('click', function () {
            syncToHolder(snapshotUrl)
        })
        $(fileImg).addClass("imgFile");
        fileImg.id = "img" + uploadId
        imgLink.append(fileImg);

        if (isUpload && uploadId != "ready") {
            $(fileImg).css("margin-left", "1.2rem")
            mainDocChild.append(cancelImg)
        }
        mainDocChild.append(imgLink)

        //添加进度条
        if (isUpload) {
            var progress = document.createElement("div");
            $(progress).addClass("progress");
            progressData = document.createElement("div");
            progressData.id = uploadId;
            $(progressData).addClass("determinate");
            if (uploadId == "ready") {
                $(progressData).css("width", "100%")
            } else {
                $(progressData).css("width", "0%")
            }

            progress.append(progressData);
            mainDocChild.append(progress);
        }

        var fileSrc = document.createElement("a");
        $(fileSrc).css("display", "block");
        $(fileSrc).css("text-align", "center");
        fileSrc.innerText = filename;
        fileSrc.href = url;
        if (url !== 'javascript:;') {
            fileSrc.target = "_blank";
        }
        fileSrc.id = "src" + uploadId;
        mainDocChild.append(fileSrc);
        mainDoc.append(mainDocChild);
        return mainDoc;

    }
    
    $(document).on('DOMNodeRemoved', '#materialbox-overlay', function() {
        // 被销毁时触发的回调函数
        console.log("materialbox-overlay 被销毁了");
        if (holder[0].style.display !== "none") {
            holder.css("display", "none")
        }
    });

    var func = function () {
        $("#materialbox-overlay").unbind("click")
        if (holder[0].style.display !== "none") {
            holder.css("display", "none")
        } else {
            holder.css("display", "")
        }
        open = !open
        lock = true;
    }
    holder.on('click', func);

    // 自定义全屏图片预览（替代 materialboxed，兼容 zoom）
    syncToHolder = function (url) {
        var overlay = document.createElement('div')
        overlay.className = 'img-preview-overlay'
        var img = document.createElement('img')
        img.src = url

        var scale = 1, translateX = 0, translateY = 0
        var isDragging = false, dragMoved = false, startX, startY, lastX = 0, lastY = 0

        function updateTransform(animate) {
            if (animate) {
                img.style.transition = 'transform 0.25s ease'
            } else {
                img.style.transition = 'none'
            }
            img.style.transform = 'translate(' + translateX + 'px, ' + translateY + 'px) scale(' + scale + ')'
        }

        // 滚轮缩放
        overlay.addEventListener('wheel', function (e) {
            e.preventDefault()
            var delta = e.deltaY > 0 ? -0.2 : 0.2
            var newScale = Math.min(Math.max(scale + delta, 0.5), 5)
            // 如果缩回1以下，重置位置
            if (newScale <= 1) {
                newScale = 1
                translateX = 0
                translateY = 0
                lastX = 0
                lastY = 0
            }
            scale = newScale
            updateTransform(true)
        }, { passive: false })

        // 双指缩放（移动端）
        var lastPinchDist = 0
        overlay.addEventListener('touchstart', function (e) {
            if (e.touches.length === 2) {
                lastPinchDist = Math.hypot(
                    e.touches[0].clientX - e.touches[1].clientX,
                    e.touches[0].clientY - e.touches[1].clientY
                )
            } else if (e.touches.length === 1) {
                isDragging = true
                dragMoved = false
                startX = e.touches[0].clientX - lastX
                startY = e.touches[0].clientY - lastY
            }
        })
        overlay.addEventListener('touchmove', function (e) {
            if (e.touches.length === 2) {
                e.preventDefault()
                var dist = Math.hypot(
                    e.touches[0].clientX - e.touches[1].clientX,
                    e.touches[0].clientY - e.touches[1].clientY
                )
                if (lastPinchDist > 0) {
                    var delta = (dist - lastPinchDist) * 0.01
                    scale = Math.min(Math.max(scale + delta, 0.5), 5)
                    if (scale <= 1) {
                        scale = 1
                        translateX = 0
                        translateY = 0
                        lastX = 0
                        lastY = 0
                    }
                    updateTransform(false)
                }
                lastPinchDist = dist
            } else if (e.touches.length === 1 && isDragging && scale > 1) {
                dragMoved = true
                translateX = e.touches[0].clientX - startX
                translateY = e.touches[0].clientY - startY
                updateTransform(false)
                e.preventDefault()
            }
        }, { passive: false })
        overlay.addEventListener('touchend', function (e) {
            if (e.touches.length < 2) {
                lastPinchDist = 0
            }
            if (e.touches.length === 0) {
                isDragging = false
                lastX = translateX
                lastY = translateY
            }
        })

        // 鼠标拖动
        overlay.addEventListener('mousedown', function (e) {
            if (scale <= 1) return
            isDragging = true
            dragMoved = false
            startX = e.clientX - lastX
            startY = e.clientY - lastY
            e.preventDefault()
        })
        overlay.addEventListener('mousemove', function (e) {
            if (!isDragging) return
            dragMoved = true
            translateX = e.clientX - startX
            translateY = e.clientY - startY
            updateTransform(false)
        })
        overlay.addEventListener('mouseup', function () {
            isDragging = false
            lastX = translateX
            lastY = translateY
        })

        // 点击关闭（图片外区域，或未放大时点击图片）
        overlay.addEventListener('click', function (e) {
            if (e.target !== img || scale <= 1) {
                document.documentElement.removeChild(overlay)
            }
        })

        overlay.appendChild(img)
        document.documentElement.appendChild(overlay)
    }


    //图片文件的后缀名
    var imgExt = new Array(".png", ".jpg", ".jpeg", ".bmp", ".gif");
    //判断是否图片文件
    isImg = function (filename) {
        var ext = null;
        var name = filename.toLowerCase();
        var i = name.lastIndexOf(".");
        if (i > -1) {
            var ext = name.substring(i);
        } else {
            return false
        }
        for (var i = 0; i < ext.length; i++) {
            if (imgExt[i] === ext)
                return true;
        }
        return false;
    }
})