const test = require('node:test')
const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const teamSource = fs.readFileSync(path.resolve(
    __dirname, '../../main/resources/static/js/chat/team.js'
), 'utf8')
const indexSource = fs.readFileSync(path.resolve(
    __dirname, '../../main/resources/templates/index.html'
), 'utf8')
const stylesSource = fs.readFileSync(path.resolve(
    __dirname, '../../main/resources/static/css/styles.css'
), 'utf8')

test('active Team mode exposes a sticky one-click main-session shortcut', () => {
    assert.match(indexSource,
        /<div class="friend-list">[\s\S]*id="leave-team-quick"[\s\S]*aria-label="切回主会话"/)
    assert.match(indexSource,
        /id="leave-team-quick"[\s\S]*<i class="material-icons" aria-hidden="true">home<\/i>/)
    assert.match(stylesSource,
        /\.team-home-shortcut\s*{[^}]*position:\s*sticky;[^}]*top:\s*0;/s)
    assert.match(stylesSource,
        /\.team-home-shortcut\[hidden\]\s*{[^}]*display:\s*none;/s)
    assert.match(stylesSource,
        /\.sidebar-outside-mobile \.team-home-shortcut span\s*{[^}]*display:\s*none;/s)
    assert.match(teamSource,
        /\$\("#leave-team-quick"\)\.prop\("hidden", !activeTeamId\)/)
})

test('quick shortcut reuses Team exit while Teams button still opens management', () => {
    assert.match(teamSource,
        /\$\("#leave-team-quick"\)\.on\("click", function \(\) \{\s*leaveTeamMode\(\)\s*\}\)/)
    assert.match(teamSource, /\$\("#teams"\)\.on\("click", openTeamModal\)/)
    assert.match(teamSource,
        /var leaveTeamMode = function \(\) \{[\s\S]*localStorage\.removeItem\(activeTeamStorageKey\(\)\)[\s\S]*refreshChatterForTeamMode\(\)[\s\S]*updateModeControls\(\)/)
})

test('Team shortcut assets have refreshed cache versions', () => {
    assert.match(indexSource, /css\/styles\.css\?v=202609122139/)
    assert.match(indexSource, /js\/chat\/team\.js\?v=202609061950/)
})
