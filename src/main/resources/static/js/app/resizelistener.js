$(document).ready(function() {

    const eventList = []
    addResizeEventListener = function(func) {
        eventList.push(func)
    }

    window.onresize = function() {
        // 窗口大小变化时重新计算zoom
        applyZoom()
        eventList.forEach(func => {
            func()
        });
    }
})