$(document).ready(function() {

    var localStream = null

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
        // 失败code
        RESPONSE_FAILED: 2269,
        // 同意请求响应
        RESPONSE_ACCEPT: 2270,
        // 拒绝请求响应
        RESPONSE_REFUSE: 2271,
    }

    // dom
    var $modal = $('#video-modal')

    // 取消请求键
    var $cancel = $("#cancel-request")

    // 个人video
    var $videoSelf = $("#videoSelf")
    var videoSelf = $("#videoSelf")[0]

    // 对方video
    var $videoOther = $("#videoOther")
    var videoOther = $("#videoOther")[0]

    // request-handler
    receiveVideoRequest = function(data) {
        let requestType = data.videoRequestType
        let requestChatterId = data.fromChatterId
        let requestChatterName = data.fromChatterName
        // console.table(data)
        switch(requestType) {
            case requestCode.REQUEST_VIDEO_ON: {
                console.log("视频请求")
                handleVideoOn(requestChatterId, requestChatterName)
                break
            }
            case requestCode.REQUEST_VIDEO_OFF: {
                console.log("视频挂断")
                handleVideoOff()
                break
            }
            case requestCode.SIGNALLING_CHANGE: {
                console.log("信令交换")
                handleSignallingChange(data)
                break
            }
            case requestCode.REQUEST_CANCEL: {
                console.log("取消请求")
                handleCancelRequest()
                break
            }
            case requestCode.VIDEO_STATE_CHANGE: {
                console.log("改变视频状态")
                handleVideoStateChange(data.state)
            }
        }
    }

    handleCancelRequest = function() {
        // 判断当前是否有通话
        if (getState().remoteChatterId) {
            return
        }
        swal("提示","对方取消了通话","info").then(()=>{
            // 更改remoteId
            setRemoteChatterId(null)
            // 关闭相机
            let engines = getEngines()
            engines.videoEngine.closeCamera(() => $modal.modal('close'))
            // stream改为null
            localStream = null
            // 关闭rtc
            engines.rtcEngine.close()
        })
    }

    // response-handler
    receiveVideoResponse = function(data) {
        let responseType = data.videoResponseType
        let from = data.fromChatterId
        let engines = getEngines()
        $cancel.css("display","none")
        switch(responseType) {
            case responseCode.RESPONSE_ACCEPT: {
                // 更改remoteId
                setRemoteChatterId(from)
                // 打开模态框
                engines.videoEngine.openCamera(stream => {
                    addSpinner("video-modal")
                    $modal.modal('open')
                    // 建立webrtc连接
                    let rtc = getEngines().rtcEngine
                    rtc.createPeerConnection()
                    // 发送信令
                    rtc.createOffer(true)
                    // 发送视频数据流
                    setTimeout(()=> {
                        var stream = videoSelf.srcObject;
                        if (stream) {
                            localStream = stream
                            rtc.sendStream(localStream, true)
                        }
                    },1000) 
                    // 设置轮询，如果自己没收到对方的数据流，则不断发出
                    var timer = setInterval(()=>{
                        let sp = $(".spinner")[0]
                        if (sp && videoOther.currentTime == 0) {
                            // 说明还在等待，立即发出stream
                            let rtc = getEngines().rtcEngine
                            // if (!rtc.isSucc()) {
                            //     rtc.createPeerConnection()
                            // }
                            rtc.sendStream(videoSelf.srcObject, true)
                            console.log("轮询，查看是否需要发出数据流")
                        } else{
                            clearInterval(timer)
                            removeSpinner()
                        }
                    },2500)
                })
                
                break
            }
            case responseCode.RESPONSE_REFUSE: {
                showToast("对方拒绝视频请求", 1000)
                console.log("对方拒绝请求")
                // 更改remoteId
                setRemoteChatterId(null)
                // 关闭摄像机
                engines.videoEngine.closeCamera(function(){})
                break
            }
            case responseCode.RESPONSE_FAILED: {
                swal("提示",data.msg,"warning").then(()=>{
                    // 更改remoteId
                    setRemoteChatterId(null)
                    // 关闭摄像头
                    engines.videoEngine.closeCamera(() => $modal.modal('close'))
                    // 关闭rtc
                    engines.rtcEngine.close()
                })
                break
            }
        }
    }

    // 收到视频请求
    function handleVideoOn(requestChatterId, requestChatterName) {
        swal("提示","是否接受"+requestChatterName+"的视频通话邀请?","info").then(rs => {
            // 检测自己设备状态
            if (rs && !getEngines().videoEngine.deviceTest(stream => {
                localStream = stream
                getEngines().videoEngine.openCamera(() => {
                    addSpinner("video-modal")
                    $modal.modal('open')
                    setTimeout(() => {
                        var stream = videoSelf.srcObject;
                        if (stream) {
                            localStream = stream
                            getEngines().rtcEngine.sendStream(localStream, false)
                        }
                    },1000)
                    // 设置轮询，如果自己没收到对方的数据流，则不断发出
                    // video上一次轮训的播放位置
                    var timer = setInterval(()=>{
                        let sp = $(".spinner")[0]
                        if (sp && videoOther.currentTime == 0) {
                            // 说明还在等待，立即发出stream
                            let rtc = getEngines().rtcEngine
                            // if (!rtc.isSucc()) {
                            //     rtc.createPeerConnection()
                            // }
                            rtc.sendStream(videoSelf.srcObject, false)
                            console.log("轮询，查看是否需要发出数据流")
                        } else {
                            clearInterval(timer)
                            removeSpinner()
                            console.log("取消轮询")
                        }
                    },2500)
                })
            },err => {
                swal("device error", "设备出现问题，请检查权限与设备连接" , "warning").then(()=>videoOff())
                rs = false
            })) {
                swal("device error", "设备出现问题，请检查权限与设备连接" , "warning");
                // 挂断
                rs = false
            }
            if (rs) {
                // 接受
                let action = {
                    code: responseCode.VIDEO_RESPONSE,
                    msg: "request_video",
                    data: {
                        videoActionCode: responseCode.RESPONSE_ACCEPT,
                        toChatterId: requestChatterId,
                        fromChatterId: getChatterId()
                    }
                }
                // 发送接受的action
                getSocket().send(JSON.stringify(action))
                // 更改remoteId
                setRemoteChatterId(requestChatterId)
                // 获取rtc通道
                getEngines().rtcEngine.createPeerConnection()
                
            } else {
                // 拒绝
                showToast("您已拒绝对方视频请求", 1000)
                let action = {
                    code: responseCode.VIDEO_RESPONSE,
                    msg: "request_video",
                    data: {
                        videoActionCode: responseCode.RESPONSE_REFUSE,
                        toChatterId: requestChatterId,
                        fromChatterId: getChatterId()
                    }
                }
                getSocket().send(JSON.stringify(action))
                // 关闭摄像头
                getEngines().videoEngine.closeCamera(() => $modal.modal('close'))
            }
            // 左下按钮消失
            $cancel.css("display","none")
        })
    }

    // 对方发起信令，交换
    function handleSignallingChange(data) {
        getEngines().rtcEngine.signallingHandle(data)
    }

    function handleVideoStateChange(state) {
        if (state === 'video') {
            $videoOther.addClass("rotateVideo")
        } else if (state === 'screen') {
            $videoOther.removeClass("rotateVideo")
        }
    }

    // 对方挂断视频
    function handleVideoOff() {
        // 设置state的remoteId为null
        setRemoteChatterId(null)
        // 关闭相机
        let engines = getEngines()
        engines.videoEngine.closeCamera(() => $modal.modal('close'))
        // stream改为null
        localStream = null
        // 关闭rtc
        engines.rtcEngine.close()
    }

    /**
     * 分享屏幕
     */
    shareScreen = function() {
        var videoEngine = getEngines().videoEngine
        var rtcEngine = getEngines().rtcEngine
        if (videoEngine.isOpen()) {
            videoEngine.changeStream("screen", function(stream) {
                // rtc发送到远端
                console.log("开始发送到远端")
                rtcEngine.sendStream(stream, true)
                // 修改videoSelf的旋转角度
                $videoSelf.removeClass("rotateVideo")
                // 通知对方修改角度
                videoStateChange("screen")
            })
        } else {
            console.log("视频未打开")
        }
    }

    /**
     * 分享video
     */
    shareVideo = function() {
        var videoEngine = getEngines().videoEngine
        var rtcEngine = getEngines().rtcEngine
        if (videoEngine.isOpen()) {
            videoEngine.changeStream("video", function(stream) {
                // rtc发送到远端
                console.log("开始发送到远端")
                rtcEngine.sendStream(stream, true)
                // 修改videoSelf的旋转角度
                $videoSelf.addClass("rotateVideo")
                // 通知对方修改角度
                videoStateChange("video")
            })
        } else {
            console.log("视频未打开")
        }
    }
})