$(document).ready(function () {

    var $friend_list = $(".friend-list")[0];

    //chatterId -> status 1:new Message 0:no message
    var statusMap = new Map();

    //chatterId ->index
    var indexMap;

    // chatterId -> Chatter
    var chatterMap = new Map();

    // 初始化chatterMap
    $.ajax({
        url: getPrefix() + "/chat/chatter/common/chatter",
        type: "get",
        dataType: "json",
        timeout:3000,
        success: function(result) {
            var chatterList = result.data;
            for (let i in chatterList) {
                chatterMap.set(chatterList[i].id, chatterList[i])
            }
        },
        error: function(result) {
        }
    });

    /**
     * 返回聊天者的dom
     * @param {*} name 昵称
     * @param {*} url 头像链接
     * @param {*} status　是否为新消息
     * @param {*} intro　个人简介
     */
    chatterDom = function (name, url, hasNewMsg, introText, status) {
        //main
        var mainDoc = document.createElement("div");
        $(mainDoc).addClass("contact");
        //头像
        var imgDoc = document.createElement("img");
        $(imgDoc).addClass("contact__photo");
        imgDoc.src = url;
        //name
        var nameDoc = document.createElement("span");
        $(nameDoc).addClass("contact__name");

        nameDoc.innerText = name;
        //status
        var statusDoc = document.createElement("span");
        $(statusDoc).addClass("contact__status");
        if (hasNewMsg === true && status != 0)
            $(statusDoc).addClass("online");

        // 掉线：（socket端点还在，但是发送心跳超时）
        if (status == 0) {
            $(statusDoc).addClass("leave");
        }
        // 手机，提示移到右上角
        if (needToShowStatusSmaller()) {
            $(statusDoc).addClass("mobile");
        }
        // 离线：（socket端点不存在）
        if (status == -1) {
            $(imgDoc).addClass("contact__photo__gray");
        }
        //intro
        var intro = document.createElement("span");
        $(intro).addClass("contact_intro");
        intro.innerText = introText;
        //拼接
        mainDoc.append(imgDoc);
        mainDoc.append(nameDoc);
        mainDoc.append(statusDoc);
        mainDoc.append(intro);
        return mainDoc;
    }

    /**
     * idx 第几个联系人
     */
    changeStatus = function (chatterId, isActive) {
        var idx = indexMap.get(chatterId);

        if (isActive) {
            //有未读消息
            console.log("点亮消息,index:" + idx);
            $($(".contact")[idx]).find(".contact__status").addClass("online");
            statusMap.set(chatterId, 1);
        
        }
        else {
            console.log("熄灭消息,index:" + idx);
            $($(".contact")[idx]).find(".contact__status").removeClass("online");
            statusMap.set(chatterId, 0);
        }

        // 遍历statusMap
        var count = 0;
        statusMap.forEach(function(value, key) {
            if (value == 1 && key != getChatterId()){
                count += 1;
            }
        });
        console.info("count"+count)
        if (count > 0){
            // 网页显示有未读消息
            document.getElementsByTagName("title")[0].innerText = "molachat(有"+count+"个联系人找你)" ;
        }else {
            // 网页显示有未读消息
            document.getElementsByTagName("title")[0].innerText = "molachat" ;
        }
    }

    //初始化聊天者
    initChatter = function (chatterList, selfId) {
        chatterListData = new Array();
        var newStatusMap = new Map();
        indexMap = new Map();
        //indexMap的index
        var index = 0;
        console.log("init_chatterList")

        $(".contact").remove();

        if (chatterList.length == 1) {
            $(".empty").css("display", "block");
        }
        else {
            $(".empty").css("display", "none");
        }

        //判断activechatter是否在list内
        var chatterIsActive = false
        for (var i in chatterList) {

            var chatter = chatterList[i];
            chatterMap.set(chatter.id, chatter);
            //更改名字时同步更新到聊天界面
            //如果activechatter在，则置为ture
            if (null != getActiveChatter() && chatter.id == getActiveChatter().id) {
                setActiveChatterName(chatter.name);
                //设置聊天clone头像
                setActiveChatterImgUrl(chatter.imgUrl);
                // 设置签名
                setActiveChatterSign(chatter.signature);
                // 同步头像状态
                syncAvatarStatus(chatter.status)
                chatterIsActive = true;
            }

            if (chatter.id != selfId) {
                //chatterId到编号的索引
                indexMap.set(chatter.id, index);
                index++;

                //新增的chatter，状态设置为false
                if (statusMap.get(chatter.id) == null || statusMap.get(chatter.id) == 0) {
                    newStatusMap.set(chatter.id, 0);
                    $friend_list.append(chatterDom(chatter.name, chatter.imgUrl, false, chatter.signature, chatter.status));
                }
                //原来有的状态，有新消息（公共状态下忽略不提醒）
                else if (statusMap.get(chatter.id) == 1) {
                    newStatusMap.set(chatter.id, 1);
                    $friend_list.append(chatterDom(chatter.name, chatter.imgUrl, true, chatter.signature, chatter.status));
                }
                chatterListData.push(chatter);
            } else {
                setChatterImage(chatter.imgUrl);
            }
        }
        //会话失效（不包含公共会话）
        if (null != getActiveChatter() && !chatterIsActive && getActiveChatter().id !== 'temp-chatter') {
            swal("会话已经被重置", "请重新选择会话", "warning")
            $(".chat__back")[0].click();
        }
        //更新状态map
        statusMap = newStatusMap;

        console.log(indexMap);
        //添加点击监听器
        addSessionListener(chatterListData);
    }

    getChatterList = function () {
        return chatterListData;
    }

    getIndexByChatterId = function (chatterId) {
        return indexMap.get(chatterId);
    }

    getChatterMap = function () {
        return chatterMap;
    }

    // 在当前聊天框内同步头像状态
    syncAvatarStatus = function(status) {
        let $imgCloned = $("img.cloned")[0]
        if (!$imgCloned) {
            return
        }
        console.log($imgCloned)
        let list = $("img.cloned")[0].classList
        if (status != -1 && status != 2) {
            list.remove("contact__photo__gray")
        } else {
            list.add("contact__photo__gray")
        }
    }

    $(".search__input").bind("keyup", function () {
        var content = $(".search__input")[0].value;
        //判断是否搜索内容为空
        var empty = true;
        $(".contact").each(function () {
            var name = $(this).find(".contact__name")[0].innerText;
            if (name.indexOf(content) == -1) {
                $(this).css("display", "none");
            } else {
                $(this).css("display", "");
                empty = false;
            }
        });
        if (empty) {
            $(".empty").css("display", "");
        } else {
            $(".empty").css("display", "none");
        }
    });
});