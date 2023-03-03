// 群聊逻辑
$(document).ready(function () {
    //const，创建session
    const CREATE_SESSION = 220;

    var $friend_list = $(".friend-list")[0];
    var $copyViewBtn = $('#copyViewBtn')
    var $viewContent = $("#viewContent")
    var $viewModal = $("#message-view-modal")

    $chatMsg = $(".chat__messages")[0];

    // 点击事件
    var menu = $("#menu")
    $(document).on('click', "#menu-icon", function(e) {
        openFAB(menu)
    })

    

    $(document).on('click', '#multichat', function(e) {
          // 群组的信息
        const groupInfo = {
            name : "大家随便聊聊",
            signature : "这里是MOLA的公共聊天室",
            sessionId : "common-session",
            imgUrl : "img/mola.png",
            hint : "已进入群聊会话，可以畅所欲言"
        }
        onStartGroupSession(groupInfo)
    });

    /**
     * 主方法，进入群聊会话
     * @param {*} groupInfo 
     */
    function onStartGroupSession(groupInfo) {
        enterMutiChat(groupInfo);
        closeFAB(menu)
        $('.tooltipped').tooltip('remove'); 
        showToast(groupInfo.hint, 1800)  
    }

    // 进入群聊区域
    function enterMutiChat(groupInfo) {
        // 在好友区添加一个空白，然后点击
        var dom = chatterDom(
            groupInfo.name, 
            groupInfo.imgUrl, 
            false, 
            "", 
            "");
        dom.id = "mutichat-dom"
        
        $friend_list.append(dom)
        let $tempDom = $("#mutichat-dom");
        let photo = $(".contact__photo");
        
        // 头像过度动画
        photo.animate({opacity:0},200)
        setTimeout(function() {
            $(".contact__photo").css({opacity:1})
        },1000)
        $tempDom.click()
        $tempDom.remove()
        // 清空区域，初始化签名
        initMutiChat(groupInfo);
        // ui结束，切换会话
    }

    function initMutiChat(groupInfo) {
        //设置当前chatter为公共chatter
        var activeChatter = {
            createTime: 99999999,
            id: "temp-chatter",
            imgUrl: groupInfo.imgUrl,
            ip: "127.0.0.1",
            name: groupInfo.name,
            signature : groupInfo.signature,
            status: 1,
            isTemp:true
        };
        setActiveChatter(activeChatter);
        // 设置签名
        var sign = cutStrByByte(activeChatter.signature, 28);
        $(".chat__status").text(sign);
        //获取session
        var socket = getSocket();
        var action = new Object();
        action.code = CREATE_SESSION;
        action.msg = "ok";
        // 发送公共chatter的固定sessionId
        action.data = groupInfo.sessionId;
        //向服务器发送数据
        socket.send(JSON.stringify(action));
    }

    /**
     * dom区域 内容 、是否是自己、chatter
     */
    commonMessageDom = function (message, isMain ,chatter) {
        // 时间dom
        let timeDoc = timeDom(message.createTime)
        if (timeDoc) {
            $chatMsg.append(timeDoc)
        }
        let content = message.content
        //拼装dom
        var mainDoc = document.createElement("div");
        $(mainDoc).addClass("chat__msgRow");

        var mainDocChild = document.createElement("div")
        var commonName = document.createElement("div")

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
    
        }
        else {
            commonName.innerText = chatter.name
            imgDoc.src = chatter.imgUrl;
            $(imgDoc).addClass("contact__photo");
            $(imgDoc).css('float', 'left');
            $(imgDoc).css('display', 'inline');
            $(imgDoc).css('margin-right', '0rem');

            $(mainDocChild).css('margin-left', '0.5rem');
            $(mainDocChild).addClass("chat__message mine");

            $(commonName).addClass("chat-common-left")
        }

        $(mainDocChild).css('position', 'relative');
        mainDoc.append(imgDoc);
        // mainDocChild.innerHTML = twemoji.parse(content,{"folder":"svg","ext":".svg","base":"asset/","size":15});
        mainDocChild.innerText = content.length > 200 ? content.slice(0,200) + "\n...." : content
        mainDoc.append(commonName);
        mainDoc.append(mainDocChild);

        // 明细view
        if (content.length > 80) {
            var copyIcon = document.createElement("span");
            $(copyIcon).addClass("copy_icon");
            $(copyIcon).css('position', 'absolute');
            $(copyIcon).css('right', '0');
            $(copyIcon).css('bottom', '0');
            $(copyIcon).css('padding', '4px');
            $(copyIcon).css('cursor', 'pointer');
            const onClickCallback = (e) => {
                const codeObj = hljs.highlightAuto(content)
                // 主流语言，显示用pre方便看
                let isCommonCode = codeObj.language === 'java' 
                || codeObj.language === 'python'
                || codeObj.language === 'cpp' 
                || codeObj.language === 'kotlin'
                || codeObj.language === 'c'
                || codeObj.language === 'csharp'
                || codeObj.language === 'javascript'
                || codeObj.language === 'xml'
                || codeObj.language === 'php'
                || codeObj.language === 'perl'
                // 只有关键字的文本，不需要按照代码格式展示
                isCommonCode = isCommonCode && (content.includes("{") || content.includes("}") || content.includes(":"))
                if (isCommonCode) {
                    $viewContent.addClass("view-content")
                } else {
                    $viewContent.removeClass("view-content")
                }
                $copyViewBtn[0].copyContent = content
                const divBlock = document.createElement("div");
                if (isMarkdown(content)) {
                    console.log("is markdown");
                    divBlock.innerHTML = marked.parse(content);
                } else {
                    divBlock.innerHTML = content
                }
                hljs.highlightElement(divBlock)
                $viewContent[0].innerHTML = divBlock.innerHTML
                $viewModal.modal('open')
            }
            $(copyIcon).on('click', onClickCallback)
            $(mainDocChild).on('click', onClickCallback)
            $(mainDocChild).css("cursor", "pointer")
            copyIcon.innerHTML = '<i class="material-icons" style="font-size: 15px;color: #868e8a;">launch</i>'
            mainDocChild.append(copyIcon)
        }
        return mainDoc;
    }

    commonFileDom = function(message, isUpload, isMain, uploadId, url,snapshotUrl, chatter) {
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
        var commonName = document.createElement("div")
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

            imgDoc.src = chatter.imgUrl;
            commonName.innerText = chatter.name
            $(imgDoc).addClass("contact__photo");
            $(imgDoc).css('float', 'left');
            $(imgDoc).css('display', 'inline');
            $(imgDoc).css('margin-right', '0rem');

            $(mainDocChild).css('margin-left', '0.5rem');
            $(mainDocChild).css('text-align', 'center');
            $(mainDocChild).addClass("chat__message mine");

            $(commonName).addClass("chat-common-left")
        }
        mainDoc.append(imgDoc)
        mainDoc.append(commonName)
        //添加取消图片
        var cancelImg = document.createElement("img");
        cancelImg.id = "cancel" + uploadId
        cancelImg.src = "img/close-circle.svg"
        $(cancelImg).css("width", "1.2rem")
        $(cancelImg).css("float", "right")
        //添加文件图片
        var imgLink = document.createElement("a");
        //imgLink.href = url;
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
})