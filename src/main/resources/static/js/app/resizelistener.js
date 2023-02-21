$(document).ready(function() {

    const eventList = []
    addResizeEventListener = function(func) {
        eventList.push(func)
    }

    window.onresize = function() {
        eventList.forEach(func => {
            func()
        });
    }
})