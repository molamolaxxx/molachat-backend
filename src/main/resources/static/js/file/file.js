// 文件发送逻辑
$(document).ready(function() {

    $chatMsg = $(".chat__messages")[0];
    chatDom = $(".chat")[0]
    //发送文件的dom
    // <div class="chat__msgRow"><img src="img/header/15.jpeg" class="contact__photo"
    //         style="float: right; display: inline; margin-right: 0rem;">
    //     <div class="chat__message notMine" style="margin-right: 0.5rem;">
    //         <img src="img/file.svg" style="
    //         width: 6rem;
    //     ">
    //         <div class="progress">
    //             <div class="determinate" style="width: 70%"></div>
    //         </div>
    //         <a style="
    //         display: block;
    //         text-align: center;
    //     ">学习资料.java</a>
    //     </div>
    // </div>
    fileDom = function(message, isUpload, isMain, uploadId, url, snapshotUrl) {
        // 时间dom
        let timeDoc = timeDom(message.createTime)
        if (timeDoc) {
            $chatMsg.append(timeDoc)
        }
        let filename = message.fileName
        if (url !== 'javascript:;'){
            url = getPrefix() + url
        }
        if (snapshotUrl !== 'javascript:;'){
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
        if (isImg(url)) {
            // 是图片
            fileImg.src = snapshotUrl
            // 同步图片到holder，显示
            $(fileImg).on('click', function() {
                syncToHolder(snapshotUrl)
            })
            $(fileImg).addClass("imgFile");
            // $(fileImg).addClass("materialboxed");
        } else {
            fileImg.src = "img/file.svg"
            $(fileImg).css("width", "6rem")
        }
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
        if (url !== 'javascript:;'){
            fileSrc.target = "_blank";
        }
        fileSrc.id = "src" + uploadId;
        mainDocChild.append(fileSrc);
        mainDoc.append(mainDocChild);
        return mainDoc;
    }

    // 将图片同步到holder，再放大
    var holder = $("#imgHolder")
    var overlay = $("#materialbox-overlay")
    var open = false
    var lock = false;
    var func = function(){
        $("#materialbox-overlay").unbind("click")
        if (holder[0].style.display !== "none") {
            // body缩放，为了适配图片缩放
            // document.body.style.zoom=1.1
            holder.css("display","none")
        } else {
            holder.css("display","")
        }     
        open = !open
        lock = true;
    }
    holder.on('click', func);
    
    syncToHolder = function(url) {
        console.log(open)
        holder[0].src = url
        // // body缩放，为了适配图片缩放
        // document.body.style.zoom=1
        holder[0].click()
        if (open) {
            $("#materialbox-overlay").on('click',() => {
                console.log("materialbox-overlay")
                holder[0].click()
            })
        }
    }
    var doUploadFileAndSendMessage = function(file, rid) {
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
        xhr.upload.addEventListener("progress", function(result) {
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

        xhr.addEventListener("readystatechange", function() {
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
                swal("success", "发送成功！", "success");
                var url = result.data;
            }
            window.uploadLock = false;
        });
        xhr.send(form); //开始上传
        //上传，锁住
        window.uploadLock = true;

        //添加dom
        var dom = fileDom({fileName:file.name,createTime:(new Date()).valueOf()}, 
            true, true, xhr.currentUploadFileId, 
            "javascript:;","javascript:;");
                 
        $chatMsg.append(dom);
        
        //滚动
        setTimeout(()=> {
            document.querySelector(".chat__messages").scrollBy({ top: 12500, left: 0, behavior: 'smooth' });
        },100)
        //设置相关监听器 1.点击取消上传监听 2.鼠标移动放大监听
        $("#cancel" + xhr.currentUploadFileId).on("click", function() {
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
                    swal("success", "文件已经终止上传", "success").then(()=>{
                        window.uploadLock = false;
                    });
                    
                }
            });
        });

        $("#cancel" + xhr.currentUploadFileId).on("mouseover", function() {
            $("#cancel" + xhr.currentUploadFileId).animate({ width: '3rem' });
        });
        $("#cancel" + xhr.currentUploadFileId).on("mouseout", function() {
            $("#cancel" + xhr.currentUploadFileId).animate({ width: '1.2rem' });
        });
    }


    var uploadFileFunc = function() {
        var $fileInput = $("#file-input")[0];
        $fileInput.click();
        var fileInput = document.querySelector("#file-input");
        fileInput.onchange = function() {
            var file = this.files[0];
            doUploadFileAndSendMessage(file, genRid())
        }
        $fileInput.value = null
    }

    var genRid = function() {
        //正在上传的文件id
        rid = ""
        var str = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        for (var i = 0; i < 5; i++) {
            rid += str[Math.round(Math.random() * 61)]
        }
        return rid
    }

    // 文件拖拽
    $('.chat').on('dragover',function(event){
        event.preventDefault()
    }).on('drop',function(event){
        if (!chatDom.className === "chat active") return
        console.log("拖拽上传")
        event.preventDefault();
        //数据在event的dataTransfer对象里
        let file = event.originalEvent.dataTransfer.files[0];
        doUploadFileAndSendMessage(file, genRid())
  
   })

    $("#file_copy").on("click", uploadFileFunc);
    $("#tool-file").on("click", uploadFileFunc);
    // 判断文件是否是图片
    //图片文件的后缀名
    var imgExt = new Array(".png",".jpg",".jpeg",".bmp",".gif");

    //判断是否图片文件
    isImg = function(filename){
        var ext = null;
        var name = filename.toLowerCase();
        var i = name.lastIndexOf(".");
        if(i > -1){
            var ext = name.substring(i);
        } else {
            return false
        }
        for(var i=0; i<ext.length; i++){
            if(imgExt[i] === ext)
                return true;
        }
        return false;
    }
});