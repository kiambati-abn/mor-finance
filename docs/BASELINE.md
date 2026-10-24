# Morph baseline

The agreed starting point: what Morph does, the money model underneath it, and what's left for later.

## Principles

1. **Super easy:** 4 tabs (Home, Sort, Budgets, Me) plus one notification. Words like "debit" and "credit" only appear in a transaction's details.
2. **Super helpful:** it records automatically, asks only when it must, and files on its own once it has learned.
3. **Teaches you about yourself:** every insight uses your own numbers and ends with one lesson.

## Money model (double entry, under the hood)

| Group | Type | Examples |
|---|---|---|
| Wallets | Asset | M-Pesa, Cash, Bank, Airtel Money |
| Savings | Asset | M-Shwari, Savings, **Chama** |
| Owed to me | Asset | Friends owe me |
| Debts | Liability | Fuliza, Hustler Fund, HELB, I owe friends |
| Income | Income | Salary, Business & hustles, Gifts |
| Fixed / Discretionary | Expense | Food, Rent, Transport… / Love, Fun, Gadgets… |
| System | Equity | Uncategorized (waiting to be sorted), Opening balance |

- Every entry balances to zero. M-Pesa fees are posted automatically to *Transaction costs*, *Withdrawal charges* or *Fuliza charges*.
- A loan you receive is not income, and repaying it is not spending. Moving money to savings is not spending either.
- The very first M-Pesa SMS sets the M-Pesa opening balance, so Morph matches M-Pesa to the cent. If an SMS is missing, Home shows the gap and offers to add it as an item to sort.
- "Set balance" corrections are posted against Opening balance, so they never show up as spending or income.

## Budgets

- Any account can have an amount **per day** or **per month**: a limit for expenses, a target for savings and income, or a repayment plan for debts.
- *Move budget* shifts the plan between envelopes for the current day or month. Real money doesn't move. (This replaces the yellow "transfer" rows from the Notion journal.)
- *Safe to spend today* = what's left in today's daily envelopes + what's left in each monthly envelope ÷ the days left in the month.

## Learning (how auto-filing works)

1. **Same counterparty.** Filed the same way at least twice, at least 80% of the time, and most recently → filed automatically.
2. **Shared words** ("LAKEWOOD HOTEL" vs "LAKEWOOD CAFE") → suggestion.
3. **Habits**: the same kind of payment, a similar amount and a similar time of day → suggestion.
4. **Starter knowledge** of Kenyan merchants (KPLC, Naivas, Carrefour, HELB, Hustler Fund, Safaricom bundles…) → suggestion. Only very clear cases (KPLC, airtime, HELB) are filed automatically.

Routines (3) are about the same amount at about the same time of day, even to different people: the morning bus fare paid to a new conductor each day. Amounts score highest when identical, less within 10% or 30%. Times within 2 hours count, the closer the better, and weekday/weekend is taken into account.

Every guess has a **confidence** (0–100%): the top choice's share of all the evidence, times how much evidence there is.

**Auto-sort** (Me → Auto-sort):
- **Off:** only rock-solid matches are filed automatically.
- **Careful:** also files anything that is at least 75% sure.
- **Bold:** also files anything that is at least 55% sure. Auto-filed items below 75% are flagged 🤔 in the Sort tab to check.

When you turn Auto-sort on, it runs over everything already waiting.

Your choices always outweigh starter knowledge. Correcting an auto-filed item teaches Morph immediately, and other waiting items from the same counterparty are re-checked.

## The game

- **Stars:** +1 for each item sorted, +2 if you sort it within 15 minutes of paying, +5 once a day for reaching inbox zero, +1 for confirming an auto-filed item or adding one by hand.
- **Streak:** consecutive days with nothing left unsorted. Days with no transactions don't break it.
- **Levels:** 🥚 Egg → 🐛 Caterpillar (25) → 🌀 Cocoon (100) → 🦋 Butterfly (300) → 👑 Monarch (1000).

## SMS formats

Built and tested from real messages on the owner's phone (anonymized in `core/src/test`):
send (with and without a phone number), paybill, till, received.

Also built, from standard Safaricom wording, but **still to be confirmed with real messages**:
withdraw, deposit, airtime, Fuliza draw/repay, M-Shwari in/out, reversal.
Anything with an M-Pesa code and a balance in unknown wording is still recorded; the balance change tells Morph which way the money went.

## Flexibility (v0.2)

- **Accounts:** add, rename, re-emoji, re-type, budget, archive, or correct the balance of any account.
- **Your own types:** create a name ("Church") and pick which basic group it behaves like (here, Discretionary). The money maths always uses the basic group.
- **Transactions:** change the category, add a note, split across accounts, delete. You can edit the amount, date, from and to only on entries you added by hand. An M-Pesa entry's amount always comes from the SMS, so balances stay true.
- **Sorting:** swipe → to go back and ← to skip or go forward; Undo (bar at the bottom, and on the reward notification); ← Previous to step back through what you just sorted; Skip moves an item to the end, "Same for all N from X", search plus recent accounts in "Something else…", and an optional note.
- **Start over:** Me → Start over. Either keep accounts and budgets or wipe everything; you then go back to setup and re-import history. A copy is saved first.

## Backups

- **Automatic:** Me → Backup → pick a file once (choose Google Drive in the picker). Morph rewrites it about once a day and whenever you open the app and the last backup is over a day old. If it fails, you get a notification.
- **Manual:** Me → Back up to a file / Restore from a backup.
- **New phone:** install the APK, tap **I have a backup** on the first screen, pick the file, allow SMS again, and continue. The backup link is per phone, so turn automatic backup on again on the new one.

## Patterns screen

| Section | Question it answers |
|---|---|
| Spending by month | Am I spending more over time? |
| This month vs your usual | Which category is off this month? (fair: same number of days from the last 3 months) |
| When you spend | Which days and times cost me most? |
| Things you pay regularly | What goes out on autopilot, and what does it cost per month? |
| Top people & places | Who and where gets my money? |
| Unusual spends | What was far bigger than normal for its category (3× the median)? |
| After money comes in | How much of what arrives is gone within 3 days? |

## Later

- Hustler Fund / HELB messages: confirm the exact wording from real SMS and add interest/fee splitting.
- M-Pesa statement PDF import for older history.
- A home-screen widget for quick cash entries.
- In-app update check (needs the Releases to be reachable by friends; see RELEASING.md).
