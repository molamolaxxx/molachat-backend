/**
 * 公共函数，可在全局调用
 */
//更改昵称
var $body = $("body")
function changeName() {
    setTimeout(() => {
        document.getElementsByClassName("swal-content__input")[0].value = getChatterName();
    }, 200);
    swal({
        content: {
            element: "input",
            attributes: {
                placeholder: "请输入昵称",
                type: "text",
            },
        },
    }).then((value) => {
        //删去原有的名片窗口,前提是手机窗口
        // if (window.innerWidth <= 600) {
        //     $(".gray").click();
        // }

        var chatterName = value;
        if (null == chatterName || "" == chatterName) {
            return;
        }

        if (chatterName.indexOf(" ") != -1 || chatterName.indexOf("　") != -1) {
            swal("Sad", "昵称不合法，请重新输入", "error");
            return;
        }

        var chatterId = getChatterId();
        // 如果chatterId为空，即还未登录的情况
        if(!chatterId) {
            // 本地修改name
            localStorage.setItem("chatterName", chatterName);
            // 登录
            setChatterName(chatterName);
            createChatter();
            return
        }
        $.ajax({
            url: getPrefix() + "/chat/chatter",
            dataType: "json",
            type: "PUT",
            xhrFields: {withCredentials:true},
            crossDomain: true,
            data: {
                "id": chatterId,
                "name": chatterName,
                "token":localStorage.getItem("token")
            },
            success: function (result) {
                swal("Nice Guy!", "修改昵称成功！", "success")
                    .then((value) => {
                        setChatterName(chatterName);
                        localStorage.setItem("chatterName", chatterName);
                    });
            },
            error: function (result) {
                console.log(result.responseText);
                var exception = JSON.parse(result.responseText);
                swal("Bad Day", "修改昵称失败，原因是" + exception.msg, "error");
            }
        })
    });;
}

function copyText(text) {
    if (typeof cordova !== 'undefined') {
        try {
            // 执行代码
            cordova.plugins.clipboard.copy(text);
            showToast("已复制到剪切板", 1000)
        } catch (error) {
            // 捕获异常
            showToast("复制到剪切板失败, " + error, 1000)
        }
        return
    }
    navigator.clipboard.writeText(text).then(function() {
        showToast("已复制到剪切板", 1000)
    }, function(err) {
        showToast("复制到剪切板失败, " + err, 1000)
    });
}


//更改签名
function changeSign() {
    setTimeout(() => {
        let dom =  document.getElementsByClassName("swal-content__input")[0];
        if (getChatterSign() !== 'signature'){
            document.getElementsByClassName("swal-content__input")[0].value = getChatterSign();
        }
    }, 200);
    swal({
        content: {
            element: "input",
            attributes: {
                placeholder: "请输入签名",
                type: "text",
            },
        },
    }).then((value) => {
        //删去原有的名片窗口,前提是手机窗口
        // if (window.innerWidth <= 600) {
        //     $(".gray").click();
        // }

        var chatterSign = value;
        if (null == chatterSign || "" == chatterSign) {
            return;
        }

        if (chatterSign.indexOf(" ") != -1 || chatterSign.indexOf("　") != -1) {
            // swal("Sad", "签名不能包含空格", "error");
            showToast("签名不能包含空格",1000)
            return;
        }

        var chatterId = getChatterId();
        // 如果chatterId为空，即还未登录的情况
        if(!chatterId) {
            // 本地修改name
            localStorage.setItem("sign", chatterSign);
            // 登录
            setChatterSign(chatterSign);
            createChatter();
            return
        }
        $.ajax({
            url: getPrefix() + "/chat/chatter",
            dataType: "json",
            type: "PUT",
            xhrFields: {withCredentials:true},
            crossDomain: true,
            data: {
                "id": chatterId,
                "signature": chatterSign,
                "token": localStorage.getItem("token")
            },
            success: function (result) {
                swal("Nice Guy!", "修改签名成功！", "success")
                    .then((value) => {
                        setChatterSign(chatterSign);
                        localStorage.setItem("sign", chatterSign);
                    });
            },
            error: function (result) {
                console.log(result.responseText);
                var exception = JSON.parse(result.responseText);
                swal("Bad Day", "修改签名失败，原因是" + exception.msg, "error");
            }
        })
    });;
}


if (window.innerWidth <= 1000) {
    $(".demo").on("click", function () {
        //如果名片打开，则关闭
        if ($(".collapsible-header.active")[0] != null) {
            $("#account_box").click();
        }
    });
}

// 时间戳格式化
function times(value) {
    var date = new Date(parseInt(value))
    var tt = [date.getFullYear(), date.getMonth() + 1, date.getDate()].join('-') + ' ' + [date.getHours(), date.getMinutes()/10 < 1 ?( "0" + date.getMinutes()) : date.getMinutes() ].join(':');
    return tt;
}

function isEmpty(param) {
    return typeof param === "undefined" || param === null || param === ""
}

function isNull(param) {
    return typeof param === "undefined" || param === null
}

//通过字节截取string
function cutStrByByte(str,lenLimit){
    var len = 0;
    for (var i=0; i<str.length; i++) {
        var result="";
        var c = str.charCodeAt(i);
        //单字节加1
        if (c>=0&&c<=128) {
            len++;

        }
        else {
            len+=2;
        }
        if(len>lenLimit)
            return str.substring(0,i)+"...";
    }
    return str;
}


var isCurrentPage = true;
function listenCurrentPage(){
    var hiddenProperty = 'hidden' in document ? 'hidden' :    
    'webkitHidden' in document ? 'webkitHidden' :    
    'mozHidden' in document ? 'mozHidden' :    
    null;
    var visibilityChangeEvent = hiddenProperty.replace(/hidden/i, 'visibilitychange');
    var onVisibilityChange = function(){
        if (!document[hiddenProperty]) {    
            //console.log('页面激活');
            isCurrentPage = true;
            // 清除未读消息
            document.getElementsByTagName("title")[0].innerText = "molachat" ;
        }else{
            //console.log('页面非激活')
            isCurrentPage = false;
        }
    }
    document.addEventListener(visibilityChangeEvent, onVisibilityChange);
}
openFAB = function(menu) {
    var c = menu;
    if (c.hasClass("active") === !1) {
        var d, e, f = c.hasClass("horizontal");
        f === !0 ? e = 40 : d = 40,
        c.addClass("active"),
        c.find("ul .btn-floating").velocity({
            scaleY: ".4",
            scaleX: ".4",
            translateY: d + "px",
            translateX: e + "px"
        }, {
            duration: 0
        });
        var g = 0;
        c.find("ul .btn-floating").reverse().each(function() {
            $(this).velocity({
                opacity: "1",
                scaleX: "1",
                scaleY: "1",
                translateY: "0",
                translateX: "0"
            }, {
                duration: 80,
                delay: g
            }),
            g += 40
        })
    }
}
closeFAB = function(menu) {
    var b, c, d = menu, e = d.hasClass("horizontal");
    e === !0 ? c = 40 : b = 40,
    d.removeClass("active");
    d.find("ul .btn-floating").velocity("stop", !0),
    d.find("ul .btn-floating").velocity({
        opacity: "0",
        scaleX: ".4",
        scaleY: ".4",
        translateY: b + "px",
        translateX: c + "px"
    }, {
        duration: 80
    })
}
listenCurrentPage();

showToast = function(str, during) {
    let $toastContent = $('<span style="font-size:14px;width: 100%;text-align: center;">'+str+'</span>')
    Materialize.toast($toastContent, during) 
}


bodyScaleTransform = function(scale) {
    if (!scale) {
        scale = 1.1
    }
    $body.animate({zoom:scale},50);
}

netPing = function (url, onFailed) {
    $.ajax({
        type: "GET",
        cache: false,
        url: url,
        success: function() {
            
        },
        error: function() {
            onFailed()
        }
    });
}

