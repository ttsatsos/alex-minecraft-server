# Local AI NPC Plugin

This plugin adds an AI-backed companion trait for `Citizens` NPCs and talks to a local `Ollama` server.

## Alex's computer-player commands

- `/bots create <name>` creates a friendly computer player at your location.
- `/bots enemy <name>` creates a roaming enemy at your location.
- `/bots list` shows every managed computer player and its number.
- `/bots follow <bot>`, `/bots guard <bot>`, and `/bots roam <bot>` change its job.
- `/bots attack <bot> <player-or-bot>` starts a fight.
- `/bots scene battle <bot1> <bot2>` starts a repeatable two-bot battle.
- `/bots freeze <bot|all>` holds characters in place between takes.
- `/bots reset <bot|all>` returns characters home and restores their health.
- `/bots persona <bot> <description>` sets a character personality for chat.
- `/bots status <bot>` and `/bots remove <bot>` inspect or remove a character.

Use either the bot number shown by `/bots list` or a one-word bot name. These commands require operator permission.

Computer players participate in StatSteal. They can gain or lose one stat per player kill, keep those stats after respawning, and never receive the real-player 30-day ban.

## Advanced commands

- Create a companion NPC with `/ainpc create <name>`
- Attach the AI trait to an existing Citizens NPC with `/ainpc bind <npc-id>`
- Set the owner who can control follow/stop behavior with `/ainpc owner <npc-id> <player>`
- Override the model with `/ainpc model <npc-id> <model>`
- Set a persona prompt with `/ainpc prompt <npc-id> <prompt...>`
- Toggle combat with `/ainpc combat <npc-id> <on|off>`
- Check status with `/ainpc status <npc-id>`

## In-game usage

Address the NPC in chat:

- `Companion, follow me`
- `Companion, guard me`
- `Companion, attack monsters`
- `Companion, stand down`
- `Companion, come here`
- `Companion, stop`
- `Companion, where are you?`
- `!Companion tell me about this place`

## Local model

The plugin expects Ollama at `http://127.0.0.1:11434` and defaults to `qwen2.5:3b`.

## Combat notes

Combat uses the `Sentinel` trait under the hood. The local model handles personality and high-level commands, while Sentinel handles chasing, targeting, and fighting nearby monsters or enemies threatening the NPC's owner.
