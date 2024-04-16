$(document).ready(function () {
    if (isApp) {
        return
    }
    Notification.requestPermission().then((result) => {
        console.log("网页版通知权限申请结果", result);
    });

    sendNotification = function (text) {
        if (isApp) {
            return
        }
        if (isCurrentPage) {
            return
        }
        const img = "img/mola2.jpg";
        const notification = new Notification("molachat", {
            body: text,
            icon: img
        });
        setTimeout(function() {
            notification.close();
          }, 3500);
    }
})