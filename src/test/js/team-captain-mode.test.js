const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const teamSource = fs.readFileSync(path.resolve(
    __dirname, '../../main/resources/static/js/chat/team.js'
), 'utf8')
const stylesSource = fs.readFileSync(path.resolve(
    __dirname, '../../main/resources/static/css/styles.css'
), 'utf8')
const indexSource = fs.readFileSync(path.resolve(
    __dirname, '../../main/resources/templates/index.html'
), 'utf8')

test('Team creation offers explicit normal and captain modes', () => {
    assert.match(teamSource, /<option value='NORMAL'>普通模式<\/option>/)
    assert.match(teamSource, /<option value='CAPTAIN'>队长模式<\/option>/)
    assert.match(teamSource, /team-create-form__basics/)
    assert.match(teamSource, /team-candidate__captain-radio/)
    assert.match(teamSource, /队长模式至少需要 2 位成员/)
    assert.match(teamSource, /请手工指定一名队长/)
    assert.match(stylesSource, /\.team-create-form\s*{[^}]*max-height:\s*100%;[^}]*overflow-y:\s*auto;/s)
    assert.match(stylesSource, /\.swal-modal\.team-create-dialog\s*{[^}]*display:\s*inline-flex;[^}]*overflow:\s*hidden;/s)
    assert.match(teamSource, /window\.innerHeight \/ zoom - 24/)
    assert.match(teamSource, /window\.addEventListener\("resize", resizeCreateDialog\)/)
    assert.match(teamSource, /window\.removeEventListener\("resize", resizeCreateDialog\)/)
    assert.match(stylesSource, /\.team-create-form__basics\s*{[^}]*grid-template-columns:/s)
    assert.match(stylesSource,
        /@media screen and \(max-width: 480px\)[\s\S]*?\.team-create-form__basics\s*{[^}]*grid-template-columns:\s*minmax\(0, 1fr\) 10\.5rem;/s)
    assert.match(stylesSource,
        /\.team-candidate__heading\s*{[^}]*min-width:\s*0;/s)
    assert.match(stylesSource,
        /@media screen and \(max-width: 480px\)[\s\S]*?\.team-candidate__heading\s*{[^}]*flex-wrap:\s*wrap;/s)
})

test('captain is selected from checked members and submitted by stable member id', () => {
    assert.match(teamSource,
        /team-candidate__captain-radio:checked/)
    assert.match(teamSource, /checkbox\.checked = true/)
    assert.match(teamSource, /teamMemberId:\s*teamMemberId/)
    assert.match(teamSource, /payload\.captainTeamMemberId = captainTeamMemberId/)
    assert.match(teamSource, /mode:\s*mode/)
})

test('Team list identifies captain mode without exposing routing identifiers', () => {
    assert.match(teamSource, /teamMode === "CAPTAIN"/)
    assert.match(teamSource, /"队长：" \+ \(captain \? captain\.displayName : "待确认"\)/)
    assert.doesNotMatch(teamSource, /队长：.*captainTeamMemberId/)
    assert.match(teamSource,
        /\$nameLine\.append\(\$\("<span class='team-row__mode'><\/span>"\)\.text\("队长模式"\)\)/)
})

test('captain product conversation keeps all members available', () => {
    assert.doesNotMatch(teamSource, /只能从队长会话入口/)
    assert.doesNotMatch(teamSource, /只投影队长/)
})

test('captain-mode assets have refreshed cache versions', () => {
    assert.match(indexSource, /css\/styles\.css\?v=202609122139/)
    assert.match(indexSource, /js\/chat\/team\.js\?v=202609061950/)
})
