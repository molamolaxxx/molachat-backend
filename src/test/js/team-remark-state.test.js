const assert = require("assert")

require("../../main/resources/static/js/chat/team-remark.js")

const first = {
    cmdProxyInstanceId: "instance-1",
    transportGroup: "team-acp-instance-1",
    sourceRobotId: "source-1",
    sourceGroupId: "group-1",
    remark: "hidden ability fallback"
}
const second = {
    cmdProxyInstanceId: "instance-2",
    transportGroup: "team-acp-instance-2",
    sourceRobotId: "source-2",
    sourceGroupId: "group-2",
    remark: "another hidden fallback"
}
const store = FastTeamRemarkState.createStore()

assert.strictEqual(store.get(first), "")
assert.strictEqual(store.get(second), "")

store.set(first, "负责实现")
store.set(second, "负责评审")
assert.deepStrictEqual([second, first].map(candidate => store.get(candidate)),
    ["负责评审", "负责实现"])

store.set(first, "")
const firstMember = FastTeamRemarkState.buildMember(first, store.get(first))
const secondMember = FastTeamRemarkState.buildMember(second, store.get(second))
assert.strictEqual(firstMember.remark, "")
assert.ok(Object.prototype.hasOwnProperty.call(firstMember, "remark"))
assert.strictEqual(secondMember.remark, "负责评审")

console.log("team remark state tests passed")
