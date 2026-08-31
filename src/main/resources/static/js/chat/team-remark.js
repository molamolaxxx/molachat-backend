(function (root) {
    var candidateKey = function (candidate) {
        return [candidate.cmdProxyInstanceId, candidate.transportGroup,
            candidate.sourceRobotId, candidate.sourceGroupId].join("\n")
    }

    var createStore = function () {
        var values = Object.create(null)
        return {
            get: function (candidate) {
                return Object.prototype.hasOwnProperty.call(values, candidateKey(candidate))
                    ? values[candidateKey(candidate)] : ""
            },
            set: function (candidate, value) {
                values[candidateKey(candidate)] = value == null ? "" : String(value)
            }
        }
    }

    var buildMember = function (candidate, teamRemark) {
        return {
            cmdProxyInstanceId: candidate.cmdProxyInstanceId,
            transportGroup: candidate.transportGroup,
            sourceRobotId: candidate.sourceRobotId,
            sourceGroupId: candidate.sourceGroupId,
            remark: teamRemark == null ? "" : String(teamRemark)
        }
    }

    root.FastTeamRemarkState = {
        candidateKey: candidateKey,
        createStore: createStore,
        buildMember: buildMember
    }
})(typeof window === "undefined" ? globalThis : window)
