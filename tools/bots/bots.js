'use strict'

/**
 * Headless test clients for the Hardcore Games dev server.
 *
 * These are REAL players from the server's point of view — they have bodies, so unlike
 * `/hgfake` counters they can be tracked with a compass, hit, and killed. Use them to test
 * combat, kits and the compass; use /hgfake when you only need the player count to move.
 *
 * The server runs Minecraft 26.2 but mineflayer only speaks up to 26.1, so the dev server
 * needs ViaVersion installed (it is, in run/plugins) to accept these connections.
 *
 * Usage:
 *   node bots.js                     # 3 bots named Bot1..Bot3, kit "fighter"
 *   node bots.js --count 5           # 5 bots
 *   node bots.js --kit knight        # pick a different kit
 *   node bots.js --port 25566        # different server
 *   node bots.js --no-wander         # stand still (easier to line up compass tests)
 *
 * In the bot console:
 *   say <text>     every bot chats
 *   cmd <command>  every bot runs a command, e.g.  cmd /kit fighter
 *   list           show connected bots and their positions
 *   quit           disconnect everyone and exit
 */

const mineflayer = require('mineflayer')

function parseArgs (argv) {
  const opts = { count: 3, host: 'localhost', port: 25565, kit: 'fighter', wander: true }
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]
    if (arg === '--count') opts.count = parseInt(argv[++i], 10)
    else if (arg === '--host') opts.host = argv[++i]
    else if (arg === '--port') opts.port = parseInt(argv[++i], 10)
    else if (arg === '--kit') opts.kit = argv[++i]
    else if (arg === '--no-wander') opts.wander = false
    else if (arg === '--help') { console.log(require('fs').readFileSync(__filename, 'utf8').split('*/')[0]); process.exit(0) }
  }
  return opts
}

const opts = parseArgs(process.argv.slice(2))
const bots = []

// mineflayer's newest supported protocol. ViaVersion translates it up to the server's 26.2.
const PROTOCOL_VERSION = '26.1'

function spawnBot (name) {
  const bot = mineflayer.createBot({
    host: opts.host,
    port: opts.port,
    username: name,
    auth: 'offline',
    version: PROTOCOL_VERSION
  })
  bot.hgName = name

  bot.once('spawn', () => {
    console.log(`[${name}] spawned`)
    // Give the server a moment to finish its own join handling before selecting a kit.
    setTimeout(() => bot.chat(`/kit ${opts.kit}`), 1500)
    if (opts.wander) startWandering(bot)
  })

  // The plugin kicks players on death, which is the expected end state for a bot.
  bot.on('kicked', reason => console.log(`[${name}] kicked: ${JSON.stringify(reason)}`))
  bot.on('end', () => console.log(`[${name}] disconnected`))
  bot.on('error', err => console.log(`[${name}] error: ${err.message}`))

  bot.on('message', message => {
    const text = message.toString()
    if (text.trim()) console.log(`[chat] ${text}`)
  })

  bots.push(bot)
  return bot
}

/** Slow random walk, so compass tracking has moving targets to lock on to. */
function startWandering (bot) {
  setInterval(() => {
    if (!bot.entity) return
    bot.setControlState('forward', true)
    bot.look(Math.random() * Math.PI * 2, 0, true)
    setTimeout(() => bot.setControlState('forward', false), 1200)
  }, 4000)
}

for (let i = 1; i <= opts.count; i++) {
  // Stagger connections; a simultaneous burst can trip the server's join throttle.
  setTimeout(() => spawnBot(`Bot${i}`), i * 900)
}

console.log(`connecting ${opts.count} bots to ${opts.host}:${opts.port} (kit: ${opts.kit})`)
console.log('commands: say <text> | cmd <command> | list | quit')

process.stdin.setEncoding('utf8')
process.stdin.on('data', chunk => {
  const line = chunk.trim()
  if (!line) return

  if (line === 'quit') {
    bots.forEach(b => b.quit())
    process.exit(0)
  } else if (line === 'list') {
    bots.forEach(b => {
      const pos = b.entity ? b.entity.position : null
      console.log(`  ${b.hgName}: ${pos ? `${pos.x.toFixed(0)}, ${pos.y.toFixed(0)}, ${pos.z.toFixed(0)}` : 'not spawned'}`)
    })
  } else if (line.startsWith('say ')) {
    bots.forEach(b => b.chat(line.slice(4)))
  } else if (line.startsWith('cmd ')) {
    bots.forEach(b => b.chat(line.slice(4)))
  } else {
    console.log('commands: say <text> | cmd <command> | list | quit')
  }
})
