---
title: Dashboard
sidebar_position: 6
---

# Dashboard

:::info New in 2.1
The web dashboard comes with Companies 2.1, on servers that switched it on. For now it shows your orders, your company
and the market; you still place, accept and deliver orders in game.
:::

The dashboard shows your orders, your company and its orders, your inbox, the rankings and market prices in a browser.
Keep it open on your phone next to the game: it updates while things happen on the server.

## Opening it

1. Run `/company dashboard`.
2. The chat shows a short link and a code, for example:

   ```
   Open companies.eldoria.de/AB12CD on any device and enter the code.
   Login code: K P M W Q R, valid for 5 minutes
   ```

   Click the link, or type it into any browser, for example on your phone.
3. Enter the six letters of the code.

The chat says "The dashboard was opened on a browser" when you are logged in. If you see that message without having
logged in, run `/company dashboard logout` right away.

The code works for five minutes. After that, run `/company dashboard` again.

## Staying logged in

You stay logged in on that browser until you log out, or until you have not used the dashboard for a while (an hour by
default) or the server's maximum time has passed (a day by default). You can be logged in on several devices at once.

| Command | What it does |
|---|---|
| `/company dashboard` | Shows a new link and code. |
| `/company dashboard sessions` | Lists the browsers you are logged in on. |
| `/company dashboard logout` | Logs you out on every browser. |

## What you see

You see what you see in game, nothing more. Your own orders show their progress, but not which company or worker
delivers them. Your company's orders show their progress, but not who placed them. Inside your company, you see which
member delivered what.
