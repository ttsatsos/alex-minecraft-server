import 'dotenv/config'
import mineflayer from 'mineflayer'
import pathfinderPackage from 'mineflayer-pathfinder'
import minecraftData from 'minecraft-data'

const { pathfinder, Movements, goals } = pathfinderPackage
const {
  GoalNear,
  GoalFollow
} = goals

const config = {
  host: process.env.MINECRAFT_HOST || '127.0.0.1',
  port: parseInt(process.env.MINECRAFT_PORT || '25565', 10),
  version: process.env.MINECRAFT_VERSION || false,
  username: process.env.BOT_USERNAME?.trim() || '',
  auth: process.env.BOT_AUTH || 'microsoft',
  displayName: process.env.BOT_DISPLAY_NAME || 'Companion',
  owner: process.env.BOT_OWNER || '',
  profilesFolder: process.env.BOT_PROFILES_DIR || './profiles',
  chatPrefix: process.env.BOT_CHAT_PREFIX || '!bot',
  followRange: parseInt(process.env.BOT_FOLLOW_RANGE || '3', 10),
  llmEnabled: (process.env.LLM_ENABLED || 'false').toLowerCase() === 'true',
  llmBaseUrl: (process.env.LLM_BASE_URL || 'https://api.openai.com/v1').replace(/\/+$/, ''),
  llmModel: process.env.LLM_MODEL || 'gpt-4.1-mini',
  llmApiKey: process.env.LLM_API_KEY || '',
  llmMaxOutputTokens: parseInt(process.env.LLM_MAX_OUTPUT_TOKENS || '160', 10),
  llmSystemPrompt:
    process.env.LLM_SYSTEM_PROMPT ||
    'You are a helpful Minecraft companion. Speak briefly, casually, and like an in-world player.'
}

if (!config.username) {
  throw new Error('BOT_USERNAME is required in ai-bot/.env. Use the Minecraft account email or unique account identifier for the bot.')
}

const bot = mineflayer.createBot({
  host: config.host,
  port: config.port,
  username: config.username,
  auth: config.auth,
  version: config.version,
  profilesFolder: config.profilesFolder,
  onMsaCode: (data) => {
    console.log('\nMicrosoft login required for the bot account.')
    console.log(`Open ${data.verification_uri} and enter code: ${data.user_code}\n`)
  }
})

bot.loadPlugin(pathfinder)

const chatHistory = new Map()
let defaultMovements
let followTarget = null

function addHistory(player, role, content) {
  const existing = chatHistory.get(player) || []
  existing.push({ role, content })
  chatHistory.set(player, existing.slice(-8))
}

function splitChat(message) {
  const clean = String(message).replace(/\s+/g, ' ').trim()
  if (!clean) return []

  const limit = 240
  const chunks = []
  let remaining = clean

  while (remaining.length > limit) {
    let index = remaining.lastIndexOf(' ', limit)
    if (index < 0) index = limit
    chunks.push(remaining.slice(0, index).trim())
    remaining = remaining.slice(index).trim()
  }

  if (remaining) chunks.push(remaining)
  return chunks
}

async function say(message) {
  for (const chunk of splitChat(message)) {
    bot.chat(chunk)
    await new Promise((resolve) => setTimeout(resolve, 350))
  }
}

function findPlayerEntity(playerName) {
  const player = bot.players[playerName]
  return player?.entity || null
}

async function handleCommand(username, command) {
  const lower = command.toLowerCase()

  if (lower === 'stop') {
    followTarget = null
    bot.pathfinder.setGoal(null)
    await say(`Stopping, ${username}.`)
    return true
  }

  if (lower === 'where are you' || lower === 'where are you?') {
    const pos = bot.entity.position
    await say(
      `I'm at ${Math.floor(pos.x)}, ${Math.floor(pos.y)}, ${Math.floor(pos.z)} in ${bot.game.dimension}.`
    )
    return true
  }

  if (lower === 'come' || lower === 'come here') {
    const entity = findPlayerEntity(username)
    if (!entity) {
      await say(`I can't see you right now, ${username}.`)
      return true
    }

    await bot.pathfinder.goto(
      new GoalNear(
        Math.floor(entity.position.x),
        Math.floor(entity.position.y),
        Math.floor(entity.position.z),
        1
      )
    )
    await say(`On my way, ${username}.`)
    return true
  }

  if (lower === 'follow me' || lower === 'follow') {
    const entity = findPlayerEntity(username)
    if (!entity) {
      await say(`I can't see you right now, ${username}.`)
      return true
    }

    followTarget = username
    bot.pathfinder.setGoal(new GoalFollow(entity, config.followRange), true)
    await say(`Following you, ${username}.`)
    return true
  }

  return false
}

function shouldHandleChat(username, message) {
  if (username === bot.username) return false

  const lower = message.toLowerCase().trim()
  const botName = config.displayName.toLowerCase()

  return (
    lower.startsWith(config.chatPrefix.toLowerCase()) ||
    lower.startsWith(`${botName},`) ||
    lower.startsWith(`${botName}:`) ||
    lower.startsWith(`@${botName}`) ||
    lower.includes(` ${botName} `) ||
    lower === botName
  )
}

function extractPrompt(message) {
  const lowerPrefix = config.chatPrefix.toLowerCase()
  const lowered = message.toLowerCase()
  const botName = config.displayName.toLowerCase()

  if (lowered.startsWith(lowerPrefix)) {
    return message.slice(config.chatPrefix.length).trim()
  }

  if (lowered.startsWith(`${botName},`)) {
    return message.slice(config.displayName.length + 1).trim()
  }

  if (lowered.startsWith(`${botName}:`)) {
    return message.slice(config.displayName.length + 1).trim()
  }

  if (lowered.startsWith(`@${botName}`)) {
    return message.slice(config.displayName.length + 1).trim()
  }

  return message.trim()
}

async function callLlm(username, prompt) {
  if (!config.llmEnabled || !config.llmApiKey) {
    return `I heard you, ${username}. LLM replies aren't enabled yet, but I can still follow, come here, stop, and report my location.`
  }

  const messages = [
    { role: 'system', content: config.llmSystemPrompt },
    ...(chatHistory.get(username) || []),
    {
      role: 'user',
      content: `${username} says in Minecraft chat: ${prompt}`
    }
  ]

  const response = await fetch(`${config.llmBaseUrl}/chat/completions`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${config.llmApiKey}`
    },
    body: JSON.stringify({
      model: config.llmModel,
      messages,
      temperature: 0.9,
      max_completion_tokens: config.llmMaxOutputTokens
    })
  })

  if (!response.ok) {
    const errorText = await response.text()
    throw new Error(`LLM request failed (${response.status}): ${errorText}`)
  }

  const data = await response.json()
  const text = data.choices?.[0]?.message?.content?.trim()

  if (!text) {
    throw new Error('LLM response did not include message content.')
  }

  return text
}

bot.once('spawn', async () => {
  const mcData = minecraftData(bot.version)
  defaultMovements = new Movements(bot, mcData)
  bot.pathfinder.setMovements(defaultMovements)

  console.log(`${config.displayName} spawned on ${config.host}:${config.port} as ${bot.username}`)
  await say(`Hey, I'm ${config.displayName}. Use "${config.chatPrefix} follow me" or mention my name to talk.`)
})

bot.on('chat', async (username, message) => {
  if (!shouldHandleChat(username, message)) return
  if (config.owner && username !== config.owner && message.toLowerCase().startsWith(config.chatPrefix.toLowerCase())) {
    return
  }

  const prompt = extractPrompt(message)
  if (!prompt) return

  addHistory(username, 'user', prompt)

  try {
    if (await handleCommand(username, prompt)) {
      addHistory(username, 'assistant', `Handled command: ${prompt}`)
      return
    }

    const reply = await callLlm(username, prompt)
    addHistory(username, 'assistant', reply)
    await say(reply)
  } catch (error) {
    console.error('Chat handling failed:', error)
    await say(`I hit a problem with that request, ${username}. Check the ai-bot console.`)
  }
})

bot.on('playerLeft', (player) => {
  if (followTarget && player.username === followTarget) {
    followTarget = null
    bot.pathfinder.setGoal(null)
  }
})

bot.on('physicTick', () => {
  if (!followTarget) return
  const entity = findPlayerEntity(followTarget)
  if (!entity) {
    bot.pathfinder.setGoal(null)
    followTarget = null
    return
  }

  const currentGoal = bot.pathfinder.goal
  if (!currentGoal) {
    bot.pathfinder.setGoal(new GoalFollow(entity, config.followRange), true)
  }
})

bot.on('whisper', async (username, message) => {
  if (username === bot.username) return
  addHistory(username, 'user', message)

  try {
    const reply = await callLlm(username, message)
    addHistory(username, 'assistant', reply)
    await say(`/tell ${username} ${reply}`)
  } catch (error) {
    console.error('Whisper handling failed:', error)
  }
})

bot.on('kicked', (reason) => {
  console.error('Bot was kicked:', reason)
})

bot.on('error', (error) => {
  console.error('Bot error:', error)
})

bot.on('end', (reason) => {
  console.log('Bot disconnected:', reason)
})
