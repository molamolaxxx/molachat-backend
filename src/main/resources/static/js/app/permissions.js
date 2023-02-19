$(document).ready(function() {
    var permissions = cordova.plugins.permissions;

    function requestPermission(permission, succCallback,failedCallback) {
        permissions.checkPermission(permission, function(s) {
            //hasPermission 验证是否成功
            if (!s.hasPermission) {
                //没有权限
                //app申请写入权限
                permissions.requestPermission(permission, function(s) {
                    if (s.hasPermission) {
                        //申请成功回调
                        succCallback()
                    }
                    else {
                        //申请失败回调
                        failedCallback()
                    }
                }, function(error) {
                    failedCallback()
                });
            } else {
                //拥有权限
                succCallback()
            }
        }, function(error) {
            // 出错
            failedCallback()
        });
    }
    //校验app是否有安卓写入权限
    requestPermission(permissions.CAMERA, function() {
        requestPermission(permissions.RECORD_AUDIO, function(){
            requestPermission(permissions.RECORD_AUDIO, function(){},function(){
                swal("permission", "获取权限失败，请手动添加权限" , "warning");
            })
        },function(){
            swal("permission", "获取权限失败，请手动添加权限" , "warning");
        })
    },function() {
        swal("permission", "获取权限失败，请手动添加权限" , "warning");
    })
    // 剪切板权限
    requestPermission(permissions.RECORD_AUDIO, function(){},function(){
        swal("permission", "获取权限失败，请手动添加权限" , "warning");
    })
})