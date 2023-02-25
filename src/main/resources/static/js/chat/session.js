// 会话相关逻辑
$(document).ready(function () {

    //const
    const CREATE_SESSION = 220;
    //当前正通信的chatter
    var activeChatter;

    //当前通信使用的session
    var activeSession;

    var chatterListDoms = document.getElementsByClassName("contact");

    // 提醒窗，一个用户只提醒一次
    var alertMap = new Map();

    var $chat = $(".chat");

    // 聊天框dom
    var $messageBox = $(".chat__messages")[0]

    function selectMessageDom(message, isMain) {
        var dom;
        if (message.content){
            // 如果是群聊message
            if (message.common) {
                dom = commonMessageDom(message, isMain, getChatterMap().get(message.chatterId));
            }
            // 单聊message
            else {
                dom = messageDom(message, isMain);
            }
        }else{
            // 如果是群聊message
            if (message.common) {
                dom = commonFileDom(message,isMain,isMain,"ready","/chat/"+message.url,"/chat/"+message.snapshotUrl, getChatterMap().get(message.chatterId));
            }
            // 单聊message
            else {
                dom = fileDom(message,isMain,isMain,"ready","/chat/"+message.url,"/chat/"+message.snapshotUrl);
            }
        }
        return dom;
    }

    addSessionListener = function (chatterListData) {
        console.log("添加了监听器");
        for (var i = 0; i < chatterListDoms.length; i++) {
            var dom = chatterListDoms[i];
            dom.index = i;
            dom.addEventListener("click", function () {
                if (window.uploadLock) {
                    return
                }
                //获取当前chatter
                activeChatter = chatterListData[this.index];
                // 设置签名
                var sign = cutStrByByte(activeChatter.signature, 28);
                $(".chat__status").text(sign);
                //判断是否为掉线状态
                if (activeChatter.status == 0){
                    showToast("对方网络异常", 1500)
                }
                // 判断是否为离线状态
                // if (activeChatter.status == -1){
                //     showToast("对方下线了", 1000)
                // }
                //获取session
                var socket = getSocket();
                var action = new Object();
                action.code = CREATE_SESSION;
                action.msg = "ok";
                action.data = getChatterId() + ";" + activeChatter.id;
                //向服务器发送数据
                socket.send(JSON.stringify(action));
                // 设置成未提醒
                setAlertMap(activeChatter.id, false);
                //设置消息已读
                changeStatus(activeChatter.id, false);
            });
        }
    }

    $(".chat__back").on("click", function(){
        if(window.uploadLock) {
            return
        }
        activeChatter = null;
        activeSession = null;
    });

    //创建session,socket回调
    createSession = function (session) {
        activeSession = session;
        //初始化消息
        //清除dom
        $(".chat__msgRow").remove();
        $(".time").remove();
        var messageList = activeSession.messageList;
        for (var i = 0; i < messageList.length; i++) {
            var message = messageList[i];
            var content = message.content;
            var isMain;
            if (message.chatterId == getChatterId()) {
                isMain = true;
            } else {
                isMain = false;
            }
            // 根据消息与ismain创建dom
            var dom = selectMessageDom(message, isMain);
            
            //dom中添加消息
            $messageBox.append(dom);
        }
        let laterDuring = isSideBarOutside() ? 100 : 1000
        setTimeout(function(){
            document.querySelector(".chat__messages").scrollBy({ top: 12500, left: 0, behavior: 'smooth' });
        },laterDuring);
        
        
    }
    //收到消息，回调
    receiveMessage = function (message) {
        console.info(message)
        // 如果message是群聊message且群聊信息窗打开
        if (message.common){
            if (activeChatter && activeChatter.id === "temp-chatter") {
                // 判断是否是自己的消息
                if (message.chatterId === getChatterId()) {
                    // 判断是否是文件
                    if (!message.content) {
                        // 更新文件的url
                        for (let dom of $(".notMine")){
                            dom = dom.querySelectorAll("a")
                            let a1 = dom[0]
                            let a2 = dom[1]
                            // 非图片
                            if (!(a1 || a2)) {
                                continue
                            }
                            innerText = a2.innerText.replace(/\s+/g,"");
                            if (a2.innerText === message.fileName && a2.href === "javascript:;") {
                                a2.href = getPrefix() + "/chat/"+message.url
                                // 更新图片文件的显示
                                let fileImg = a1.querySelector("img")
                                if (fileImg && isImg(a2.href)) {
                                    // 更新为图片的snapshot
                                    fileImg.src = getPrefix() + "/chat/"+message.snapshotUrl
                                    $(fileImg).css("width","100%");
                                    $(fileImg).on('click', function() {
                                        syncToHolder(fileImg.src)
                                    })
                                }
                                a2.target = "_blank";
                            }
                        }
                        
                    }
                    return
                }
                // 根据消息与ismain创建dom
                var dom = selectMessageDom(message, false);
                
                $messageBox.append(dom);
                setTimeout(()=> {
                    document.querySelector(".chat__messages").scrollBy({ top: 12500, left: 0, behavior: 'smooth' });
                },100)
                
                // 判断是不是当前页
                if (!isCurrentPage) {
                    document.getElementsByTagName("title")[0].innerText = "molachat(当前有未读消息)" ;
                }
                return
            }
            // 收到common的消息，非群聊页面中，直接丢弃
            return
        }

        // 如果是自己的文件消息
        if (message.chatterId === getChatterId() && !message.content) {
            // 更新文件的url
            for (let dom of $(".notMine")){
                dom = dom.querySelectorAll("a")
                let a1 = dom[0]
                let a2 = dom[1]
                // 非图片
                if (!(a1 || a2)) {
                    continue
                }
                innerText = a2.innerText.replace(/\s+/g,"");
                if (a2.innerText === message.fileName && a2.href === "javascript:;") {
                    a2.href = getPrefix() + "/chat/"+message.url
                    // 更新图片文件的显示
                    let fileImg = a1.querySelector("img")
                    if (fileImg && isImg(a2.href)) {
                        // 更新为图片的snapshot
                        fileImg.src = getPrefix() + "/chat/"+message.snapshotUrl
                        $(fileImg).css("width","100%");
                        $(fileImg).on('click', function() {
                            syncToHolder(fileImg.src)
                        })
                    }
                    a2.target = "_blank";
                }
            }
        }

        //如果是当前session,立即加载到dom中
        if (activeChatter != null && message.chatterId == activeChatter.id){
            //dom中添加消息
            //如果为文件传输
            var dom;
            if (message.content != null){
                dom = messageDom(message, false);
            }else{
                dom = fileDom(message,false,false,"ready","/chat/"+message.url,"/chat/"+message.snapshotUrl);
            }

            $messageBox.append(dom);
            setTimeout(()=> {
                document.querySelector(".chat__messages").scrollBy({ top: 12500, left: 0, behavior: 'smooth' });
            },100)
            // 判断是不是当前页
            if (!isCurrentPage) {
                document.getElementsByTagName("title")[0].innerText = "molachat(当前有未读消息)" ;
            }
        }
        //如过非当前session，将对应chatter的未读消息提示点亮
        else{
            var senderId = message.chatterId;
            // 这个信息必须不是公共信息
            if (!message.common && senderId != getChatterId()){
                changeStatus(senderId,true);
            }
            // 提醒要求
            // 1.判断该信息不能是自己发的
            // 2.不能没有正在通话的session
            // 3.消息主人id不能是正在通话者的id
            // 4.聊天窗可见
            if(message.chatterId != getChatterId() && activeChatter != null && message.chatterId != activeChatter.id && $chat.css("display") === "block"){
                if (!alertMap.get(message.chatterId) && !message.common){
                    // 群消息免提醒
                    showToast("外部有新的消息", 1000)
                }
                 // 设置成已经提醒
                 setAlertMap(message.chatterId, true);
            }
        }
    }

    getActiveSessionId = function () {
        return activeSession.sessionId;
    }

    getActiveChatter = function(){
        return activeChatter;
    }
    setActiveChatterName = function(name){
        activeChatter.name = name;
        //更新dom
        $(".chat__name")[0].innerText = name;
    }
    setActiveChatter = function(chatter) {
        activeChatter = chatter;
    }

    setActiveChatterImgUrl = function(imgUrl){
        activeChatter.imgUrl = imgUrl;
        $("img.cloned")[0].src = imgUrl;
    }

    setActiveChatterSign = function(sign) {
        activeChatter.signature = sign;
        sign = cutStrByByte(sign, 28);
        $(".chat__status")[0].innerText = sign;
    }

    setAlertMap = function(id, status) {
        alertMap.set(id, status)
    }
});