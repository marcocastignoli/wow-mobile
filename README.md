# WoW Mobile

Play **World of Warcraft 3.3.5a (Wrath of the Lich King)** on Android with a
touch UI designed for [ConsolePortLK](https://github.com/leoaviana/ConsolePortLK) —
no manual setup, no fiddling with Wine, bindings or addons.

WoW Mobile is a single-purpose fork of [Winlator](https://github.com/brunodev85/winlator):
it keeps the Wine + Box64 engine and wraps it in a three-screen app.

<img width="1280" height="576" alt="2026-08-13 16 01 33" src="https://github.com/user-attachments/assets/26b5064c-814d-469b-a28b-becea41696cf" />

60fps on Pixel 8

## What it does

1. **Play** — pick the folder that contains `Wow.exe` and press PLAY.
   On first launch the app automatically:
   - installs the bundled ConsolePortLK addons (if not already present)
   - makes ConsolePort keyboard bindings account-wide
   - detects your client locale (`enUS`, `enGB`, …) and sets the realmlist
     (default: `logon.therawow.com`, changeable in WoW Settings)
   - applies a tuned `Config.wtf` (windowed-maximized, 960x432, performance graphics)
   - creates a tuned Winlator container with the game mapped as drive `F:`
   - enables a touch-controls profile whose buttons emit the keystrokes
     ConsolePortLK expects (movement pad, UI-navigation pad, face buttons,
     modifiers, camera toggle, loot, jump, target)
2. **WoW Settings** — switch realm server, resolution and view distance
   directly from the app (edits `Config.wtf` / `realmlist.wtf` while the game is off).
3. **Container Settings** — the full Winlator container editor for the single
   game container, prefilled with sane defaults.

After the very first login the app asks you to log out once — that's when WoW
creates your account folder, and WoW Mobile finishes calibrating ConsolePort
for it automatically. From then on everything just works.

## Install

Grab the APK from [Releases](../../releases) and sideload it.

> **Note:** WoW Mobile uses its own application id (`it.wowmobile`), so it
> installs alongside the original Winlator without touching it. All asset
> archives (rootfs, drivers, box64) are re-patched at build time to match.

You need to provide your own World of Warcraft 3.3.5a (build 12340) client
folder on the phone's storage. No game files are distributed with this app.

## Controls layout

| Touch button | Key sent | ConsolePort action |
|---|---|---|
| Left pad | W/A/S/D | Movement |
| Small upper pad | I/J/K/L | UI cursor (CP_L_*) |
| Y / B / A / X | Y / B / N / H | CP_R_UP / RIGHT / DOWN / LEFT |
| L1 / L2 | Shift / Ctrl | Modifiers (more ability pages) |
| R1 / R2 | Q / E | CP_T1 / CP_T2 |
| BACK / MENU | G / V | CP_X_LEFT / CP_X_RIGHT |
| LOOK (toggle) | Right mouse | Camera |
| LCLICK / RCLICK | Left / right mouse | Cursor clicks |
| M | M | Map |
| JUMP / TGT | Space / Tab | Jump / target enemy |

## Building

```
./gradlew assembleRelease
```

Requires JDK 17, Android SDK (platform 34), NDK 24.0.8215888 and CMake 3.22.1.
Release signing reads `~/.wow-mobile-secrets/keystore.properties`
(`storeFile`/`storePassword`/`keyAlias`/`keyPassword`); without it the release
build is unsigned.

## Credits & license

- [Winlator](https://github.com/brunodev85/winlator) by brunodev85 — the entire
  Windows-on-Android engine this app is built on. LGPL-2.1, same as this fork.
- [ConsolePortLK](https://github.com/leoaviana/ConsolePortLK) by leoaviana —
  controller UI for WoW 3.3.5a (bundled unmodified).
- World of Warcraft is a trademark of Blizzard Entertainment. This project is
  not affiliated with or endorsed by Blizzard. Bring your own client files.
