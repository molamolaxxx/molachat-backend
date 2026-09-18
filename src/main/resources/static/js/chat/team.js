$(document).ready(function () {
    var $teamModal = $("#teams-modal")
    var $teamList = $(".teams-list")
    var $empty = $(".teams-empty")
    var $notice = $("#teams-runtime-notice")
    var teams = []
    var candidates = []
    var homeState = null
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
        $("#leave-team-quick").prop("hidden", !activeTeamId)
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

    var teamStatusLabel = function (status) {
        var labels = {
            CREATING: "创建中",
            READY: "运行中",
            RECOVERING: "部分成员离线，等待恢复",
            DELETING: "删除中",
            PENDING_CLEANUP: "删除未完成，可重试",
            FAILED: "创建失败"
        }
        return labels[status] || status
    }

    var renderTeams = function () {
        $teamList.empty()
        $empty.toggle(teams.length === 0)
        teams.forEach(function (team) {
            var teamStatus = team.status || team.state
            var teamMode = team.mode || "NORMAL"
            var captain = (team.members || []).find(function (member) {
                return member.teamMemberId === team.captainTeamMemberId
            })
            var teamActivity = getTeamActivity(team.teamId)
            var $row = $("<button type='button' class='team-row'></button>")
            $row.attr("data-team-id", team.teamId)
            $row.toggleClass("team-row--deleting", teamStatus === "DELETING")
            var $nameLine = $("<span class='team-row__name-line'></span>")
            $nameLine.append($("<span class='team-row__name'></span>").text(team.name))
            if (teamMode === "CAPTAIN") {
                $nameLine.append($("<span class='team-row__mode'></span>").text("队长模式"))
            }
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
                    + (teamMode === "CAPTAIN"
                        ? "队长：" + (captain ? captain.displayName : "待确认") + " · "
                        : "普通模式 · ")
                    + teamStatusLabel(teamStatus)
                    + (team.runtimeUnavailable ? " · 实例离线/不可用" : "")))
            if (teamStatus === "FAILED" && team.lastError) {
                $row.append($("<span class='team-row__error'></span>")
                    .text(team.lastError.message || "成员启动失败"))
            }
            if (["READY", "FAILED", "RECOVERING", "PENDING_CLEANUP"]
                    .indexOf(teamStatus) >= 0) {
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
            url: getPrefix() + "/chat/team/home",
            type: "get",
            dataType: "json",
            timeout: 5000,
            data: authData(),
            success: function (result) {
                homeState = result.data || null
            },
            error: function () {
                homeState = null
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
                var unsupportedCount = candidates.filter(function (candidate) {
                    return candidate.status === "DISCOVERY_UNSUPPORTED"
                }).length
                if (unsupportedCount > 0
                        && !candidates.some(function (candidate) {
                            return candidate.status === "AVAILABLE"
                        })) {
                    showRuntimeNotice("当前 ACP 设备不支持 Team 来源发现，请升级后重试")
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
        if (!team || ["READY", "FAILED", "RECOVERING", "PENDING_CLEANUP"]
                .indexOf(team.status || team.state) < 0) {
            showToast("当前队伍状态不能发起删除", 1000)
            return
        }
        swal({
            title: "删除队伍「" + team.name + "」？",
            text: "将停止并删除所有设备上的成员会话。远程设备离线时会保留清理任务，恢复后可继续处理。",
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

    $("#leave-team-quick").on("click", function () {
        leaveTeamMode()
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

    updateModeControls()

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
        var wrapper = document.createElement("div")
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
        var heading = document.createElement("span")
        heading.className = "team-candidate__heading"
        var name = document.createElement("strong")
        name.innerText = candidate.displayName
        heading.appendChild(name)
        if (candidate.onlyTeamMember) {
            var role = document.createElement("em")
            role.className = "team-candidate__role"
            role.innerText = "仅 Team Member"
            heading.appendChild(role)
        }
        var captainChoice = document.createElement("label")
        captainChoice.className = "team-candidate__captain"
        captainChoice.hidden = true
        var captainRadio = document.createElement("input")
        captainRadio.type = "radio"
        captainRadio.name = "teamCaptainCandidate"
        captainRadio.className = "team-candidate__captain-radio"
        captainRadio.value = index
        captainRadio.disabled = candidate.status !== "AVAILABLE"
        captainChoice.appendChild(captainRadio)
        captainChoice.appendChild(document.createTextNode("设为队长"))
        heading.appendChild(captainChoice)
        content.appendChild(heading)
        if (candidate.status !== "AVAILABLE") {
            var status = document.createElement("small")
            var statusLabels = {
                BUSINESS_COMMANDS_NOT_READY: "业务命令未就绪",
                DISCOVERY_STALE: "设备已离线",
                TRANSPORT_UNREACHABLE: "设备暂不可达",
                HOME_SELECTION_REQUIRED: "请先选择本机设备",
                MIXED_UNSUPPORTED: "暂不支持跨设备组队",
                DISCOVERY_UNSUPPORTED: candidate.remark
            }
            status.innerText = statusLabels[candidate.status] || candidate.status
            content.appendChild(status)
        }
        var remark = document.createElement("input")
        remark.type = "text"
        remark.className = "team-candidate__remark"
        remark.dataset.candidateIndex = index
        remark.maxLength = 200
        remark.placeholder = "Team 备注（可选）"
        remark.setAttribute("aria-label", candidate.displayName + " Team 备注")
        remark.disabled = candidate.status !== "AVAILABLE"
        content.appendChild(remark)
        wrapper.appendChild(content)
        wrapper.addEventListener("click", function (event) {
            if (event.target === checkbox || event.target === remark
                    || event.target.closest(".team-candidate__captain") || checkbox.disabled) {
                return
            }
            checkbox.click()
        })
        return wrapper
    }

    var candidatePlacementKey = function (candidate) {
        return candidate.cmdProxyInstanceId + "\n" + candidate.transportGroup
    }

    var appendCandidateGroups = function (container) {
        var groups = {}
        candidates.forEach(function (candidate, index) {
            var key = candidatePlacementKey(candidate)
            if (!groups[key]) {
                groups[key] = {
                    sourceType: candidate.sourceType || "REMOTE",
                    candidates: []
                }
            }
            groups[key].candidates.push({candidate: candidate, index: index})
        })
        var ordered = Object.keys(groups).sort(function (left, right) {
            if (groups[left].sourceType === groups[right].sourceType) {
                return left.localeCompare(right)
            }
            return groups[left].sourceType === "HOME" ? -1 : 1
        })
        var remoteNumber = 0
        ordered.forEach(function (key) {
            var group = groups[key]
            var section = document.createElement("section")
            section.className = "team-candidate-group"
            var title = document.createElement("h4")
            if (group.sourceType === "HOME") {
                title.innerText = "本机"
            } else {
                remoteNumber++
                title.innerText = "远程设备 " + remoteNumber
            }
            section.appendChild(title)
            group.candidates.forEach(function (item) {
                section.appendChild(createCandidateRow(item.candidate, item.index))
            })
            container.appendChild(section)
        })
    }

    var selectHomeDevice = function () {
        if (!homeState || !homeState.selectionRequired || !homeState.devices
                || homeState.devices.length < 2) {
            return false
        }
        var content = document.createElement("div")
        content.className = "team-home-selection"
        homeState.devices.forEach(function (device, index) {
            var label = document.createElement("label")
            label.className = "team-home-selection__device"
            var radio = document.createElement("input")
            radio.type = "radio"
            radio.name = "team-home-device"
            radio.value = device.cmdProxyInstanceId
            radio.disabled = device.status !== "AVAILABLE"
            label.appendChild(radio)
            label.appendChild(document.createTextNode("设备 " + (index + 1)
                + (radio.disabled ? "（当前不可用）" : "")))
            content.appendChild(label)
        })
        swal({
            title: "选择本机 ACP 设备",
            text: "该选择将用于普通 ACP 和 Fast Team，存在活跃 Team 时不能切换。",
            content: content,
            buttons: {cancel: "取消", confirm: {text: "设为本机", value: "select"}}
        }).then(function (value) {
            if (value !== "select") {
                return
            }
            var selected = content.querySelector("input[name='team-home-device']:checked")
            if (!selected) {
                showToast("请选择一台本机设备", 1200)
                return
            }
            $.ajax({
                url: getPrefix() + "/chat/team/home",
                type: "post",
                dataType: "json",
                data: {
                    chatterId: getChatterId(),
                    token: localStorage.getItem("token"),
                    cmdProxyInstanceId: selected.value
                },
                success: function () {
                    showToast("本机 ACP 设备已设置", 1000)
                    refreshTeams()
                },
                error: function (xhr) {
                    var response = xhr.responseJSON || {}
                    swal("设置失败", response.msg || "本机设备当前不可用", "warning")
                }
            })
        })
        return true
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
            return counts[key] >= 1
        })
    }

    $("#createTeamBtn").on("click", function () {
        if (!teamCapabilityReady) {
            swal("当前无法发起Team",
                "Fast Team连接正在恢复，这不代表普通ACP离线", "info")
            return
        }
        if (selectHomeDevice()) {
            return
        }
        if (candidates.some(function (candidate) {
            return candidate.homeSelectionRequired === true
        })) {
            showToast("本机设备列表正在加载，请稍后重试", 1200)
            refreshTeams()
            return
        }
        if (!hasAvailablePlacement()) {
            swal("暂时无法发起 Team",
                "至少需要 1 个可用的本机 ACP 成员", "info")
            return
        }

        var form = document.createElement("div")
        form.className = "team-create-form"

        var basics = document.createElement("div")
        basics.className = "team-create-form__basics"

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
        basics.appendChild(nameInput)

        var modeFields = document.createElement("div")
        modeFields.className = "team-mode-fields"
        var modeSelect = document.createElement("select")
        modeSelect.className = "browser-default team-mode-select"
        modeSelect.setAttribute("aria-label", "团队模式")
        modeSelect.innerHTML = "<option value='NORMAL'>普通模式</option>"
            + "<option value='CAPTAIN'>队长模式</option>"
        modeFields.appendChild(modeSelect)
        basics.appendChild(modeFields)
        form.appendChild(basics)

        var candidateList = document.createElement("div")
        candidateList.className = "team-candidate-list"
        appendCandidateGroups(candidateList)
        form.appendChild(candidateList)

        var validationMessage = document.createElement("p")
        validationMessage.className = "team-create-form__validation"
        form.appendChild(validationMessage)

        var syncCaptainSelection = function () {
            var captainMode = modeSelect.value === "CAPTAIN"
            form.querySelectorAll(".team-candidate__captain").forEach(function (choice) {
                choice.hidden = !captainMode
            })
            form.querySelectorAll(".team-candidate__captain-radio").forEach(
                function (radio) {
                    var checkbox = form.querySelector(".team-candidate__check[value='"
                        + radio.value + "']")
                    if (!captainMode || !checkbox || !checkbox.checked) {
                        radio.checked = false
                    }
                })
        }

        var validateCreateForm = function () {
            var selected = Array.from(form.querySelectorAll(
                ".team-candidate__check:checked")).map(function (checkbox) {
                return candidates[Number(checkbox.value)]
            })
            if (!nameInput.value.trim()) {
                return {valid: false, message: "请填写队伍名称"}
            }
            if (selected.length < 1) {
                return {valid: false, message: "请选择 1～6 位成员"}
            }
            if (selected.length > 6) {
                return {valid: false, message: "每支 Team 最多 6 位成员"}
            }
            if (modeSelect.value === "CAPTAIN" && selected.length < 2) {
                return {valid: false, message: "队长模式至少需要 2 位成员"}
            }
            if (modeSelect.value === "CAPTAIN"
                    && !form.querySelector(".team-candidate__captain-radio:checked")) {
                return {valid: false, message: "请手工指定一名队长"}
            }
            var hasHome = selected.some(function (candidate) {
                return candidate.sourceType === "HOME"
            })
            var hasRemote = selected.some(function (candidate) {
                return candidate.sourceType === "REMOTE"
            })
            if (hasRemote && !hasHome) {
                return {valid: false, message: "跨设备 Team 必须至少包含一位本机成员"}
            }
            if (hasRemote && selected.some(function (candidate) {
                return candidate.mixedSupported !== true
            })) {
                return {valid: false, message: "所选设备尚未全部支持跨设备组队"}
            }
            return {valid: true, message: hasRemote
                ? "已选择本机和远程成员，将创建跨设备 Team"
                : "将创建本机 Team"}
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
        var resizeCreateDialog = function () {
            var modal = form.closest(".swal-modal")
            if (!modal) {
                return
            }
            var zoom = parseFloat(document.body.style.zoom || 1)
            if (!isFinite(zoom) || zoom <= 0) {
                zoom = 1
            }
            modal.classList.add("team-create-dialog")
            modal.style.maxHeight = Math.max(320,
                window.innerHeight / zoom - 24) + "px"
        }
        resizeCreateDialog()
        window.addEventListener("resize", resizeCreateDialog)
        var confirmButton = document.querySelector(".swal-button--confirm")
        var updateValidation = function () {
            var result = validateCreateForm()
            validationMessage.innerText = result.message
            validationMessage.classList.toggle("team-create-form__validation--valid", result.valid)
            if (confirmButton) {
                confirmButton.disabled = !result.valid
            }
        }
        if (confirmButton) {
            confirmButton.addEventListener("click", function (event) {
                if (!validateCreateForm().valid) {
                    event.preventDefault()
                    event.stopImmediatePropagation()
                }
            }, true)
        }
        nameInput.addEventListener("input", updateValidation)
        modeSelect.addEventListener("change", function () {
            syncCaptainSelection()
            updateValidation()
        })
        form.querySelectorAll(".team-candidate__check").forEach(function (checkbox) {
            checkbox.addEventListener("change", function () {
                var checked = form.querySelectorAll(".team-candidate__check:checked")
                if (checked.length > 6) {
                    checkbox.checked = false
                    showToast("每支 Team 最多 6 位成员", 1200)
                }
                syncCaptainSelection()
                updateValidation()
            })
        })
        form.querySelectorAll(".team-candidate__captain-radio").forEach(function (radio) {
            radio.addEventListener("change", function () {
                if (!radio.checked) {
                    return
                }
                var checkbox = form.querySelector(".team-candidate__check[value='"
                    + radio.value + "']")
                if (checkbox && !checkbox.checked) {
                    if (form.querySelectorAll(".team-candidate__check:checked").length >= 6) {
                        radio.checked = false
                        showToast("每支 Team 最多 6 位成员", 1200)
                        updateValidation()
                        return
                    }
                    checkbox.checked = true
                }
                updateValidation()
            })
        })
        syncCaptainSelection()
        updateValidation()
        createDialog.then(function (value) {
            window.removeEventListener("resize", resizeCreateDialog)
            if (value !== "create") {
                return
            }
            var name = nameInput.value.trim()
            var selected = Array.from(form.querySelectorAll(".team-candidate__check:checked"))
                .map(function (checkbox) {
                    var index = Number(checkbox.value)
                    var remark = form.querySelector(".team-candidate__remark[data-candidate-index='"
                        + index + "']")
                    return {
                        candidateIndex: index,
                        candidate: candidates[index],
                        teamRemark: remark ? remark.value : ""
                    }
                })
            swal.close()
            var captain = form.querySelector(".team-candidate__captain-radio:checked")
            createTeam(name, selected, modeSelect.value, captain ? captain.value : "")
        })
    })

    var createTeam = function (name, selected, mode, captainCandidateIndex) {
        var requestId = createUuid()
        var captainTeamMemberId = null
        var members = selected.map(function (selection) {
            var teamMemberId = createUuid()
            if (mode === "CAPTAIN"
                    && String(selection.candidateIndex) === captainCandidateIndex) {
                captainTeamMemberId = teamMemberId
            }
            return {
                teamMemberId: teamMemberId,
                cmdProxyInstanceId: selection.candidate.cmdProxyInstanceId,
                transportGroup: selection.candidate.transportGroup,
                sourceRobotId: selection.candidate.sourceRobotId,
                sourceGroupId: selection.candidate.sourceGroupId,
                remark: selection.teamRemark
            }
        })
        var payload = {
            chatterId: getChatterId(),
            token: localStorage.getItem("token"),
            requestId: requestId,
            name: name,
            mode: mode,
            members: members
        }
        if (mode === "CAPTAIN") {
            payload.captainTeamMemberId = captainTeamMemberId
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
            success: function (result) {
                var status = result.data && (result.data.status || result.data.state)
                if (status === "DELETED") {
                    delete deleteOperations[team.teamId]
                    if (getActiveTeamId() === team.teamId) {
                        leaveTeamMode()
                    }
                    showToast("队伍已删除", 1000)
                } else if (status === "PENDING_CLEANUP") {
                    showToast("部分远程成员尚未清理，可稍后重试", 1800)
                } else {
                    showToast("删除请求已提交", 1000)
                }
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
