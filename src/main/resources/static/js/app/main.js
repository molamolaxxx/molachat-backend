// 主页面ui逻辑
$(document).ready(function() {
    var $svg = $(".sidebar"),
        $demo = $(".demo"),
        $path = $(".s-path"),
        $sCont = $(".sidebar-content"),
        $chat = $(".chat"),
        $menu = $("#menu"),
        demoTop = $demo.offset().top,
        demoLeft = $demo.offset().left,
        diffX = 0,
        curX = 0,
        finalX = 0,
        frame = 1000 / 60,
        animTime = 600,
        sContTrans = 200,
        animating = false;

    var easings = {
        smallElastic: function(t, b, c, d) {
            var ts = (t /= d) * t;
            var tc = ts * t;
            return b + c * (33 * tc * ts + -106 * ts * ts + 126 * tc + -67 * ts + 15 * t);
        },
        inCubic: function(t, b, c, d) {
            var tc = (t /= d) * t * t;
            return b + c * (tc);
        }
    };

    function createD(top, ax, dir) {
        return "M0,0 " + top + ",0 a" + ax + ",250 0 1," + dir + " 0,520 L0,520";
    }

    var startD = createD(50, 0, 1),
        midD = createD(125, 75, 0),
        finalD = createD(200, 0, 1),
        clickMidD = createD(300, 80, 0),
        clickMidDRev = createD(200, 100, 1),
        clickD = createD(300, 0, 1),
        currentPath = startD;

    function newD(num1, num2) {
        var d = $path.attr("d"),
            num2 = num2 || 250,
            nd = d.replace(/\ba(\d+),(\d+)\b/gi, "a" + num1 + "," + num2);
        return nd;
    }

    function animatePathD(path, d, time, handlers, callback, easingTop, easingX) {
        var steps = Math.floor(time / frame),
            curStep = 0,
            oldArr = currentPath.split(" "),
            newArr = d.split(" "),
            oldTop = +oldArr[1].split(",")[0],
            topDiff = +newArr[1].split(",")[0] - oldTop,
            nextTop,
            nextX,
            easingTop = easings[easingTop] || easings.smallElastic,
            easingX = easings[easingX] || easingTop;
        $(document).off("mousedown mouseup");

        function animate() {
            curStep++;
            nextTop = easingTop(curStep, oldTop, topDiff, steps);
            //console.log(curStep+" "+oldTop+ " "+topDiff+" "+steps);
            nextX = easingX(curStep, curX, finalX - curX, steps);
            oldArr[1] = nextTop + ",0";
            oldArr[2] = "a" + Math.abs(nextX) + ",250";
            oldArr[4] = (nextX >= 0) ? "1,1" : "1,0";
            $path.attr("d", oldArr.join(" "));

            if (curStep > steps) {
                curX = 0;
                diffX = 0;
                $path.attr("d", d);
                currentPath = d;
                if (handlers) handlers1();
                if (callback) callback();
                return;
            }
            requestAnimationFrame(animate);
        }
        animate();
    }

    function handlers1() {

        $(document).on("mousedown touchstart", ".s-path", function(e) {
            var startX = e.pageX || e.originalEvent.touches[0].pageX;

            $(document).on("mousemove touchmove", function(e) {
                var x = e.pageX || e.originalEvent.touches[0].pageX;
                diffX = x - startX;
                if (diffX < 0) diffX = 0;
                if (diffX > 300) diffX = 300;
                curX = Math.floor(diffX / 2);
                $path.attr("d", newD(curX));
            });
        });

        $(document).on("mouseup touchend", function() {
            $(document).off("mousemove touchmove");
            if (animating) return;
            if (!diffX) return;
            if (diffX < 40) {
                animatePathD($path, newD(0), animTime, true);
            } else {
                animatePathD($path, finalD, animTime, false, function() {
                    $sCont.addClass("active");
                    setTimeout(function() {
                        $("#keyboard_arrow_left").on("click", e => {
                            // closeSidebar(e); // 关闭侧栏
                            openTerminal(); // 打开控制台
                        }
                    );
                    }, sContTrans);
                });
            }
        });

    }

    handlers1();


    function openTerminal() {
        swal({
            content: {
                element: "input",
                attributes: {
                    placeholder: "请输入js指令",
                    type: "text",
                },
            },
        }).then((value) => {
            if (value) {
                openTerminal()
                try {
                     showToast(eval(value), 1800)  
                } catch (error) {
                    showToast("指令错误："+ error, 1800)  
                } 
            }
        })
    }

    function closeSidebar(e) {
        if ($(e.target).closest(".sidebar-content").length ||
            $(e.target).closest(".chat").length) return;
        if (animating) return;
        animating = true;
        $sCont.removeClass("active");
        $chat.removeClass("active");
        $(".cloned").addClass("removed");
        finalX = -75;
        setTimeout(function() {
            animatePathD($path, midD, animTime / 3, false, function() {
                $chat.hide();
                $(".cloned").remove();
                finalX = 0;
                curX = -75;
                animatePathD($path, startD, animTime / 3 * 2, true);
                animating = false;
            }, "inCubic");
        }, sContTrans);
        $(document).off("click", closeSidebar);
    }

    function moveImage(that) {
        var $img = $(that).find(".contact__photo"),
            top = $img.offset().top - $demo.offset().top,
            left = $img.offset().left - $demo.offset().left,
            $clone = $img.clone().addClass("cloned");

        $clone.css({ top: top, left: left });
        $demo.append($clone);
        $clone.css("top");
        $clone.css({ top: "1.8rem", left: "25rem" });
    }

    function ripple(elem, e) {
        var elTop = elem.offset().top,
            elLeft = elem.offset().left,
            x = e.pageX - elLeft,
            y = e.pageY - elTop;
        var $ripple = $("<div class='ripple-contact'></div>");
        $ripple.css({ top: y, left: x });
        elem.append($ripple);
    }
    //初始化提示框
    $('.tooltipped').tooltip({ delay: 50 });

    $(document).on("click", ".contact", function(e) {
        //判断是否在上传文件，上传则不能退出
        if (window.uploadLock) {
            swal("warning", "文件正在上传，请勿退出", "warning");
            return;
        }
        var that = this,
            name = $(that).find(".contact__name").text()
        $(".chat__name").text(name);
        ripple($(that), e);
        // 如果用户栏在外部，省略动画
        if (isSideBarOutside()) {
            // moveImage(that);
            var $img = $(that).find(".contact__photo")
            var online = $(that).find(".contact__photo__gray")
            let $cloned = $(".cloned")
            $cloned[0].src = $img[0].src
            console.log(online.length)
            if (online.length > 0) {
                $cloned.addClass("contact__photo__gray")
            } else {
                $cloned.removeClass("contact__photo__gray")
            }
            return
        }

        if (animating) return;
        animating = true;
        $(document).off("click", closeSidebar);
        var online = $(this).find(".contact__status").hasClass("online");

        // $intro.innerText = $(this).find(".contact_intro")[0].innerText;
  
        $(".chat__online").removeClass("active");
        if (online) $(".chat__online").addClass("active");
        setTimeout(function() {
            $sCont.removeClass("active");
            moveImage(that);
            finalX = -80;
            setTimeout(function() {
                $(".ripple").remove();
                animatePathD($path, clickMidD, animTime / 3, false, function() {
                    curX = -80;
                    finalX = 0;
                    animatePathD($path, clickD, animTime * 2 / 3, true, function() {
                        $chat.show();
                        $chat.css("top");
                        $chat.addClass("active");
                        animating = false;
                        // 展现用户栏
                        showUserBar()
                    });
                }, "inCubic");
            }, sContTrans);
        }, sContTrans);
        //添加表情包、文件传输工具
        
        $("#insert_emoticon").css("display", "");
        $("#video").css("display", "");
        $("#file_copy").css("display", "");
        $("#keyboard_arrow_left").css("display", "none");
        $("#multichat").css("display", "none");
        if (window.innerWidth <= 1000) {
            $menu.css("display", "none");
            setTimeout(function() {
                $demo.animate({ left: "52%" },200);
            },800)
            
        }
    });

    $(document).on("click", ".chat__back", function() {
        //判断是否在上传文件，上传则不能退出
        if (window.uploadLock) {
            swal("warning", "文件正在上传，请勿退出", "warning");
            return;
        }
        if (animating) return;
        animating = true;
        $chat.removeClass("active");
        $(".cloned").addClass("removed");
        setTimeout(function() {
            $(".cloned").remove();
            // 撤销隐藏用户栏的显示
            hideUserBar()
            $chat.hide();
            finalX = 100;
            animatePathD($path, clickMidDRev, animTime / 3, false, function() {
                curX = 100;
                finalX = 0;
                animatePathD($path, finalD, animTime * 2 / 3, true, function() {
                    $sCont.addClass("active");
                    $(document).off("click", closeSidebar);
                    animating = false;
                    
                });
            }, "inCubic");
            
        }, sContTrans);
        //非缩小模式，删去对应工具
        $("#insert_emoticon").css("display", "none");
        $("#file_copy").css("display", "none");
        $("#video").css("display", "none");
        $("#keyboard_arrow_left").css("display", "");
        $("#multichat").css("display", "");
        if (window.innerWidth <= 1000) {
            setTimeout(function() {
                $menu.animate({ opacity: 1 }, function() {
                    $menu.css("display", "");
                });
                
            }, 100);
            $demo.animate({ left: "50%" },200);
        }
    });

    var $user_info = $(".user_info")
    var $account_box = $("#account_box")
    userInfoUIAdjust = function() {
        //定位用户框
        if (window.innerWidth <= 1000) {
            $user_info.css("opacity", "0");
            $user_info.css("width", "80%");
            $user_info.css("display", "none");
            $(".collapsible-body").css("background", "#f0f8ff");
            var marginRight = window.innerWidth * 0.2 / 2;
        } else {
            $user_info.css("opacity", "0");
            $user_info.css("width", "25%");
            $user_info.css("z-index", "1");
            $(".collapsible-body").css("background", "rgba(0, 0, 0, 0)")
            var marginRight = ((window.innerWidth - 420) / 2 - window.innerWidth / 4) / 2
        }
        $account_box.css("display", "");
        $("#insert_emoticon").css("display", "none");
        $("#file_copy").css("display", "none");
        $("#video").css("display", "none");
        $user_info.css("right", marginRight)

    }

    var $html = $("html")
    let funcRemSizeChange = function() {
        if (window.innerWidth <= 600) {
            $html.css("font-size","68%")
        } else {
            $html.css("font-size","74%")
        }
    }

    userInfoUIAdjust();
    funcRemSizeChange();
    var demoTop = $demo.offset().top;
    var $btn = $(".fixed-action-btn");
    
    $(window).on("resize", function() {
        // if (Math.abs(demoTop - $demo.offset().top) < 100) {
        //     $btn.css({display : ""})
        //     $btn.animate({opacity : 1})
        // } else {
        //     $btn.css({display : "none"})
        //     $btn.css({opacity : 0})
        // }
        funcRemSizeChange()
    });

    var openFlag = false;
    let func = function() {

        if (openFlag) {
            if (!($(".collapsible-header.active")[0] == null)) {
                $('.collapsible-header').click();
            }
            setTimeout(function() {
                $(".user_info").animate({ opacity: 0 }, function() {
                    $(".user_info").css("display", "none");
                });
            }, 100);

        } else {
            if ($(".collapsible-header.active")[0] == null) {
                setTimeout(function() {
                    $('.collapsible-header').click();
                }, 200);
            }
            $(".user_info").css("display", "");
            $(".user_info").animate({ opacity: 1 });
        }
        openFlag = !openFlag;
    }

    
    $("#account_box").on("click", func);
    $("#tool-account").on("click", func);

    $(".gravatar").on("click", function() {
        console.log("click image");
        swal({
            content: {
                element: "input",
                attributes: {
                    placeholder: "请输入头像的合法链接",
                    type: "text",
                },
            },
            buttons: {
                defeat: {
                    text: "随机获取头像",
                    value: "rand",
                    color: "red"
                },
                cancel: "cancel",

                catch: {
                    text: "ok",
                    value: "ok",
                },

            }
        }).then((value) => {
            //删去原有的名片窗口,前提是手机窗口
            if (window.innerWidth <= 600) {
                $(".gray").click();
            }
            if (null == value) {
                return;
            }
            switch (value) {
                case "ok":
                    {
                        //获取输入链接，查看是否合法
                        var url = $(".swal-content__input")[0].value;
                        imageSrc(url);
                        break;
                    }
                case "rand":
                    {
                        //随机
                        console.log("random");
                        var rand = Math.ceil(Math.random() * 1000000000) % 15 + 1;
                        url = "img/header/" + rand + ".jpeg"
                        imageSrc(url);
                        break;
                    }

                default:
                    {
                        imageSrc(url);
                    }
            }
        });

        function imageSrc(url) {
            var ImgObj = new Image();
            ImgObj.src = url;
            setTimeout(function() {
                if (ImgObj.width > 0 && ImgObj.height > 0) {

                    //向服务器发送更新请求
                    $.ajax({
                        url: getPrefix() + "/chat/chatter",
                        dataType: "json",
                        type: "PUT",
                        xhrFields: {withCredentials:true},
                        crossDomain: true,
                        data: {
                            "id": getChatterId(),
                            "imgUrl": url,
                            "token": localStorage.getItem("token")
                        },
                        success: function(result) {
                            swal("good job!", "获取头像成功", "success")
                                .then((value) => {
                                    //设置自身头像
                                    $("img.gravatar")[0].src = url;
                                    localStorage.setItem("imgUrl", url);
                                });
                        },
                        error: function(result) {
                            var exception = JSON.parse(result.responseText);
                            swal("Bad Day", "获取头像失败，原因是:" + exception.msg, "error");
                        }
                    })
                } else {
                    swal("Bad request...", "获取头像无效", "error")
                }
            }, 500);

        }
        window.addEventListener('online', function() {
            swal('网络连接恢复！');
        })
        window.addEventListener('offline', function() {
            swal('网络连接中断！');
        })

    });
});