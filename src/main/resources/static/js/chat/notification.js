$(document).ready(function () {
    // 权限申请
    if (!isApp) {
        Notification.requestPermission().then((result) => {
            console.log("网页版通知权限申请结果", result);
        });
    }

    sendNotification = function (text) {
        if (isCurrentPage) {
            return
        }
        if (isApp) {
            sendNotificationInApp(text)
        } else {
            sendNotificationInWeb(text)
        }
        
    }

    function sendNotificationInWeb(text) {
        const img = "img/mola2.jpg";
        const notification = new Notification("molachat", {
            body: text,
            icon: img
        });
        setTimeout(function() {
            notification.close();
          }, 3500);
    }

    function sendNotificationInApp(text) {
        if (!cordova.plugins.notification) {
            return
        }
        cordova.plugins.notification.local.schedule({
            title: '您有新的通知',
            text,
            foreground: true
        });
    }
})