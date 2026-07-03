# Minecraft AI Bot

This folder contains a `Mineflayer`-based companion bot for the local Paper server in this project.

## What it does

- Joins the server as a real Minecraft player account
- Responds in chat when addressed
- Supports a few built-in movement commands:
  - `!bot follow me`
  - `!bot come here`
  - `!bot stop`
  - `!bot where are you`
- Can forward chat to an OpenAI-compatible LLM endpoint for more natural replies

## Important limitation

Your server currently has `online-mode=true`, so this bot must use a real Java/Microsoft account.
It cannot join this server with an offline username unless the server auth mode changes.

## Setup

1. Copy `.env.example` to `.env`.
2. Fill in `BOT_USERNAME`.
3. Leave `BOT_AUTH=microsoft` for this server.
4. If you want LLM chat, set:
   - `LLM_ENABLED=true`
   - `LLM_API_KEY=...`
   - optionally `LLM_MODEL` and `LLM_BASE_URL`

The bot stores its Microsoft auth token cache in `./profiles`.

## Start

```bash
/Users/alextsatsos1/Documents/Codex/2026-04-26/help-us-set-up-a-minecraft/scripts/start-ai-bot.sh
```

On first login with Microsoft auth, the console will print a verification URL and device code.
Open the URL in a browser, enter the code, and sign in with the bot's Minecraft account.

## Notes

- The bot is separate from the Paper server. It connects like a normal player.
- By default only `BOT_OWNER` can issue `!bot ...` commands.
- Natural language replies are intentionally short so they fit well in Minecraft chat.
