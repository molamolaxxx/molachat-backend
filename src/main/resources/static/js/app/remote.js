$(document).ready(function() {
    // dom
    var $serverModal = $('#change-server-modal')
    // dom初始化位置
    $serverModal.css("max-width",600)
    if (window.innerWidth > 600) {
        $serverModal.css("left",(window.innerWidth - $serverModal.innerWidth())/2)
    }
    addResizeEventListener(function() {
        if (window.innerWidth > 600) {
            $serverModal.css("left",(window.innerWidth - $serverModal.innerWidth())/2)
        } else {
            $serverModal.css("left",0)
        }
    })
    var $menuBtn = $("#settingsRemote")
    var $serverList = $(".server-list")[0]
    var $changeServerBtn = $("#changeServerBtn")
    var $copyServerBtn = $("#copyServerBtn")


    openServerModal = function() {
        while($serverList.firstChild) {
            $serverList.removeChild($serverList.firstChild)
        }
        $serverModal.modal("open")
        for (let i = 0; i < localStorage.length; i++) {
            const key = localStorage.key(i); // 获取当前遍历到的 key
            const value = localStorage.getItem(key); // 获取当前 key 对应的 value
            if (key.startsWith("_host_")) {
                const host = key.slice(6)
                $serverList.append(serverDom(host, i, host === getHost()))
            }
        }
        
    }

    $menuBtn.on('click', openServerModal)

    // 模态框初始化
    $serverModal.modal({
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
     serverDom = function (name, idx, online) {
        //main
        var mainDoc = document.createElement("div");
        $(mainDoc).addClass("server_contact");
        //头像
        var imgDoc = document.createElement("img");
        $(imgDoc).addClass("contact__photo");
        imgDoc.src = "img/mola.png";
        //name
        var nameDoc = document.createElement("span");
        $(nameDoc).addClass("contact__name");
        nameDoc.innerText = name;
        //拼接
        mainDoc.append(imgDoc);
        mainDoc.append(nameDoc);
        //status，显示当前用户
        if (online) {
            var statusDoc = document.createElement("span");
            $(statusDoc).addClass("contact__status");
            $(statusDoc).addClass("online");
            mainDoc.append(statusDoc);
        }
        mainDoc.idx = idx
        mainDoc.host = name
        return mainDoc;
    }

    $(document).on("click", ".server_contact", function(e) {
        ripple($(this), e);
        if (this.host === getHost()) {
            showToast("已处于当前服务器",1000)
            return
        }
        swal("切换服务器","是否切换服务器地址到["+this.host+"]?\n","info").then((change) => {
            if (change) {
                changeServer(this.host)
                $serverModal.modal("close")
            }
        });
    })

    // 复制序列
    $copyServerBtn.on('click', function() {
        copyText(getHost())
    })

    // 粘贴序列
    $changeServerBtn.on('click', function() {
        swal({
            content: {
                element: "input",
                attributes: {
                    placeholder: "请输入服务器地址",
                    type: "text",
                },
            },
        }).then((value) => {
            if (value) {
                if (value === getHost()) {
                    showToast("已处于当前服务器",1000)
                    return
                }
                swal("切换服务器","是否切换服务器地址到["+value+"]?\n","info").then((change) => {
                    if (change) {
                        changeServer(value)
                    }
                });
            }
        })
    })

    changeServer = function(host) {
        if (host === getHost()) {
            showToast("已处于当前服务器",1000)
            return
        }
        // 判断server是否可用
        checkConnect(host, (res) => {
            if (res === 'error') {
                const contains = localStorage.getItem("_host_" + host)
                swal("连接失败", "服务器不可用" +(contains ? "，是否删除?" : ""),"info").then((change) => {
                    if (change) {
                        localStorage.removeItem("_host_" + host)
                        if (contains) {
                            showToast("删除成功",1000)
                        }
                    }
                });
                return
            }
            const dict = {}
            const curHost = getHost()
            let targetStorageDict = null
            for (let i = 0; i < localStorage.length; i++) {
                const key = localStorage.key(i); // 获取当前遍历到的 key
                const value = localStorage.getItem(key); // 获取当前 key 对应的 value
                // 恢复目标的localstore
                if (key === "_host_" + host) {
                    targetStorageDict = JSON.parse(value)
                }
                // 保存当前localstore
                if (!key.startsWith("_host_")) {
                    dict[key] = value
                }
            }
            // 保存当前localstore
            localStorage.setItem("_host_" + curHost, JSON.stringify(dict))
            // 恢复目标host的变量
            if (targetStorageDict) {
                for (let key in targetStorageDict) {
                    localStorage.setItem(key, targetStorageDict[key])
                }
                // 覆盖内存中的用户信息
                setChatterName(localStorage.getItem("chatterName"))
                setChatterImage(localStorage.getItem("imgUrl"))
                setChatterSign(localStorage.getItem("sign"))
                setToken(localStorage.getItem("token"))
            } else { // 不存在上下文，则移除所有当前相关信息
                const keys = []
                for (let i = 0; i < localStorage.length; i++) {
                    const key = localStorage.key(i); // 获取当前遍历到的 key
                    keys.push(key)
                }
                for (let index = 0; index < keys.length; index++) {
                    const key = keys[index];
                    if (!key.startsWith("_host_")) {
                        localStorage.removeItem(key)
                    }
                }
                // 覆盖内存中的用户信息
                setChatterName(createChatterName())
                setChatterSign("点击修改签名")
            }
            // 修改当前host
            const arr = host.split(":")
            setIp(arr[0])
            setPort(arr[1])
            recoverChatter()
        })
        
    }

    checkConnect = function (host, callback) {
        addSpinner("app_content", true)
        $.ajax({
            url: "https://" + host + "/chat/app/host",
            type: "get",
            dataType: "json",
            timeout:3000,
            success: function(result) {
                callback('success')
                removeSpinner()
            },
            error: function(result) {
                callback('error')
                removeSpinner()
            }
        });
    }
    checkHost = function() {
        checkConnect(getHost(), function(res) {
            if (res === 'error') {
                swal("连接失败", "服务器不可用，是否切换其他服务器?","info").then((change) => {
                    if (change) {
                        openServerModal()
                    }
                });
            }
        })
    }
})