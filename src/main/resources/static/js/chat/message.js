// 消息发送
$(document).ready(function () {
    var $send = $(".send"),
        $chatInput = $(".chat__input")[0],
        $viewContent = $("#viewContent"),
        $viewModal = $("#message-view-modal"),
        $copyViewBtn = $('#copyViewBtn'),
        $chatMsg = $(".chat__messages")[0];
    var inEditMode = false

    // dom初始化位置
    $viewModal.css("max-width",1000)
    if (window.innerWidth > 1000) {
        $viewModal.css("left",(window.innerWidth - $viewModal.innerWidth())/2)
    }

    addResizeEventListener(function() {
        if (window.innerWidth > 1000) {
            $viewModal.css("left",(window.innerWidth - $viewModal.innerWidth())/2)
        } else {
            $viewModal.css("left",0)
        }
    })

    //const
    const SEND_MESSAGE = 595;

    // 聊天时间显示dom
    var lastTime = -1;
    timeDom = function(time) {
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
        }
        else {
            
            imgDoc.src = getActiveChatter().imgUrl;
            $(imgDoc).addClass("contact__photo");
            $(imgDoc).css('float', 'left');
            $(imgDoc).css('display', 'inline');
            $(imgDoc).css('margin-right', '0rem');

            $(mainDocChild).css('margin-left', '0.5rem');
            $(mainDocChild).addClass("chat__message mine");
        }
        $(mainDocChild).css('position', 'relative');

        mainDoc.append(imgDoc);
        // mainDocChild.innerHTML = twemoji.parse(content,{"folder":"svg","ext":".svg","base":"asset/","size":15});
        mainDocChild.innerText = content
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
                || codeObj.language === 'c++' 
                || codeObj.language === 'kotlin'
                || codeObj.language === 'c'
                || codeObj.language === 'bash'
                || codeObj.language === 'csharp'
                || codeObj.language === 'javascript'
                || codeObj.language === 'xml'
                // 只有关键字的文本，不需要按照代码格式展示
                isCommonCode = isCommonCode && (content.includes("{") || content.includes("}") || content.includes(":"))
                if (isCommonCode) {
                    $viewContent.addClass("view-content")
                } else {
                    $viewContent.removeClass("view-content")
                }
                $copyViewBtn[0].copyContent = content
                $viewContent[0].innerHTML = codeObj.value
                console.log(codeObj);
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

    //增加一条我方记录
    addMessage = function ($chatMsg, content, isMain) {

        //拼装dom
        var mainDoc = messageDom({content:content,createTime:(new Date()).valueOf()}, isMain);

        //添加dom
        $chatMsg.append(mainDoc);

        //滚动
        setTimeout(()=> {
            document.querySelector(".chat__messages").scrollBy({ top: 12500, left: 0, behavior: 'smooth' });
        },100)
    }

    //删除所有记录
    deleteAllMessage = function () {
        $chatMsg.remove();
    }

    // 判断输入是否完成
    var isInputFinished = true
    $(".chat__input").bind("keyup", function (ev) {
        if (inEditMode) {
            return
        }
        if (ev.keyCode == "13" && isInputFinished) {
            $send.click();
        }
    });
    // 判断输入是否结束
    $chatInput.addEventListener('compositionstart', function(e) {
        console.log("false")
        isInputFinished = false;
    }, false)
    $chatInput.addEventListener('compositionend', function(e) {
        setTimeout(() => {
            isInputFinished = true
            console.log("true")
        }, 100)
    }, false)


    $(document).on("click", ".send", function () {
        //获取文本框内容
        var content = $chatInput.value;
        if (content === "") {
            // swal("stop!","输入不能为空","warning");
            showToast("输入不能为空",1000)
            return;
        }

        //清空文本框
        $chatInput.value = "";

        //自动获取焦点
        $chatInput.focus();

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
    });

    // 明细模态框初始化
    $viewModal.modal({
        dismissible: true, // Modal can be dismissed by clicking outside of the modal
        opacity: .2, // Opacity of modal background
        in_duration: 300, // Transition in duration
        out_duration: 200, // Transition out duration
        starting_top: '4%', // Starting top style attribute
        ending_top: '100%', // Ending top style attribute
        ready: function(modal, trigger) { // Callback for Modal open. Modal and trigger parameters available.
            
        },
        complete: function() { 
            
        } 
    });

    $copyViewBtn.on('click', function(e) {
        copyText(this.copyContent)
    })

     // 明细模态框初始化
     $viewModal.modal({
        dismissible: true, // Modal can be dismissed by clicking outside of the modal
        opacity: .2, // Opacity of modal background
        in_duration: 300, // Transition in duration
        out_duration: 200, // Transition out duration
        starting_top: '4%', // Starting top style attribute
        ending_top: '100%', // Ending top style attribute
        ready: function(modal, trigger) { // Callback for Modal open. Modal and trigger parameters available.
            
        },
        complete: function() { 
            
        } 
    });

    var $editModal = $("#message-edit-modal")
    var $openEditBtn = $("#open-text-btn")
    
    $editModal.modal({
        dismissible: true, // Modal can be dismissed by clicking outside of the modal
        opacity: .2, // Opacity of modal background
        in_duration: 300, // Transition in duration
        out_duration: 200, // Transition out duration
        starting_top: '4%', // Starting top style attribute
        ending_top: '100%', // Ending top style attribute
        ready: function(modal, trigger) { // Callback for Modal open. Modal and trigger parameters available.
            inEditMode = true
        },
        complete: function() { 
            inEditMode = false
        } 
    });

    // dom初始化位置
    $editModal.css("max-width",800)
    if (window.innerWidth > 800) {
        $editModal.css("left",(window.innerWidth - $editModal.innerWidth())/2)
    }

    addResizeEventListener(function() {
        if (window.innerWidth > 800) {
            $editModal.css("left",(window.innerWidth - $editModal.innerWidth())/2)
        } else {
            $editModal.css("left",0)
        }
    })

    var $chatEditor = $("#chatEditor")
    $chatEditor.keydown(function(e) {
        if(e.keyCode === 9) { // tab was pressed
            // get caret position/selection
            var start = this.selectionStart;
            var end = this.selectionEnd;
    
            var $this = $(this);
            var value = $this.val();
    
            // set textarea value to: text before caret + tab + text after caret
            $this.val(value.substring(0, start)
                        + "\t"
                        + value.substring(end));
    
            // put caret at right position again (add one for the tab)
            this.selectionStart = this.selectionEnd = start + 1;
    
            // prevent the focus lose
            e.preventDefault();
        }
    });
    $openEditBtn.on('click', function() {
        $editModal.modal('open')
        if ($chatEditor.val() === '' && $chatInput.value !== '') {
            $chatEditor.val($chatInput.value)
        }
    })

    var $editCompleteBtn = $("#editCompleteBtn")
    $editCompleteBtn.on('click', function() {
        const content = $chatEditor.val()
        if (content === "") {
            // swal("stop!","输入不能为空","warning");
            showToast("输入不能为空",1000)
            return;
        }

        //清空文本框
        $chatEditor.val('')
        $chatInput.value = "";

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
    })
});