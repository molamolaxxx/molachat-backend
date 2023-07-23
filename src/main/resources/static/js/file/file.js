// 文件发送逻辑
$(document).ready(function () {

    $chatMsg = $(".chat__messages")[0];
    chatDom = $(".chat")[0]
    fileDom = function (message, isUpload, isMain, uploadId, url, snapshotUrl) {
        const fileRender = getRender(url)
        return fileRender({
            message,
            isUpload,
            isMain,
            uploadId,
            url,
            snapshotUrl
        })
    }

    var doUploadFileAndSendMessage = function (file, rid) {
        if (file == null) {
            return;
        }
        var form = new FormData();
        var url = getPrefix() + "/chat/files/upload";
        var xhr = new XMLHttpRequest();
        form.append("file", file);
        form.append("sessionId", getActiveSessionId());
        form.append("chatterId", getChatterId());
        form.append("token", localStorage.getItem("token"));

        xhr.open("post", url, true);
        xhr.currentUploadFileId = rid;
        xhr.withCredentials = true

        //上传进度事件
        xhr.upload.addEventListener("progress", function (result) {
            if (result.lengthComputable) {
                //上传进度
                var percent = (result.loaded / result.total * 100).toFixed(2);
                console.info(xhr.currentUploadFileId + ":" + percent);
                if (xhr.currentUploadFileId != null) {
                    $("#" + xhr.currentUploadFileId).css("width", percent + "%")
                }
                window.uploadLock = true;
            }
        }, false);

        xhr.addEventListener("readystatechange", function () {
            var result = xhr;
            if (result.status != 200) { //error
                console.log('上传失败', result.status, result.statusText, result.response);
                //文件名变红
                $("#src" + xhr.currentUploadFileId).css("color", "rgb(255, 11, 11)");
                $("#src" + xhr.currentUploadFileId)[0].innerText = file.name + "\n(failed)"
                //删掉取消键
                $("#cancel" + xhr.currentUploadFileId).css("display", "none");
                //恢复文件大小
                $("#img" + xhr.currentUploadFileId).css("margin-left", "0rem");

                //swal("error", "上传文件失败", "error");
            } else if (result.readyState == 4) { //finished
                console.log('上传成功', result);
                //删掉取消键
                $("#cancel" + xhr.currentUploadFileId).css("display", "none");
                //恢复文件大小
                $("#img" + xhr.currentUploadFileId).css("margin-left", "0rem");
                // swal("success", "发送成功！", "success");
                showToast("发送成功",1000)
                var url = result.data;
            }
            window.uploadLock = false;
        });
        xhr.send(form); //开始上传
        //上传，锁住
        window.uploadLock = true;

        //添加dom
        var dom = fileDom({
                fileName: file.name,
                createTime: (new Date()).valueOf()
            },
            true, true, xhr.currentUploadFileId,
            "javascript:;", "javascript:;");

        $chatMsg.append(dom);

        //滚动
        scrollToChatContainerBottom(500)
        //设置相关监听器 1.点击取消上传监听 2.鼠标移动放大监听
        $("#cancel" + xhr.currentUploadFileId).on("click", function () {
            swal({
                title: "Warning",
                text: "需要终止文件上传吗?",
                icon: "warning",
                buttons: true,
                dangerMode: true,
            }).then((willDelete) => {
                if (willDelete) {
                    //如果上传已经完毕，则不能取消
                    if ($("#" + xhr.currentUploadFileId).css("width") == $(".progress").css("width")) {
                        swal("finish", "上传已经完成，无法取消", "warning")
                        return;
                    }
                    xhr.abort();
                    //删掉取消键
                    $("#cancel" + xhr.currentUploadFileId).css("display", "none");
                    //恢复文件大小
                    $("#img" + xhr.currentUploadFileId).css("margin-left", "0rem");
                    //文件名变黄
                    $("#src" + xhr.currentUploadFileId).css("color", "#e69200");
                    $("#src" + xhr.currentUploadFileId)[0].innerText = file.name + "\n(cancel)"
                    swal("success", "文件已经终止上传", "success").then(() => {
                        window.uploadLock = false;
                    });

                }
            });
        });

        $("#cancel" + xhr.currentUploadFileId).on("mouseover", function () {
            $("#cancel" + xhr.currentUploadFileId).animate({
                width: '3rem'
            });
        });
        $("#cancel" + xhr.currentUploadFileId).on("mouseout", function () {
            $("#cancel" + xhr.currentUploadFileId).animate({
                width: '1.2rem'
            });
        });
    }


    var uploadFileFunc = function () {
        var $fileInput = $("#file-input")[0];
        $fileInput.click();
        var fileInput = document.querySelector("#file-input");
        fileInput.onchange = function () {
            var file = this.files[0];
            doUploadFileAndSendMessage(file, genRid())
        }
        $fileInput.value = null
    }

    var genRid = function () {
        //正在上传的文件id
        rid = ""
        var str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (var i = 0; i < 5; i++) {
            rid += str[Math.round(Math.random() * 61)]
        }
        return rid
    }

    // 文件拖拽
    $('.chat').on('dragover', function (event) {
        event.preventDefault()
    }).on('drop', function (event) {
        if (!chatDom.className === "chat active") return
        console.log("拖拽上传")
        event.preventDefault();
        //数据在event的dataTransfer对象里
        let file = event.originalEvent.dataTransfer.files[0];
        doUploadFileAndSendMessage(file, genRid())

    })

    /**
     * 图片粘贴事件处理
     */
    $('input.chat__input').on('paste', function (event) {
        if (!chatDom.className === "chat active") return
        var items = null
        if (event.originalEvent && event.originalEvent.clipboardData) {
            items = event.originalEvent.clipboardData.items
        }
        if (!items || items.length === 0) {
            return
        }
        var len = items.length,
            blob = null;
        for (var i = 0; i < len; i++) {
            if (items[i].kind.indexOf("file") !== -1) {
                //getAsFile() 此方法只是living standard firefox ie11 并不支持
                blob = items[i].getAsFile();
                console.info("paste file", blob)
                doUploadFileAndSendMessage(blob, genRid())

                // 阻止事件冒泡
                event.preventDefault();
            }
        }

    })


    $("#file_copy").on("click", uploadFileFunc);
    $("#tool-file").on("click", uploadFileFunc);
});