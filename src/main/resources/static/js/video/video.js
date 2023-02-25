// 视频和语音通话
// 基于webrtc
$(document).ready(function() {

    var engines = {
        // 视频引擎
        videoEngine : VideoEngine(),
        // rtc引擎
        rtcEngine : RTCEngine(function(event){
            // onmessage回调
            console.log(event)
        },function(remoteStream) {
            // onaddstream 收到对方的视频流数据
            var video = $("#videoOther")[0];
            video.srcObject = remoteStream;
            video.onloadedmetadata = function(e) {
                video.play();
                removeSpinner()
            };
        },function() {
            setTimeout(function() {
                // onconnected
                var stream = $("#videoSelf")[0].srcObject;
                if (stream) {
                    getEngines().rtcEngine.sendStream(stream, false)
                } else {
                    console.log("no local stream avaliable")
                }
            },500)
            
        })
    }

    var requestCode = {
        // actionCode:
        VIDEO_REQUEST: 378,
        // 发起视频请求
        REQUEST_VIDEO_ON: 1269,
        // 取消视频请求\挂断
        REQUEST_VIDEO_OFF: 1272,
        // 信令交换
        SIGNALLING_CHANGE: 1483,
        // 取消通话请求
        REQUEST_CANCEL: 1485,
        // 通知对方切换状态
        VIDEO_STATE_CHANGE: 1486
    }
    var responseCode = {
        // actionCode:
        VIDEO_RESPONSE: 379,
        // 同意请求响应
        RESPONSE_ACCEPT: 2270,
        // 拒绝请求响应
        RESPONSE_REFUSE: 2271,
    }
    // 模态框
    var $modal = $('#video-modal')
    // 挂断按键
    var $off = $('#video-off')
    // 前置摄像头开关
    var $mirror = $('#open-mirror')
    // 前置摄像头
    var $videoSelf = $('#videoSelf')
    // 对方video
    var $videoOther = $("#videoOther")
    // 返回通话按键
    var $back = $("#back-to-video")
    // 取消请求键
    var $cancel = $("#cancel-request")

    // 选择个人摄像头
    var $choose_camera = $("#choose_camera")
    // 屏幕共享
    var $choose_screen = $("#choose_screen")
    // 全部video的状态对象
    var state = {
        // 是否打开前置摄像头
        openMirror: true,
        // 对方的id
        remoteChatterId: null       
    }
    /*
     dom操作
     */
    // 模态框初始化
    $modal.modal({
        dismissible: true, // Modal can be dismissed by clicking outside of the modal
        opacity: .2, // Opacity of modal background
        in_duration: 300, // Transition in duration
        out_duration: 200, // Transition out duration
        starting_top: '4%', // Starting top style attribute
        ending_top: '100%', // Ending top style attribute
        ready: function(modal, trigger) { // Callback for Modal open. Modal and trigger parameters available.
            
        },
        complete: function() { 
            // 判断是否是挂断电话
            if (state.remoteChatterId) {
                // 挂起
                $back.css("display","inline")
                showToast("视频挂起到后台", 1000)
            } 
            else {
                // 挂断
                $back.css("display","none")
                showToast("视频已挂断", 1000)
            }
        } 
    });
    // dom初始化位置
    $modal.css("max-width",800)
    if (window.innerWidth > 800) {
        $modal.css("left",(window.innerWidth - $modal.innerWidth())/2)
    }
    $off.css("left",($modal.innerWidth() - 56)/2)
    
    addResizeEventListener(function() {
        
        if (window.innerWidth > 800) {
            $modal.css("left",(window.innerWidth - 800)/2)
            $off.css("left",($modal.innerWidth - 56)/2)
        } else {
            $modal.css("left",0)
            $off.css("left",(window.innerWidth - 56)/2)
        }
    })
    

    /*
     监听事件
     */
    $mirror.on('click', function() {
        if (state.openMirror) {
            // 关闭前置
            $videoSelf.animate({opacity:0})
        } else {
            // 打开前置
            $videoSelf.animate({opacity:1})
        }
        state.openMirror = !state.openMirror
    })
    
    videoStateChange = function(st) {
        let req = {
            code: requestCode.VIDEO_REQUEST,
            msg: "video_state_change",
            data: {
                videoActionCode: requestCode.VIDEO_STATE_CHANGE,
                toChatterId: state.remoteChatterId,
                fromChatterId: getChatterId(),
                state: st
            }
        }
        getSocket().send(JSON.stringify(req))
    }

    videoOff = function() {
        let socket = getSocket()
        let req = {
            code: requestCode.VIDEO_REQUEST,
            msg: "request_video",
            data: {
                videoActionCode: requestCode.REQUEST_VIDEO_OFF,
                toChatterId: state.remoteChatterId,
                fromChatterId: getChatterId()
            }
        }
        state.remoteChatterId = null
        socket.send(JSON.stringify(req))
        // 关闭视频流
        engines.videoEngine.closeCamera(() => $modal.modal('close'))
        // 关闭rtc
        engines.rtcEngine.close()
        
    }
    $off.on('click', videoOff)

    // 用于取消通话的id
    var toCancelId = null;
    let func = function(){
        // 获得当前聊天窗口的chatter
        let activeChatter = getActiveChatter()
        console.log(activeChatter)
        // 判断是否是群聊
        if (activeChatter.id === "temp-chatter"){
            swal("Not Support", "暂且不支持群聊视频通话" , "warning");
            return
        }
        // 判断对方是否在线
        if (activeChatter.status != 1 || $(".cloned")[0].classList.contains("contact__photo__gray")) {
            swal("offline", "对方已经离线，无法发起视频通话" , "warning");
            return
        }
        // 正在和其他人通话
        if (engines.videoEngine.isOpen()) {
            showToast("您正在通话中", 1000)
            return
        }
        // 检测自己设备状态
        if (!engines.videoEngine.deviceTest(val=>{},err=>{
            swal("device error", "设备出现问题，请检查权限与设备连接" , "warning")
        })) {
            swal("device error", "设备出现问题，请检查权限与设备连接" , "warning");
            return
        }
        
        toCancelId = activeChatter.id
        
        swal("提示","是否与"+activeChatter.name+"进行视频通话?","info")
        .then(function (value) {
            // 发起视频请求
            if (value) {
                sendVideoRequest()
                $cancel.css("display","inline")
            } else {
                engines.videoEngine.closeCamera(()=>{})
            }
        });
    }
    $("#video").on('click',func);
    $("#tool-video").on('click',func);
    $back.on('click',function(){
        // 判断对方是否在线
        if (state.remoteChatterId) {
            $modal.modal('open')
        }
    });
    $cancel.on('click', function() {
        swal("提示","是否取消视频通话请求?","info")
        .then(function (value) {
            // 取消视频请求
            if (value) {
                sendCancelRequest()
                $cancel.css("display","none")
                engines.videoEngine.closeCamera(() => {})
            }
        });
    })

    /*
     api
     */
    // 发起视频请求
    sendCancelRequest = function() {
        let socket = getSocket()
        let req = {
            code: requestCode.VIDEO_REQUEST,
            msg: "request_video",
            data: {
                videoActionCode: requestCode.REQUEST_CANCEL,
                toChatterId: toCancelId,
                fromChatterId: getChatterId()
            }
        }
        socket.send(JSON.stringify(req))
        showToast("已取消视频邀请", 1000)
    }

    // 发起视频请求
    sendVideoRequest = function() {
        let socket = getSocket()
        let req = {
            code: requestCode.VIDEO_REQUEST,
            msg: "request_video",
            data: {
                videoActionCode: requestCode.REQUEST_VIDEO_ON,
                toChatterId: getActiveChatter().id,
                fromChatterId: getChatterId()
            }
        }
        socket.send(JSON.stringify(req))
        showToast("已发出视频邀请，等待对方响应", 1000)
    }


    // 获取状态
    getState = function() {
        return state
    }

    // 获取引擎
    getEngines = function() {
        return engines
    }

    setRemoteChatterId = function(remote) {
        state.remoteChatterId = remote
    }

    // 双击放大
    var isFull = false
    $videoOther.on("dblclick",function(e) {
        if (isFull) {
            $videoOther.removeClass("videoFullScreen")
        }else{
            $videoOther.addClass("videoFullScreen")
        }
        isFull = !isFull
    })

    // $videoSelf.on("dblclick",function(e) {
    //     if (isFull) {
    //         $videoSelf.removeClass("videoFullScreen")
    //     }else{
    //         $videoSelf.addClass("videoFullScreen")
    //     }
    //     isFull = !isFull
    // })

    $choose_camera.on("click", function(e) {
        swal("提示","是否切换至视频通信?","info")
        .then(function (value) {
            if (value) {
                shareVideo()
            }
            
        });
    })

    $choose_screen.on("click", function(e) {
        swal("提示","是否切换至屏幕共享?","info")
        .then(function (value) {
            if (value) {
                shareScreen()
            }
        });
    })

    
})