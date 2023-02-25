$(document).ready(function() {
    addSpinner = function(elementId, lock) {
        if (lock) {
            $(".page_lock").css("display","inline")
        }
        if ($(".spinner")[0]) {
            return
        }
        var spinner = "<div class=\"spinner\">\n"+
            "      <div class=\"rect1\"></div>\n"+
            "      <div class=\"rect2\"></div>\n"+
            "      <div class=\"rect3\"></div>\n"+
            "      <div class=\"rect4\"></div>\n"+
            "      <div class=\"rect5\"></div>\n"+
            "      <div class=\"rect6\"></div>\n"+
            "      <div class=\"rect7\"></div>\n"+
            "      <div class=\"rect8\"></div>\n"+
            "  </div>"
        $("#"+elementId).append(spinner)
    }

    removeSpinner = function () {
        $(".spinner").remove();
        $(".page_lock").css("display","none")
    }
})