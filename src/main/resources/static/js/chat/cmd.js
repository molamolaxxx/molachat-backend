$(document).ready(function() {
    // 消息模态框
    var $viewContent = $("#cmdContent")
    var $viewModal = $("#cmd-view-modal")

    // dom初始化位置
    $viewModal.css("max-width", 1000)
    if (window.innerWidth > 1000) {
        $viewModal.css("left", (window.innerWidth - $viewModal.innerWidth()) / 2)
    }

    addResizeEventListener(function () {
        if (window.innerWidth > 1000) {
            $viewModal.css("left", (window.innerWidth - $viewModal.innerWidth()) / 2)
        } else {
            $viewModal.css("left", 0)
        }
    })

    // 明细模态框初始化
    $viewModal.modal({
        dismissible: true, // Modal can be dismissed by clicking outside of the modal
        opacity: .2, // Opacity of modal background
        in_duration: 300, // Transition in duration
        out_duration: 200, // Transition out duration
        starting_top: '4%', // Starting top style attribute
        ending_top: '100%', // Ending top style attribute
        ready: function (modal, trigger) { // Callback for Modal open. Modal and trigger parameters available.

        },
        complete: function () {

        }
    });

    registerOnClick = function() {
        var $innerBtn = $("#tool-cmd")
        $innerBtn.on('click', () => {
            $.ajax({
                url: getPrefix() + "/chat/robot/cmd/markdown/" + getActiveChatter().id + "/" + getActiveSessionId(),
                type: "get",
                dataType: "json",
                timeout:3000,
                success: function(result) {
                    if (!result.data) {
                        showToast("未获取到可操作的命令", 1000)
                        return
                    }
                    $viewContent[0].innerHTML = buildMarkdown(result.data)
                    $viewModal.modal('open')
                    
                },
                error: function(result) {
                    console.log(result);
                    showToast("获取命令异常，请稍后重试", 1000)
                }
            });
        })
        
    }

    closeMessageView = function() {
        $viewModal.modal('close')
    }

    buildMarkdown = function (content) {
        return marked.parse(content)
    }
})