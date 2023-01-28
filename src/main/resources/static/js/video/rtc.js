$(document).ready(function() {
    // 摄像头、视频引擎
    VideoEngine = function(){
        // 本地视频流数据
        var localStream = null
        // 打开状态
        var open = false
        // 当前视频类型 1、video摄像头 2、screen屏幕分享
        var curType = "video"
        // 打开摄像头
        var openCamera = function(callback) {
            curType = "video"
            if (localStream) {
                videoSelf.srcObject = localStream;
                videoSelf.onerror = function () {
                    stream.stop();
                };
                localStream.onended = function(err) {console.log(err)};
                videoSelf.onloadedmetadata = function () {
                    videoSelf.play()                
                    // let $toastContent = $('<span style="font-size:14px">摄像头已打开</span>');
                    // Materialize.toast($toastContent, 1000)
                };
                // 调用回调函数
                callback(localStream)
            }
        }

        // 关闭摄像头
        var closeCamera =  function(callback) {
            if(localStream != null){
                open = false
                closeStream(localStream)
                localStream = null;
                // 回调函数
                callback()
                // let $toastContent = $('<span style="font-size:14px">摄像头已关闭</span>');
                // Materialize.toast($toastContent, 1000)
            } 
        }

        // 关闭流
        function closeStream(stream) {
            if(stream.getVideoTracks()[0]){
                stream.getVideoTracks()[0].stop();
            }
            if(stream.getAudioTracks()[0]){
                stream.getAudioTracks()[0].stop();
            }
            if(stream.getTracks()[0]){
                stream.getTracks()[0].stop();
            }
        }
        // 设备检测
        var deviceTest = function(callback,errCallback) {
            if (open) {
                addSpinner("video-modal")
                $('#video-modal').modal('open')
                $("#videoSelf")[0].srcObject = localStream
                setTimeout(() => {
                    getEngines().rtcEngine.sendStream(localStream, false)                    
                },1000)
                return true
            }
            navigator.getUserMedia = navigator.getUserMedia ||
                navigator.webkitGetUserMedia ||
                navigator.mozGetUserMedia ||
                navigator.msGetUserMedia; //获取媒体对象（这里指摄像头）
                // 音视频通话
                videoAndAudio = {
                    video: true,
                    audio: true
                }
        
                try {
                    //参数1获取用户打开权限；参数二成功打开后调用，并传一个视频流对象，参数三打开失败后调用，传错误信息
                    navigator.getUserMedia(videoAndAudio,gotStream, noStream); 
                    // navigator.mediaDevices.getDisplayMedia(videoAndAudio)
					// 		  .then(gotStream)
					// 		  .catch(noStream)
                } catch (err) {
                    console.log(err)
                    alert(err)
                    return false
                }
                function gotStream(stream) {
                    open = true
                    console.log(stream)
                    localStream = stream
                    // 获取视频流之后的回调
                    callback(stream)
                }
                function noStream(err) {
                    console.log(err)
                    alert(err)
                    errCallback(err)
                }
            return true
        }
        var isOpen = () => {return open}

        /**
         * 改变video的类型
         * @param {*} type 
         * @function changeRemote 改变远端的视频流
         */
        var changeStream = function(type, changeRemote) {
            if (!isOpen()) {
                console.warn("视频未开启")
                return
            }
            // if (type === curType) {
            //     showToast("正处于当前类型的通信状态", 1000)
            //     return
            // }
            getStreamByType(type, stream=>{
                if (stream){
                    console.log(stream)
                    // 关闭原来的stream
                    closeStream(localStream)
                    localStream = stream
                    $("#videoSelf")[0].srcObject = localStream
                    if (changeRemote) {
                        changeRemote(stream)
                    }
                    curType = type
                } else {
                    console.error("不支持视频类型"+type)
                }
            })
            
        }

        var getStreamByType = function(type, callback) {
            navigator.getUserMedia = navigator.getUserMedia ||
                navigator.webkitGetUserMedia ||
                navigator.mozGetUserMedia ||
                navigator.msGetUserMedia; //获取媒体对象（这里指摄像头）
                // 音视频通话
                videoAndAudio = {
                    video: true,
                    audio: true
                }
            if (type === 'video') {
                navigator.getUserMedia(videoAndAudio,gotStream, noStream); 
            } else if (type === 'screen') {
                navigator.mediaDevices.getDisplayMedia(videoAndAudio)
							  .then(gotStream)
							  .catch(noStream)
            } else{
                return null
            }
            function gotStream(stream) {
                open = true
                callback(stream)
            }
            function noStream(err) {
                console.log(err)
                alert(err)
            }
        }
        return {
            openCamera,closeCamera,deviceTest,isOpen, getStreamByType, changeStream
        }
    }

    RTCEngine = function(onmessage, onaddstream, onconnected){
        var iceServer = {
            "iceServers": [
            {
                url: "stun:120.27.230.24",
                credential: "molamolaxxx",
                username:"mola"
            },
            {
                url: "stun:stunserver.org"
            },{
                url: "stun:stun.voipbuster.com"
            },{
                url:"stun:stun.voiparound.com"
            },{
                url:"stun:stun.voipstunt.com"
            },{
                url: "stun:stun.ekiga.net"
            },{
                url: "stun:stun.ideasip.com"
            },{
                url: "turn:120.27.230.24",
                credential: "molamolaxxx",
                username:"mola"
            } ]
        };
        var PeerConnection = RTCPeerConnection;
        //兼容不同浏览器
        var SessionDescription = (window.RTCSessionDescription || window.mozRTCSessionDescription || window.webkitRTCSessionDescription);
        var pc = null;//本地peerConnection对象
        var oppositeChannel = null;//远端的数据通道
        var isSucc = null;
        var createPeerConnection = function(){
            isSucc = false
            //创建PeerConnection实例
            pc = new PeerConnection(iceServer);
            pc.localChannel = pc.createDataChannel({
                ordered: false,
                maxRetransmitTime: 6000,
            });//本地通道,本地通道接收由远程通道发送过来的数据
            pc.localChannel.onerror = function (error) {
                console.log("数据传输通道建立异常:", error);
              };
            pc.localChannel.onopen = function () {
                console.log("本地数据通道建立成功");
              };
            pc.localChannel.onclose = function () {
                console.log("关闭数据传输通道");
                close();
            };
            pc.localChannel.onmessage = function(event){
                onmessage(event);
            };
            pc.ondatachannel = function(event) {
                //对方的通道,发送数据使用这个通道,则发到对方的本地通道的onmessage回调,反之使用本地通道发送数据则oppositeChannel通道的onmessage接收到数据
                console.log("接受对方信道");
                oppositeChannel = event.channel;
            };
            pc.onaddstream = function(event){//如果检测到媒体流连接到本地，将其绑定到一个video标签上输出
                console.log("收到对方数据流");
                onaddstream(event.stream);
            };
            pc.onicecandidate = function(event){//发送候选到其他客户端
                if (event.candidate !== null) {
                    var candidate = {"candidate":event.candidate,"type":"_candidate"};
                    // 发送candidate信令
                    sendSignal(candidate)
                }
            };
            pc.onconnectionstatechange = function(event) {
                switch(pc.connectionState) {
                  case "connected":{
                    isSucc = true
                    // 连接成功
                    console.log("webrtc连接成功")
                    // 连接成功回调
                    onconnected()
                    break;
                  }
                  case "disconnected": {
                  }
                  case "failed":{
                    // 连接失败
                    console.log("webrtc失败")
                    break;
                  }
                  case "closed":{
                    // 关闭
                    console.log("webrtc关闭")
                    break;
                  }
                }
              }
        }

        /**
         * 通道是否建立成功
         */
        var isSuccess = function() {
            return isSucc
        }
        
        /**
         * 处理发送过来的信令  temp 在群组的时候代表 回复给指定的人
         * @param {jsonObject} json 收到的消息,json对象
         */
        var signallingHandle = function(json){
            //如果是一个ICE的候选，则将其加入到PeerConnection中，否则设定对方的session描述为传递过来的描述
            if(json.type === "_candidate" ){
                pc.addIceCandidate(new RTCIceCandidate(json.candidate));
            }else{
                pc.setRemoteDescription(new SessionDescription(json.sdp),
                    function(){
                        // 如果是一个offer，那么需要回复一个answer
                        if(json.type === "_offer") {
                            pc.createAnswer(function(desc){
                                pc.setLocalDescription(desc);
                                // 发送answer信令
                                sendSignal({"sdp":desc,"type":"_answer"})
                            }, function (error) {
                                console.log("响应信令失败:" + error);
                            });
                        }
                    }
                );
            }
        }

        // 发送一个offer
        var createOffer = function(needSendOffer) {
            pc.createOffer(function(desc){
                pc.setLocalDescription(desc);
                console.log("needSendOffer", needSendOffer);
                if (needSendOffer) {
                    sendSignal({"sdp":desc,"type":"_offer"});
                }
            }, function (error) {
                console.log("发起信令失败:" + error);
            });
        }
        /**
         * 关闭webrtc通道
         */
        var close = function(){
            if(pc != null){
                pc.localChannel.close();
                pc.close();
                pc = null;
                oppositeChannel = null;
            }
        }

        function sendSignal(data) {
            let code = 378 // 信令交换code
            let fullData = {
                videoActionCode: 1483,
                sdp: data.sdp,
                candidate: data.candidate,
                type: data.type,
                fromChatterId: getChatterId(),
                toChatterId: getState().remoteChatterId
            }
            let action = {
                code: code,
                msg: "send_signal",
                data: fullData
            }
            // 发送接受的action
            getSocket().send(JSON.stringify(action))
        }

        var sendStream = function(stream, needSendOffer) {
            pc.addStream(stream)
            // 发送方需要sendOffer
            setTimeout(() => {
                createOffer(needSendOffer)
            },1000)
        }
        return {
            createPeerConnection, // 建立连接
            sendStream, // 发送视频流到channel
            signallingHandle, // 处理发来的信令
            createOffer, // 发送offer请求,
            close,
            isSucc
        }
    }
})
