# Ignition Tag Hover Module

[![Build](https://github.com/bearinmindcat/ignition-tag-hover-module/actions/workflows/build.yml/badge.svg)](https://github.com/bearinmindcat/ignition-tag-hover-module/actions/workflows/build.yml)
[![Ignition](https://img.shields.io/badge/Ignition-8.1.35+-rebeccapurple.svg)](https://inductiveautomation.com/)
[![Vision](https://img.shields.io/badge/Vision-client%20%2B%20Designer-orange.svg)](#requirements)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Hold **Ctrl** and hover over any Vision component, in a client or the Designer, to see which tags it's bound to.

<p align="center">
  <img src="docs/demo.gif" alt="A Vision client: with Ctrl held, tooltips list the tags behind a level label, a tank, an expression label and an indirect motor speed; switching the motor to Motor2 makes the indirect binding follow; Ctrl+Comma opens the settings, Press a key sets F2, tap mode is chosen, and a tap of F2 keeps the tooltips on until Esc" width="100%">
  <br>
  <sub>A Vision client on Ignition 8.1.35: hold Ctrl, switch the motor and watch the indirect binding follow, then set F2 and tap mode in the settings. About 0:50, sped up; the <a href="docs/demo.mp4">full video</a> (MP4, 1:11) plays at real speed and full resolution. It works the same way in the Designer.</sub>
</p>

Forum threads: [Vision: Tag Info popup on tag(s) under mouse](https://forum.inductiveautomation.com/t/vision-tag-info-popup-on-tag-s-under-mouse/25937) · [Determine origin of tips in the Ignition SCADA HMI](https://forum.inductiveautomation.com/t/determine-origin-of-tips-in-the-ignition-scada-hmi/111100)

## Features

<table>
<tr>
<td width="50%"><img src="docs/indirect.png" alt="Ctrl held over a motor speed label: value comes from [default]TagHoverTest/Motor2/Speed, an indirect binding with the pattern [default]TagHoverTest/{1}/Speed"><br><sub>Indirect bindings show the tag they resolve to right now, and the pattern behind it.</sub></td>
<td width="50%"><img src="docs/expression.png" alt="Ctrl held over a label bound to an expression: its text reads Tank1Mode and Tank1HighAlarm"><br><sub>Expressions list every tag they read, whether written as <code>{...}</code> or <code>tag("...")</code>.</sub></td>
</tr>
<tr>
<td width="50%"><img src="docs/settings.png" alt="The Tag Hover Settings window: the key set to F2 with Press a key, and the choice between holding and tapping it"><br><sub>Ctrl+Comma opens the settings. Pick a key from the list or press it, and choose to hold or tap it. Saved per Windows user.</sub></td>
<td width="50%"><img src="docs/tap-mode.png" alt="Tap mode: the tooltip over a tank stays on with no key held, with the hint Tap F2 or press Esc to stop"><br><sub>Tap mode: one tap and the tooltips follow the mouse with no key held, until another tap or Esc.</sub></td>
</tr>
</table>

Also: direct and UDT tag bindings, property bindings followed back to their tag, template parameters, bad quality flags, and `↔` for bidirectional bindings. It works on every window with nothing to set up, and it's free, with no license or trial timer.

## Requirements

- Ignition 8.1.35 or newer 8.1 (8.3 isn't supported yet)
- Vision clients and the Designer, in design and preview mode (not Perspective)
- A mouse and keyboard, since touchscreens have no hover

## Installation

1. Download `Tag-Hover-Tooltip.unsigned.modl` from [Releases](https://github.com/bearinmindcat/ignition-tag-hover-module/releases).
2. Allow unsigned modules: add `wrapper.java.additional.N=-Dignition.allowunsignedmodules=true` (with the next free `N`) to `ignition.conf`, then restart the Gateway.
3. In the Gateway, go to **Config → Modules → Install or Upgrade a Module** and choose the file.
4. Reopen Vision clients and the Designer.

## Usage

Hold **Ctrl** and hover over a component. The tooltip goes away when you let go, click, scroll, or press a shortcut such as Ctrl+C.

Press **Ctrl+Comma** to open the settings, or in the Designer use **Tools → Tag Hover Settings…**. Scripts can open them with `system.taghover.openSettings()`, for example from a client menu item.

## Building

Requires JDK 17 or 21.

```
./gradlew build             # build/Tag-Hover-Tooltip.unsigned.modl, and the unit tests
./gradlew :client:runDemo   # try it without a Gateway: real Vision components with fake tags
```

To sign the module, set `ignition.signing.keystoreFile`, `keystorePassword`, `certFile`, `certAlias` and `certPassword` in `~/.gradle/gradle.properties`. The build then also writes `build/Tag-Hover-Tooltip.modl`.

## License

[MIT](LICENSE). Issues and pull requests are welcome.

*Not affiliated with or endorsed by Inductive Automation. Ignition is a trademark of Inductive Automation.*
