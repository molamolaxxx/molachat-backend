$(document).ready(function() {
    // dom
    var $historyModal = $('#history-user-modal')
    // dom初始化位置
    $historyModal.css("max-width",600)
    if (getInnerWidth() > 600) {
        $historyModal.css("left",(getInnerWidth() - $historyModal.innerWidth())/2)
    }
    addResizeEventListener(function() {
        if (getInnerWidth() > 600) {
            $historyModal.css("left",(getInnerWidth() - $historyModal.innerWidth())/2)
        } else {
            $historyModal.css("left",0)
        }
    })
    var $menuBtn = $("#history")
    var $innerBtn = $("#tool-histroy")
    var $historyList = $(".history-list")[0]
    var $changeUserBtn = $("#changeUserBtn")
    var $copyBtn = $("#copyBtn")
    var $addUserBtn = $("#addUserBtn")

    var globalHistoryUsers = []

    var renderHistoryList = function(historyUsers) {
        globalHistoryUsers = historyUsers ? historyUsers : []
        while($historyList.firstChild) {
            $historyList.removeChild($historyList.firstChild)
        }
        globalHistoryUsers.forEach((user, idx) => {
            $historyList.append(historyChatterDom(user.id, user.name, user.imgUrl, idx))
        });
    }

    var refreshHistoryList = function() {
        getHistoryChatters(
            renderHistoryList,
            function() {
                renderHistoryList([])
            }
        )
    }

    openModal = function() {
        getHistoryChatters(
            function(historyUsers) {
                renderHistoryList(historyUsers)
                $historyModal.modal("open")
            },
            function() {
                renderHistoryList([])
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
        } else {
            // 非当前用户，允许从本机缓存中删除
            var deleteDoc = document.createElement("span");
            $(deleteDoc).addClass("history_user_delete");
            deleteDoc.innerHTML = '<i class="material-icons" style="font-size: 18px;">delete</i>'
            deleteDoc.idx = idx
            mainDoc.append(deleteDoc);
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
        popSwitchUserConfirm(userInfo)
    })

    /**
     * 切换历史用户的确认弹窗
     * @param {*} userInfo 目标用户
     */
    var popSwitchUserConfirm = function(userInfo) {
        var wrapper = document.createElement("div");
        $(wrapper).addClass("switch-user-card");

        var coverDoc = document.createElement("div");
        $(coverDoc).addClass("switch-user-card__cover");
        coverDoc.innerHTML = [
            '<span class="switch-user-card__eyebrow">MOLA CHAT</span>',
            '<h3>确认切换身份</h3>',
            '<p>切换后将以该用户继续聊天</p>'
        ].join("");
        wrapper.append(coverDoc);

        var bodyDoc = document.createElement("div");
        $(bodyDoc).addClass("switch-user-card__body");

        var avatarDoc = document.createElement("img");
        $(avatarDoc).addClass("switch-user-card__avatar");
        avatarDoc.src = userInfo.imgUrl;
        avatarDoc.alt = userInfo.name + "的头像";
        bodyDoc.append(avatarDoc);

        var labelDoc = document.createElement("span");
        $(labelDoc).addClass("switch-user-card__label");
        labelDoc.innerText = "目标身份";
        bodyDoc.append(labelDoc);

        var nameDoc = document.createElement("h4");
        $(nameDoc).addClass("switch-user-card__name");
        nameDoc.innerText = userInfo.name;
        nameDoc.title = userInfo.name;
        bodyDoc.append(nameDoc);

        var confirmDoc = document.createElement("p");
        $(confirmDoc).addClass("switch-user-card__confirm");
        confirmDoc.innerText = "确定切换到这个历史用户吗？";
        bodyDoc.append(confirmDoc);

        var hintDoc = document.createElement("p");
        $(hintDoc).addClass("switch-user-card__hint");
        hintDoc.innerHTML = [
            '<i class="material-icons" aria-hidden="true">swap_horiz</i>',
            '<span>确认后将使用该身份重新连接</span>'
        ].join("");
        bodyDoc.append(hintDoc);
        wrapper.append(bodyDoc);

        swal({
            className: "switch-user-modal",
            content: wrapper,
            buttons: {
                cancel: "取消",
                confirm: {
                    text: "确认切换",
                    value: "switch"
                }
            }
        }).then((value) => {
            if (value !== "switch") {
                return
            }
            changeChatter(userInfo.base64)
            $historyModal.modal('close')
        })
    }

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

    // 删除历史用户，仅清除本机缓存
    $(document).on("click", ".history_user_delete", function(e) {
        e.stopPropagation();
        const userInfo = globalHistoryUsers[this.idx];
        if (!userInfo) {
            return
        }
        if (userInfo.id === getChatterId()) {
            showToast("无法删除当前用户", 1000)
            return
        }
        swal({
            title: "删除历史用户",
            text: "是否删除用户[" + userInfo.name + "]?\n仅清除本机的登录记录，不会影响服务端数据",
            icon: "warning",
            buttons: {
                cancel: "取消",
                confirm: {
                    text: "确认",
                    value: "delete",
                    className: "swal_delete"
                }
            }
        }).then((value) => {
            if (value) {
                removeHistorySecret(userInfo.id)
                showToast("删除成功", 1000)
                refreshHistoryList()
            }
        });
    })

    // 新增用户
    $addUserBtn.on('click', function() {
        if (getEngines().videoEngine.isOpen()) {
            showToast("您正在通话中，无法新增用户", 1000)
            return
        }
        if (window.uploadLock) {
            showToast("文件正在上传，无法新增用户", 1000)
            return
        }
        $historyModal.modal('close')
        popAddUserForm(randomChatterName(), randomChatterImg())
    })

    /**
     * 新增用户的确认弹窗
     * @param {*} defaultName 默认昵称
     * @param {*} defaultImg 默认头像
     */
    var popAddUserForm = function(defaultName, defaultImg) {
        var wrapper = document.createElement("div");
        $(wrapper).addClass("add-user-card");

        var coverDoc = document.createElement("div");
        $(coverDoc).addClass("add-user-card__cover");
        coverDoc.innerHTML = [
            '<span class="add-user-card__eyebrow">MOLA CHAT</span>',
            '<h3>创建新身份</h3>',
            '<p>创建后将自动保存到历史用户</p>'
        ].join("");
        wrapper.append(coverDoc);

        var bodyDoc = document.createElement("div");
        $(bodyDoc).addClass("add-user-card__body");

        // 头像：点击整个头像区域均可随机更换
        var imgDoc = document.createElement("img");
        imgDoc.src = defaultImg;
        imgDoc.alt = "新用户头像";
        var imgLink = document.createElement("a");
        imgLink.href = "javascript:;";
        imgLink.setAttribute("aria-label", "随机更换头像");
        imgLink.setAttribute("data-hint", "点击随机更换");
        $(imgLink).addClass("add-user-card__avatar");
        imgLink.append(imgDoc);
        var avatarBadge = document.createElement("span");
        $(avatarBadge).addClass("add-user-card__avatar-edit");
        avatarBadge.innerHTML = '<i class="material-icons" aria-hidden="true">photo_camera</i>';
        imgLink.append(avatarBadge);
        bodyDoc.append(imgLink);

        // 昵称字段
        var nameInput = document.createElement("input");
        $(nameInput).addClass("browser-default").addClass("add-user-input").addClass("add-user-name");
        nameInput.id = "add-user-name";
        nameInput.type = "text";
        nameInput.placeholder = "请输入昵称";
        nameInput.value = defaultName;
        var nameField = document.createElement("div");
        $(nameField).addClass("add-user-field");
        nameField.innerHTML = [
            '<i class="material-icons add-user-field__icon" aria-hidden="true">person_outline</i>',
            '<label class="add-user-field__content" for="add-user-name">',
            '<span>昵称</span>',
            '</label>'
        ].join("");
        nameField.querySelector("label").append(nameInput);
        bodyDoc.append(nameField);

        // 头像链接字段
        var imgInput = document.createElement("input");
        $(imgInput).addClass("browser-default").addClass("add-user-input").addClass("add-user-img");
        imgInput.id = "add-user-img";
        imgInput.type = "text";
        imgInput.placeholder = "请输入头像链接";
        imgInput.value = defaultImg;
        var imgField = document.createElement("div");
        $(imgField).addClass("add-user-field");
        imgField.innerHTML = [
            '<i class="material-icons add-user-field__icon" aria-hidden="true">link</i>',
            '<label class="add-user-field__content" for="add-user-img">',
            '<span>头像链接</span>',
            '</label>'
        ].join("");
        imgField.querySelector("label").append(imgInput);

        var randomButton = document.createElement("button");
        randomButton.type = "button";
        randomButton.setAttribute("aria-label", "随机更换头像");
        randomButton.title = "随机更换头像";
        $(randomButton).addClass("add-user-card__random");
        randomButton.innerHTML = '<i class="material-icons" aria-hidden="true">refresh</i>';
        imgField.append(randomButton);
        bodyDoc.append(imgField);

        var hintDoc = document.createElement("p");
        $(hintDoc).addClass("add-user-card__hint");
        hintDoc.innerHTML = [
            '<i class="material-icons" aria-hidden="true">info_outline</i>',
            '<span>新身份会独立生成 Chatter ID，并保存在当前设备中</span>'
        ].join("");
        bodyDoc.append(hintDoc);
        wrapper.append(bodyDoc);

        var randomizeAvatar = function() {
            imgInput.value = randomChatterImg()
            imgDoc.src = imgInput.value
        }
        $(imgLink).on("click", randomizeAvatar)
        $(randomButton).on("click", randomizeAvatar)
        $(imgInput).on("change", function() {
            imgDoc.src = imgInput.value
        })

        swal({
            className: "add-user-modal",
            content: wrapper,
            buttons: {
                cancel: "取消",
                confirm: {
                    text: "创建并切换",
                    value: "create"
                }
            }
        }).then((value) => {
            if (!value) {
                return
            }
            const name = nameInput.value.trim()
            const imgUrl = imgInput.value.trim()
            if (isEmpty(name)) {
                showToast("昵称不能为空", 1000)
                popAddUserForm(defaultName, imgUrl)
                return
            }
            if (isEmpty(imgUrl)) {
                showToast("头像链接不能为空", 1000)
                popAddUserForm(name, defaultImg)
                return
            }
            createAndSwitchChatter(name, imgUrl, function() {
                // 创建失败时保留已填内容，方便修改后重试
                popAddUserForm(name, imgUrl)
            })
        })
    }
})
