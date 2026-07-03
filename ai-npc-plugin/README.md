# Local AI NPC Plugin

This plugin adds an AI-backed companion trait for `Citizens` NPCs and talks to a local `Ollama` server.

## What it supports

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
