// app全局的一些配置
var DEFAULT_HOST = "molaspace.xyz"
var DEFAULT_LOCAL_HOST = "127.0.0.1"
var DEFAULT_PORT = "8550"

var ipAndPort = getIpAndPort()
// ip
var _ip = ipAndPort['ip']
// 端口
var _port = ipAndPort['port']

var isApp = false

function getIpAndPort() {
    var ip = ""
    var port = ""
    // 判断是否web页面
    if (location.href.indexOf("file://") >= 0) {
        // web-app， 此处可进行服务器选择
        ip = localStorage.getItem("_ip") ? localStorage.getItem("_ip") : DEFAULT_HOST
        port = localStorage.getItem("_port") ? localStorage.getItem("_port") :DEFAULT_PORT
        isApp = true
    } else {
        // web页面，不可进行选择
        let str = location.href.split("/")[2]
        let configArr = str.split(":")
        if (configArr.length == 2) {
            ip = configArr[0]
            port = configArr[1]
        } else if (configArr.length == 1) {
            ip = configArr[0]
            if ( ip === '127.0.0.1' || ip === 'localhost' || ip === '192.168.1.7') { // 测试使用
                ip = DEFAULT_HOST
            }
            port = DEFAULT_PORT
        } else {
            ip = DEFAULT_HOST
            port = DEFAULT_PORT
        }
    }
    return {ip, port}
}


function setIp(a) {
    _ip = a
    localStorage.setItem("_ip",_ip)
}

function getIp() {
    return _ip
}

function setPort(a) {
    _port = a;
    localStorage.setItem("_port",_port)
}

function getPort() {
    return _port
}

//获取前缀
function getPrefix() {
    return "https://" + _ip + ":" + _port
}

// 获取socket前缀
function getSocketPrefix() {
    return "wss://" + _ip + ":" + _port
}

/**
 * 判断当前是否是app
 */
function isAppNow() {
    return isApp;
}

/**
 * 获取当前服务器前缀，用于区分localStroage
 */
function getCurrentServerPrefix() {
    if (isApp) {
        return _ip + _port
    }
    return ""
}

// 测试地址合法性
function testHostValid() {
    let url = getPrefix() + "/chat/app/host"
    $.ajax({
        url: url,
        dataType: "json",
        type: "GET",
        xhrFields: {withCredentials:true},
        crossDomain: true,
        success: function (result) {
            return true
        },
        error: function (result) {
            return false
        }
    })
}