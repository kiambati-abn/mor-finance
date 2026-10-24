# Morph 🦋

A personal finance app for Android by **Morph Labs**. Morph reads your M-Pesa SMS, keeps a proper
double-entry ledger, files what it recognises, and asks you about the rest with one tap. Every tap
earns stars and teaches it your habits.

- **Automatic:** every M-Pesa message (send, paybill, till, withdraw, airtime, Fuliza, M-Shwari, reversals) becomes a journal entry, with fees split out.
- **One tap:** a notification right after you pay: *"Ksh 200 → LAKEWOOD HOTEL. What was it?"* with your likely answers as buttons.
- **Learns:** after you file the same person/till/paybill twice the same way, Morph files it for you. It also learns your habits (amount + time of day).
- **Budgets:** daily or monthly amounts on any account: limits for spending, targets for savings, repayment plans for debts. Move budget between envelopes without touching real money.
- **Feedback:** weekly insights from your own numbers (fees, small repeated payments, weekend habits, 50/30/20 split, emergency fund, runway).
- **Patterns:** a deeper look: months compared fairly, when you spend (day and hour), what you pay on a rhythm, top people and places, unusual spends, how fast money goes after payday.
- **Flexible, still simple:** add or edit any account, create your own types ("Church", "Business"), split a transaction, edit what you added by hand, add notes, undo any sort, or start over and re-import.
- **Private:** everything stays on the phone. Automatic daily backup goes to a file you pick once (e.g. on Google Drive). On a new phone: install → "I have a backup".

## Install or update (phone plugged into your laptop)

One-time setup:

1. On the phone, go to Settings → About phone → Software information and tap **Build number** 7 times. Then go to Settings → Developer options and turn on **USB debugging**.
2. On the laptop, install [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (for `adb`) and the [GitHub CLI](https://cli.github.com/), then run `gh auth login`.
3. Plug in the phone and tap **Allow** on the prompt. `adb devices` should list it.

Every install or update after that:

```bash
./scripts/install.sh <owner>/mor-finance                    # Mac / Linux
.\scripts\install.ps1 -Repo <owner>/mor-finance             # Windows PowerShell
```

The script downloads the newest Release and runs `adb install -r`, which keeps your data.
**Never uninstall Morph**: uninstalling deletes its data. Make a backup first (Me → Back up to a file).

To share with friends, run `bash scripts/get-apk.sh` to put the latest `.apk` in `~/MAX`, then send it to them. On their phone they open it and allow
"install unknown apps". If Android then blocks the SMS permission, they go to App info → ⋮ → **Allow restricted settings**.

## Updating from a bundle file (no GitHub access for Claude)

1. Save `mor-finance-latest.bundle` into `~/MAX` (replace the old one).
2. Run `bash ~/MAX/morph-update.sh`.

The script loads the changes, pushes them to GitHub, waits for the build, installs the app on the phone plugged in over USB, and deletes the downloaded file.

## How it's built

| Part | What it is |
|---|---|
| `core/` | Pure Kotlin, no Android: M-Pesa parser, posting rules, learning categorizer, budgets, stars & streaks, insights. Fully unit-tested (`cd core && gradle test`). |
| `app/` | Android app (Kotlin, Jetpack Compose, SQLite). SMS receiver, inbox import, notifications, screens. |
| `.github/workflows/build.yml` | Every push to `main` runs the tests and builds a signed APK, then publishes it as a GitHub Release `v0.1.<n>`. |

More detail: [docs/BASELINE.md](docs/BASELINE.md) (what Morph does and why) and [docs/RELEASING.md](docs/RELEASING.md) (signing, versions, data safety).
