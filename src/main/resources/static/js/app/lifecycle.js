// example.js file
// Wait for device API libraries to load
//
$(document).ready(function() {
    onLoad()
})
function onLoad() {
    document.addEventListener("deviceready", onDeviceReady, false);
}
// device APIs are available
//
function onDeviceReady() {
    document.addEventListener("pause", onPause, false);
    document.addEventListener("resume", onResume, false);
    document.addEventListener("menubutton", onMenuKeyDown, false);
    // Add similar listeners for other events
}

function onPause() {
    // Handle the pause event
}

function onResume() {
    // 立刻发送心跳
    sendHeartBeat()
}

function onMenuKeyDown() {
    // Handle the menubutton event
}