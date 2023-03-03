$(document).ready(function() {
    // dom
    var $historyModal = $('#history-user-modal')
    // dom初始化位置
    $historyModal.css("max-width",600)
    if (window.innerWidth > 600) {
        $historyModal.css("left",(window.innerWidth - $historyModal.innerWidth())/2)
    }
    addResizeEventListener(function() {
        if (window.innerWidth > 600) {
            $historyModal.css("left",(window.innerWidth - $historyModal.innerWidth())/2)
        } else {
            $historyModal.css("left",0)
        }
    })
    var $menuBtn = $("#history")
    var $innerBtn = $("#tool-histroy")
    var $historyList = $(".history-list")[0]
    var $changeUserBtn = $("#changeUserBtn")
    var $copyBtn = $("#copyBtn")

    var globalHistoryUsers = []
    openModal = function() {
        getHistoryChatters(
            function(historyUsers) {
                globalHistoryUsers = historyUsers
                while($historyList.firstChild) {
                    $historyList.removeChild($historyList.firstChild)
                }
                historyUsers.forEach((user, idx) => {
                    $historyList.append(historyChatterDom(user.id, user.name, user.imgUrl,idx))
                });
                $historyModal.modal("open")
            },
            function() {
                while($historyList.firstChild) {
                    $historyList.removeChild($historyList.firstChild)
                }
                $historyModal.modal("open")
            }
        )
    }

    $menuBtn.on('click', openModal)
    $innerBtn.on('click', openModal)

    // 模态框初始化
    $historyModal.modal({
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

    /**
     * 返回聊天者的dom
     * @param {*} name 昵称
     * @param {*} url 头像链接
     * @param {*} status　是否为新消息
     * @param {*} intro　个人简介
     */
     historyChatterDom = function (id, name, url, idx) {
        //main
        var mainDoc = document.createElement("div");
        $(mainDoc).addClass("history_contact");
        //头像
        var imgDoc = document.createElement("img");
        $(imgDoc).addClass("contact__photo");
        imgDoc.src = url;
        //name
        var nameDoc = document.createElement("span");
        $(nameDoc).addClass("contact__name");
        nameDoc.innerText = name;
        //拼接
        mainDoc.append(imgDoc);
        mainDoc.append(nameDoc);
        //status，显示当前用户
        if (id === getChatterId()) {
            var statusDoc = document.createElement("span");
            $(statusDoc).addClass("contact__status");
            $(statusDoc).addClass("online");
            mainDoc.append(statusDoc);
        }
        mainDoc.idx = idx
        return mainDoc;
    }

    $(document).on("click", ".history_contact", function(e) {
        ripple($(this), e);
        const userInfo = globalHistoryUsers[this.idx];
        if (userInfo.id === getChatterId()) {
            showToast("已经切换到当前用户", 1000)
            return
        }
        swal("切换用户","是否切换到用户[" + userInfo.name + "]","info").then((change) => {
            if (change) {
                changeChatter(userInfo.base64)
                $historyModal.modal('close')
            }
        });
    })

    // 复制序列
    $copyBtn.on('click', function() {
        copyText(getSecret())
    })

    // 粘贴序列
    $changeUserBtn.on('click', function() {
        swal({
            content: {
                element: "input",
                attributes: {
                    placeholder: "请输入序列号",
                    type: "text",
                },
            },
        }).then((value) => {
            if (value) {
                changeChatter(value)
            }
        })
    })
})