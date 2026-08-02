$(document).ready(function () {
    var $teamModal = $("#teams-modal")
    var $teamList = $(".teams-list")
    var $empty = $(".teams-empty")
    var $notice = $("#teams-runtime-notice")
    var teams = []
    var candidates = []
    var refreshTimer = null
    var refreshAttempts = 0
    var deleteOperations = {}
    var knownTeamStates = {}
    var teamStatesInitialized = false
    var teamCapabilityReady = false
    var acknowledgedTeamResultIds = new Set()

    var activeTeamStorageKey = function () {
        return "fastTeam.activeTeamId." + getChatterId()
    }

    var getActiveTeamId = function () {
        return getChatterId() ? localStorage.getItem(activeTeamStorageKey()) : null
    }

    var updateModeControls = function () {
        var activeTeamId = getActiveTeamId()
        var activeTeam = teams.find(function (team) {
            return team.teamId === activeTeamId
        })
        $("#teams-mode-badge").text(activeTeam ? activeTeam.name : "主会话")
        $("#leaveTeamBtn").prop("disabled", !activeTeamId)
    }

    var notifyTeamReady = function (teamName) {
        var message = "队伍「" + teamName + "」已就绪"
        showToast(message, 1800)
        if (typeof sendNotification === "function") {
            sendNotification(message)
        }
    }

    var notifyTeamStatusChanges = function (nextTeams) {
        var nextStates = {}
        nextTeams.forEach(function (team) {
            var status = team.status || team.state
            nextStates[team.teamId] = {
                name: team.name,
                status: status
            }
            var previous = knownTeamStates[team.teamId]
            if (teamStatesInitialized && previous && status === "READY"
                    && (previous.status === "CREATING" || previous.status === "RECOVERING")) {
                notifyTeamReady(team.name)
            }
        })
        knownTeamStates = nextStates
        teamStatesInitialized = true
    }

    $teamModal.css("max-width", 600)

    var resetModalPosition = function () {
        if (getInnerWidth() > 600) {
            $teamModal.css("left", (getInnerWidth() - $teamModal.innerWidth()) / 2)
        } else {
            $teamModal.css("left", 0)
        }
    }

    resetModalPosition()
    addResizeEventListener(resetModalPosition)

    $teamModal.modal({
        dismissible: true,
        opacity: .2,
        in_duration: 300,
        out_duration: 200,
        starting_top: "4%",
        ending_top: "100%"
        ,
        complete: function () {
            if (refreshTimer) {
                clearTimeout(refreshTimer)
                refreshTimer = null
            }
            refreshAttempts = 0
        }
    })

    var authData = function () {
        return {
            chatterId: getChatterId(),
            token: localStorage.getItem("token")
        }
    }

    var showRuntimeNotice = function (message) {
        $notice.text(message).show()
    }

    var setTeamCapabilityReady = function (ready) {
        teamCapabilityReady = ready
        $("#createTeamBtn")
            .prop("disabled", !ready)
            .attr("title", ready ? "发起 Team" : "Fast Team 环境未就绪")
    }

    var getTeamChatters = function (teamId) {
        return (window.latestRawChatterList || []).filter(function (chatter) {
            return chatter.robotGroup === "team-acp" && chatter.teamId === teamId
        })
    }

    var getTeamActivity = function (teamId) {
        if (typeof isChatterStreaming === "function"
                && getTeamChatters(teamId).some(function (chatter) {
                    return isChatterStreaming(chatter.id)
                })) {
            return "streaming"
        }
        if (typeof hasUnreadMessage === "function"
                && getTeamChatters(teamId).some(function (chatter) {
                    return hasUnreadMessage(chatter.id)
                        && !acknowledgedTeamResultIds.has(chatter.id)
                })) {
            return "unread"
        }
        return null
    }

    var renderTeams = function () {
        $teamList.empty()
        $empty.toggle(teams.length === 0)
        teams.forEach(function (team) {
            var teamStatus = team.status || team.state
            var teamActivity = getTeamActivity(team.teamId)
            var $row = $("<button type='button' class='team-row'></button>")
            $row.attr("data-team-id", team.teamId)
            $row.toggleClass("team-row--deleting", teamStatus === "DELETING")
            var $nameLine = $("<span class='team-row__name-line'></span>")
            $nameLine.append($("<span class='team-row__name'></span>").text(team.name))
            if (teamActivity) {
                $nameLine.append($("<span class='team-row__activity'></span>")
                    .addClass("team-row__activity--" + teamActivity)
                    .attr("aria-label", teamActivity === "streaming"
                        ? "队伍成员正在运行" : "队伍有待确认的运行结果")
                    .attr("title", teamActivity === "streaming"
                        ? "队伍成员正在运行" : "点击确认运行结果"))
            }
            $row.append($nameLine)
            if (team.teamId === getActiveTeamId()) {
                $row.addClass("team-row--active")
                $row.append("<span class='team-row__current'>当前小队</span>")
            }
            $row.append($("<span class='team-row__meta'></span>")
                .text((team.members ? team.members.length : 0) + " 位成员 · "
                    + teamStatus + (team.runtimeUnavailable ? " · 实例离线/不可用" : "")))
            if (teamStatus === "FAILED" && team.lastError) {
                $row.append($("<span class='team-row__error'></span>")
                    .text(team.lastError.message || "成员启动失败"))
            }
            if (teamStatus === "READY" || teamStatus === "FAILED") {
                $row.append("<i class='material-icons team-row__delete' "
                    + "aria-label='删除队伍'>delete</i>")
            }
            $teamList.append($row)
        })
        updateModeControls()
    }

    refreshTeamActivityIndicators = function () {
        var chatterIds = new Set((window.latestRawChatterList || []).map(function (chatter) {
            return chatter.id
        }))
        acknowledgedTeamResultIds.forEach(function (chatterId) {
            if (!chatterIds.has(chatterId)) {
                acknowledgedTeamResultIds.delete(chatterId)
            }
        })
        renderTeams()
    }

    markTeamResultPending = function (chatterId) {
        acknowledgedTeamResultIds.delete(chatterId)
    }

    var acknowledgeTeamResults = function (teamId) {
        if (typeof hasUnreadMessage !== "function") {
            return
        }
        getTeamChatters(teamId).forEach(function (chatter) {
            if (hasUnreadMessage(chatter.id)) {
                acknowledgedTeamResultIds.add(chatter.id)
            }
        })
        renderTeams()
    }

    var refreshTeams = function () {
        $notice.hide()
        $.ajax({
            url: getPrefix() + "/chat/team/capability",
            type: "get",
            dataType: "json",
            timeout: 5000,
            data: authData(),
            success: function (result) {
                if (result.data && result.data.businessCommandsReady) {
                    setTeamCapabilityReady(true)
                    $notice.hide()
                } else {
                    showRuntimeNotice("Fast Team transport已连接，业务命令正在初始化")
                }
            },
            error: function (xhr) {
                setTeamCapabilityReady(false)
                if (xhr.status === 400) {
                    showRuntimeNotice("登录状态已失效，请重新连接后再使用Fast Team")
                } else if (xhr.status === 503) {
                    showRuntimeNotice("Fast Team连接正在恢复，普通ACP在线状态不受影响")
                } else {
                    showRuntimeNotice("Fast Team能力检查失败，请稍后重试")
                }
            }
        })
        $.ajax({
            url: getPrefix() + "/chat/team",
            type: "get",
            dataType: "json",
            timeout: 5000,
            data: authData(),
            success: function (result) {
                notifyTeamStatusChanges(result.data || [])
                teams = result.data || []
                renderTeams()
                scheduleCreatingRefresh()
            },
            error: function (xhr) {
                var response = xhr.responseJSON || {}
                if (xhr.status === 503) {
                    showRuntimeNotice("Fast Team连接正在恢复，已有队伍保持展示但暂不可操作")
                } else if (response.msg) {
                    showRuntimeNotice(response.msg)
                }
                $.ajax({
                    url: getPrefix() + "/chat/team/snapshot",
                    type: "get",
                    dataType: "json",
                    timeout: 5000,
                    data: authData(),
                    success: function (snapshotResult) {
                        teams = (snapshotResult.data || []).map(function (team) {
                            team.runtimeUnavailable = true
                            return team
                        })
                        renderTeams()
                    }
                })
            }
        })
        $.ajax({
            url: getPrefix() + "/chat/team/candidates",
            type: "get",
            dataType: "json",
            timeout: 5000,
            data: authData(),
            success: function (result) {
                candidates = result.data || []
                var unsupportedInstances = candidates.filter(function (candidate) {
                    return candidate.status === "DISCOVERY_UNSUPPORTED"
                }).map(function (candidate) {
                    return candidate.cmdProxyInstanceId
                })
                if (unsupportedInstances.length > 0
                        && !candidates.some(function (candidate) {
                            return candidate.status === "AVAILABLE"
                        })) {
                    showRuntimeNotice("cmd-proxy 实例 " + unsupportedInstances.join("、")
                        + " 不支持 Team 来源发现，请升级 cmd-proxy")
                }
            },
            error: function () {
                candidates = []
            }
        })
    }

    var scheduleCreatingRefresh = function () {
        var creating = teams.some(function (team) {
            var status = team.status || team.state
            return status === "CREATING" || status === "RECOVERING"
        })
        if (!creating || refreshTimer || refreshAttempts >= 90) {
            return
        }
        refreshAttempts++
        refreshTimer = setTimeout(function () {
            refreshTimer = null
            refreshTeams()
        }, 1500)
    }

    openTeamModal = function () {
        if (!getChatterId()) {
            showToast("请先连接服务器", 1000)
            return
        }
        setTeamCapabilityReady(false)
        refreshTeams()
        $teamModal.modal("open")
    }

    $("#teams").on("click", openTeamModal)

    $(document).on("click", ".team-row", function (event) {
        if ($(event.target).hasClass("team-row__delete")) {
            return
        }
        var teamId = $(this).attr("data-team-id")
        var team = teams.find(function (item) {
            return item.teamId === teamId
        })
        if (!team || (team.status || team.state) !== "READY") {
            showToast("队伍尚未就绪", 1000)
            return
        }
        acknowledgeTeamResults(team.teamId)
        if (team.teamId === getActiveTeamId()) {
            showToast("已确认当前小队的运行结果", 1000)
            return
        }
        enterTeam(team)
    })

    $(document).on("click", ".team-row__delete", function (event) {
        event.stopPropagation()
        var teamId = $(this).closest(".team-row").attr("data-team-id")
        var team = teams.find(function (item) {
            return item.teamId === teamId
        })
        if (!team || ["READY", "FAILED"].indexOf(team.status || team.state) < 0) {
            showToast("仅就绪或创建失败的队伍可以删除", 1000)
            return
        }
        swal({
            title: "删除队伍「" + team.name + "」？",
            text: "队内会话和临时通讯录将被关闭，此操作不可撤销。",
            icon: "warning",
            buttons: {
                cancel: "取消",
                confirm: {
                    text: "确认删除",
                    value: "delete"
                }
            },
            dangerMode: true
        }).then(function (value) {
            if (value === "delete") {
                deleteTeam(team)
            }
        })
    })

    $("#leaveTeamBtn").on("click", function () {
        leaveTeamMode()
        $teamModal.modal("close")
    })

    var showChatterPage = function () {
        if (typeof getActiveChatter === "function" && getActiveChatter()
                && $(".chat__back").length > 0) {
            $(".chat__back").trigger("click")
        }
    }

    var enterTeam = function (team) {
        localStorage.setItem(activeTeamStorageKey(), team.teamId)
        showChatterPage()
        if (typeof refreshChatterForTeamMode === "function") {
            refreshChatterForTeamMode()
        }
        updateModeControls()
        $teamModal.modal("close")
    }

    var leaveTeamMode = function () {
        localStorage.removeItem(activeTeamStorageKey())
        showChatterPage()
        if (typeof refreshChatterForTeamMode === "function") {
            refreshChatterForTeamMode()
        }
        updateModeControls()
    }

    filterChattersForActiveTeam = function (chatterList, selfId) {
        var activeTeamId = getActiveTeamId()
        return chatterList.filter(function (chatter) {
            if (chatter.id === selfId) {
                return true
            }
            if (activeTeamId) {
                return chatter.robotGroup === "team-acp" && chatter.teamId === activeTeamId
            }
            return chatter.robotGroup !== "team-acp"
        })
    }

    var createCandidateRow = function (candidate, index) {
        var wrapper = document.createElement("label")
        wrapper.className = "team-candidate"

        var checkbox = document.createElement("input")
        checkbox.type = "checkbox"
        checkbox.className = "team-candidate__check"
        checkbox.value = index
        checkbox.disabled = candidate.status !== "AVAILABLE"
        wrapper.appendChild(checkbox)

        var avatar = document.createElement("img")
        avatar.className = "team-candidate__avatar"
        avatar.src = candidate.avatar || "img/kiro.png"
        avatar.alt = candidate.displayName
        wrapper.appendChild(avatar)

        var content = document.createElement("span")
        content.className = "team-candidate__content"
        var name = document.createElement("strong")
        name.innerText = candidate.displayName
        content.appendChild(name)
        if (candidate.onlyTeamMember) {
            var role = document.createElement("em")
            role.className = "team-candidate__role"
            role.innerText = "仅 Team Member"
            content.appendChild(role)
        }
        if (candidate.status !== "AVAILABLE") {
            var status = document.createElement("small")
            var statusLabels = {
                BUSINESS_COMMANDS_NOT_READY: "业务命令未就绪",
                DISCOVERY_STALE: "discovery 已过期",
                TRANSPORT_UNREACHABLE: "transport 不可达",
                DISCOVERY_UNSUPPORTED: candidate.remark
            }
            status.innerText = statusLabels[candidate.status] || candidate.status
            content.appendChild(status)
        }
        if (candidate.remark && candidate.status !== "DISCOVERY_UNSUPPORTED") {
            var remark = document.createElement("small")
            remark.innerText = candidate.remark
            content.appendChild(remark)
        }
        wrapper.appendChild(content)
        return wrapper
    }

    var hasAvailablePlacement = function () {
        var counts = candidates.filter(function (candidate) {
            return candidate.status === "AVAILABLE"
        }).reduce(function (groups, candidate) {
            var key = candidate.cmdProxyInstanceId + "\n" + candidate.transportGroup
            groups[key] = (groups[key] || 0) + 1
            return groups
        }, {})
        return Object.keys(counts).some(function (key) {
            return counts[key] >= 2
        })
    }

    $("#createTeamBtn").on("click", function () {
        if (!teamCapabilityReady) {
            swal("当前无法发起Team",
                "Fast Team连接正在恢复，这不代表普通ACP离线", "info")
            return
        }
        if (!hasAvailablePlacement()) {
            swal("暂时无法发起 Team",
                "至少需要同一 cmd-proxy 实例中的 2 个可用 ACP robot", "info")
            return
        }

        var form = document.createElement("div")
        form.className = "team-create-form"

        var nameInput = document.createElement("input")
        nameInput.className = "team-create-form__name"
        nameInput.maxLength = 40
        nameInput.placeholder = "队伍名称"
        nameInput.addEventListener("input", function () {
            if (nameInput.value.trim()) {
                nameInput.classList.remove("team-create-form__name--invalid")
                nameInput.removeAttribute("aria-invalid")
            }
        })
        form.appendChild(nameInput)

        var candidateList = document.createElement("div")
        candidateList.className = "team-candidate-list"
        candidates.forEach(function (candidate, index) {
            candidateList.appendChild(createCandidateRow(candidate, index))
        })
        form.appendChild(candidateList)

        var validateCreateForm = function () {
            if (!nameInput.value.trim()) {
                nameInput.classList.add("team-create-form__name--invalid")
                nameInput.setAttribute("aria-invalid", "true")
                nameInput.focus()
                showToast("请填写队伍名称", 1200)
                return false
            }
            var selectedCount = form.querySelectorAll(
                ".team-candidate__check:checked").length
            if (selectedCount < 2 || selectedCount > 6) {
                showToast("请选择 2~6 个成员", 1200)
                return false
            }
            var selectedPlacements = new Set(Array.from(form.querySelectorAll(
                ".team-candidate__check:checked")).map(function (checkbox) {
                var candidate = candidates[Number(checkbox.value)]
                return candidate.cmdProxyInstanceId + "\n" + candidate.transportGroup
            }))
            if (selectedPlacements.size !== 1) {
                showToast("成员必须来自同一个 cmd-proxy 实例", 1500)
                return false
            }
            return true
        }

        var createDialog = swal({
            title: "发起 Team",
            content: form,
            buttons: {
                cancel: "取消",
                confirm: {
                    text: "确认发起",
                    value: "create",
                    closeModal: false
                }
            }
        })
        var confirmButton = document.querySelector(".swal-button--confirm")
        if (confirmButton) {
            confirmButton.addEventListener("click", function (event) {
                if (!validateCreateForm()) {
                    event.preventDefault()
                    event.stopImmediatePropagation()
                }
            }, true)
        }
        createDialog.then(function (value) {
            if (value !== "create") {
                return
            }
            var name = nameInput.value.trim()
            var selected = Array.from(form.querySelectorAll(".team-candidate__check:checked"))
                .map(function (checkbox) {
                    return candidates[Number(checkbox.value)]
                })
            swal.close()
            createTeam(name, selected)
        })
    })

    var createTeam = function (name, selected) {
        var payload = {
            chatterId: getChatterId(),
            token: localStorage.getItem("token"),
            requestId: createUuid(),
            name: name,
            members: selected.map(function (candidate) {
                return {
                    cmdProxyInstanceId: candidate.cmdProxyInstanceId,
                    transportGroup: candidate.transportGroup,
                    sourceRobotId: candidate.sourceRobotId,
                    sourceGroupId: candidate.sourceGroupId
                }
            })
        }
        $.ajax({
            url: getPrefix() + "/chat/team",
            type: "post",
            contentType: "application/json",
            dataType: "json",
            timeout: 10000,
            data: JSON.stringify(payload),
            success: function () {
                refreshTeams()
            },
            error: function (xhr) {
                var response = xhr.responseJSON || {}
                var message = response.msg || "Team运行时暂不可用"
                if (message.indexOf("sourceGroupId does not resolve") >= 0) {
                    message = "所选ACP成员已离线或配置已失效，请刷新成员列表后重新选择"
                }
                swal("发起 Team 失败", message, "warning")
                refreshTeams()
            }
        })
    }

    var deleteTeam = function (team) {
        var operation = deleteOperations[team.teamId]
        if (!operation) {
            operation = {
                requestId: createUuid(),
                expectedVersion: team.version
            }
            deleteOperations[team.teamId] = operation
        }
        team.status = "DELETING"
        team.state = "DELETING"
        renderTeams()
        $.ajax({
            url: getPrefix() + "/chat/team/" + encodeURIComponent(team.teamId),
            type: "delete",
            dataType: "json",
            timeout: 35000,
            data: {
                chatterId: getChatterId(),
                token: localStorage.getItem("token"),
                requestId: operation.requestId,
                expectedVersion: operation.expectedVersion
            },
            success: function () {
                delete deleteOperations[team.teamId]
                if (getActiveTeamId() === team.teamId) {
                    leaveTeamMode()
                }
                showToast("队伍已删除", 1000)
                refreshTeams()
            },
            error: function (xhr) {
                var response = xhr.responseJSON || {}
                swal("删除失败", (response.msg || "清理尚未完成") + "，可再次点击删除重试",
                    "warning")
                refreshTeams()
            }
        })
    }

    var createUuid = function () {
        if (window.crypto && window.crypto.randomUUID) {
            return window.crypto.randomUUID()
        }
        return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, function (char) {
            var random = Math.random() * 16 | 0
            var value = char === "x" ? random : (random & 0x3 | 0x8)
            return value.toString(16)
        })
    }
})
