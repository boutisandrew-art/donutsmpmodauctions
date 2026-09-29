# Donut Auction Manager (Fabric, Minecraft 1.21.11, works in Lunar Client)

Client-side mod with a clean auction menu, scheduled starts, presets, a countdown HUD
and chat warnings. It sends your server's "sell" command for you at the time you choose.

## Build
Easiest way (gets you the Gradle wrapper):
1. Go to https://fabricmc.net/develop/template/ , pick Minecraft 1.21.11, mappings **Yarn**, download.
2. Copy this folder's `src/` over the template's `src/` (delete the template's example mod code)
   and copy `build.gradle` + `gradle.properties` over the template's if its versions differ from mine.
3. Run `./gradlew build` (Windows: `gradlew.bat build`). Jar ends up in `build/libs/`
   (use the one WITHOUT `-sources`).

If you already have Gradle installed you can run `gradle wrapper` in this folder instead.
Needs JDK 21.

## Install in Lunar Client
Lunar launcher -> version 1.21.11 -> enable the **Fabric** add-on -> Mods -> drag the jar in.

## Use
- Press **K** (rebind in Controls -> "Donut Auction") or run `/aucmenu`.
- Pick a hotbar slot, enter a price (`5000`, `25k`, `1.5m`), a duration (`15m`, `1h30m`, `90s`)
  and optionally a start time (`now`, `10m`, or a clock time like `14:30`).
- **Start Auction**. The HUD shows the countdown; chat warns at 60s and 10s left.
- Presets: type a name, **Save**. Use `<` `>` to load, **Delete** to remove.

Commands: `/aucmenu`, `/auc start` (last menu settings), `/auc cancel`, `/auc status`,
`/auc preset <name>`.

## IMPORTANT: set the sell command
The mod sends `config/donutauction.json` -> `sellCommand`, default `ah sell {price}`
(no leading slash). Check DonutSMP's real syntax and edit it. `{buynow}` is also available.
Other options in that file: HUD position, warning times, chat-announce text,
and `minCommandDelayMs` (minimum gap between anything sent to the server, floor 1000).

## Notes
- Only items in your hotbar can be auctioned (the mod selects the slot, then sends the command).
- Cancel stops the timer only; a listing already placed on the server must be removed in-game.
- "Chat announce" posts public chat messages; it's off by default. Check DonutSMP's rules
  on advertising and automation before using it.
