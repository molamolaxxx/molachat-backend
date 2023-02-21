// 侧边导航
$(document).ready(function () {
    $demo = $(".demo")
    $('.button-collapse').sideNav({
        menuWidth: 300, // Default is 240
        edge: 'left', // Choose the horizontal origin
        closeOnClick: false, // Closes side-nav on <a> clicks, useful for Angular/Meteor
        draggable: true // Choose whether you can drag to open on touch screens
    });

    var nav_img = $("#nav-img")[0]
    var nav_name = $("#nav-name")[0]
    var nav_sign = $("#nav-sign")[0]

    setNavImg = function(img) {
        nav_img.src = img
    }

    setNavName = function(name) {
        nav_name.innerText = name
    }

    setNavSign = function(sign) {
        nav_sign.innerText = sign
    }

    // 用户栏
    // 展示用户栏
    var $sidebar = $("#sidebar")
    var $frendlist = $(".friend-list")
    var isOutside = false
    showUserBar = function() {
        isOutside = true
        
        $sidebar.addClass("active")
        windowFilter(() => {
            $sidebar.addClass("sidebar-outside")
            $frendlist.css("height", "35rem")
        },() => {
            $sidebar.addClass("sidebar-outside-mobile")
            $frendlist.css("height", "40.5rem")
            $(".contact__status").addClass("mobile")
            $(".search").css("display", "none")
        })
        
    }

    // 收起用户栏
    hideUserBar = function() {
        isOutside = false
        $sidebar.removeClass("active")
        $frendlist.css("height", "45rem")
        windowFilter(() => {
            setTimeout(() => {
                $sidebar.removeClass("sidebar-outside")
                $sidebar.removeClass("sidebar-outside-mobile")
            },200)
        },() => {
            setTimeout(() => {
                $sidebar.removeClass("sidebar-outside")
                $sidebar.removeClass("sidebar-outside-mobile")
                $(".contact__status").removeClass("mobile")
                $(".search").css("display", "")
            },200) 
        })
    }

    isSideBarOutside = function() {
        return isOutside
    }

    // 手机终端下，需要缩小化展示状态
    needToShowStatusSmaller = function() {
        return isOutside && window.innerWidth < 800
    }

    var min_Width = 800
    windowFilter = function(e1, e2) {
        if (window.innerWidth >= min_Width && e1) {
            e1()
        } else if (e2) {
            e2()
        }
    }

    addResizeEventListener(function() {
        if (isOutside) {
            if (window.innerWidth >= min_Width) {
                $sidebar.removeClass("sidebar-outside-mobile")
                $(".search").css("display", "")
                $(".contact__status").removeClass("mobile")
            } else {
                $sidebar.addClass("sidebar-outside-mobile")
                $frendlist.css("height", "40.5rem")
                $(".contact__status").addClass("mobile")
                $(".search").css("display", "none")
            }
        }
    })

    var isVisible = true
    $("#tool-contacts").on('click', function() {
        if (isOutside) {
            hideUserBar()
            if (window.innerWidth < 1000) {
            setTimeout(function() {
                $demo.animate({ left: "50%" },200);
                },200)
            }
            
        } else {
            showUserBar()
            if (window.innerWidth < 1000) {
                setTimeout(function() {
                        $demo.animate({ left: "52%" },200);
                },200)
            }
            
        }
    })
})