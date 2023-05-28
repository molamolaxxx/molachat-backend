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

    function gifFileRender() {

    }

    var func = function () {
        $("#materialbox-overlay").unbind("click")
        if (holder[0].style.display !== "none") {
            // body缩放，为了适配图片缩放
            // document.body.style.zoom=1.1
            holder.css("display", "none")
        } else {
            holder.css("display", "")
        }
        open = !open
        lock = true;
    }
    holder.on('click', func);

    syncToHolder = function (url) {
        console.log(open)
        holder[0].src = url
        // // body缩放，为了适配图片缩放
        // document.body.style.zoom=1
        holder[0].click()
        if (open) {
            $("#materialbox-overlay").on('click', () => {
                console.log("materialbox-overlay")
                holder[0].click()
            })
        }
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