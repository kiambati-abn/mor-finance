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

## Tap anything to see what's behind it

Every amount opens the list of its transactions. The list shows its total, and each transaction opens to change its category, split it, add a note or delete it.

- **Home:** "Spent today", the Today / Yesterday / 7 / 30 days tiles, the averages, each day of the chart ("See that day's transactions"), every "Where it went" row, and insights about specific spending (fees, repeated small payments, an over-budget category).
- **Patterns:** each month, regular payments, top people and places, and inside a category's drill-down, its places and "All transactions".
- **Me:** every wallet, savings and debt balance; Fixed, Discretionary and Money in this month.
- **Accounts & budgets:** tap an account for its transactions (Edit is a button); budgets and envelopes have History / See transactions.
- **Fees:** M-Pesa fees are split out automatically, and every transaction with a fee shows "fee 7 (auto)". Fees never need sorting.

## Where it went (Home)

- Two views: **Where** (bars by Category, Type or People & places) and **When** (bars per day; per week for 3 months).
- Periods: 7 days, 30 days, this month, 3 months, this year, all time (When shows months for the last two). Your choice is remembered. Patterns has the same Year and All options.
- Each row shows its share of spending; tapping any row or bar opens its transactions.
- One colour on purpose: bars stay readable with any number of categories.

## Spending chart & reasons

- **Home → Last 14 days:** one bar per day, today highlighted. Tap a bar to see that day and **why**, e.g. "🏠 Rent Ksh 5,000 (LANDLORD) · 🍔 Food Ksh 300". Unusually big days get a gold dot: 1.5× your usual day, and at least Ksh 500 more.
- **Patterns → Spending by month:** tap a month to see what changed against your usual month, e.g. "▲ 🎉 Fun +Ksh 3,000 (mostly CLUB) ▼ 🚌 Transport −Ksh 800".
- Reasons are worked out from what you've sorted; you never type them. Notes you add to transactions show up there.

## Budget traffic lights & alerts

| Level | When | Shown as |
|---|---|---|
| 🟢 On track | more than 25% left | purple bar |
| 🟡 Getting low | 25% or less left | gold bar + "Getting low" |
| 🔴 Almost out | 10% or less left | red bar + "Almost out" |
| ⛔ Over | below zero | red bar + "Over" |

- Applies to spending budgets and envelopes (daily ones use what's left today). Savings and debt targets don't warn.
- Shown on budget/envelope cards, Accounts, Sort buttons ("⚠️ Ksh 100 left") and a Home card ("2 budgets running low", which opens Budgets filtered to Running low).
- **Alerts:** a notification once when a budget reaches 🔴 Almost out and once if it goes ⛔ Over, per day or month. Switch off under Me → Budget alerts.

## Accounts, one tap away

- Home has **🔍 Find an account…**, which opens Accounts. Search by name, or filter: Spending · Savings · Debts · Wallets · Income · With budget.
- A spending account with a budget shows **"Ksh 700 left"** in its traffic-light colour, a thin bar and "Spent Ksh 1,300 of 2,000 · 65%". Others show this month's spending, money in, or their balance.
- The Budgets summary shows what's left (large), a bar and "Spent X of Y · N% used". The envelopes header shows what's left in spending envelopes and % used.

## Finding and checking budgets

- **Budgets tab:** a search box and type filters (All · Spending · Savings & debts · Running low) stay pinned at the top while you scroll.
- **Can I afford?** Type an amount and every budget shows ✓ "Ksh 400 left after" or ✗ "Short by Ksh 200". The ones that can cover it come first. Daily envelopes check against what's left *today*.
- **Averages:** Home shows average spending per day, week and month (over the last 90 days, or since you started). Patterns shows the same for the period you pick.
- **Refresh:** pull down on Home, or tap ↻. Morph re-reads the last 3 days of M-Pesa SMS, re-matches the M-Pesa balance (including the last half hour), and recalculates.

## Cash

- A withdrawal moves money from M-Pesa to Cash; the fee goes to Withdrawal charges.
- Morph then asks: **"💵 Ksh 2,000 cash. What's it for?"** You can answer from the notification, from the Sort tab, or from the withdrawal's details.
- Tap your usual cash categories (Food, Transport…), type the amounts, and Save. Each part becomes a cash spend (+1⭐ each); anything you don't spread stays in Cash. "Keep as cash" stops the question.
- Parsed wording: "Withdraw Ksh… from AGENT" and "You have withdrawn Ksh… from AGENT".

## Envelopes & Budget view (optional, Me → Envelopes)

- Every shilling in your wallets (M-Pesa, Cash, Bank, Airtel Money) is either **in an envelope** or **To assign**.
- **Money in** → To assign. **Fill my envelopes** tops each envelope up to its amount, fixed costs first. A monthly envelope fills to its monthly amount; a daily one (Food 300/day) fills to 300 × the days left in the month. You can also add, take back, or move money between envelopes by hand.
- **Spending** on a category takes it out of that envelope. Saving into Chama, or repaying HELB, uses up those envelopes. Spending on something with no envelope comes out of To assign.
- **Leftovers carry over.** Overspending turns an envelope red; cover it by moving money from another.
- **Budget view** hides real wallet balances and net worth; you see "In envelopes" and "To assign" instead. Tap 👁 to peek. Sort buttons and notifications show what's left, e.g. "🍻 Friends · Ksh 100 left".
- Without envelopes, the daily/monthly amounts work as simple limits and targets, as before.

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

**Keeps improving:** when you open the app and in the daily background job (at most every 6 hours), Morph re-runs Auto-sort on the waiting pile and re-checks its *own* earlier filings. One moves only when Morph is now 75%+ sure of a different account and surer than before; each filing's own vote is left out of the re-check. Your sorted or confirmed transactions and your splits are never changed. Me → Auto-sort shows the last check, with "Re-check now".

**Sort everything for me** (Sort tab, shown when 10+ are waiting) files the whole pile in rounds: first what Morph is 75%+ sure of, then 60%+ and 45%+ (learning between rounds), then everything left gets its best guess. Anything under 75% is flagged 🤔. Forced low-confidence guesses count only a quarter as much in learning, so a wrong guess can't spread; confirming or fixing one makes it count fully.

Your choices always outweigh starter knowledge. Correcting an auto-filed item teaches Morph immediately, and other waiting items from the same counterparty are re-checked.

## The game

- **Stars:** +1 for each item sorted, +2 if you sort it within 15 minutes of paying, +5 once a day for reaching inbox zero, +1 for confirming an auto-filed item or adding one by hand.
- **Day complete:** when every one of today's transactions is sorted, +3 ⭐ once a day, with a full-screen celebration (butterflies, stars, streak, double buzz) and a "🦋 Day complete!" notification.
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
- **Split works out the rest:** the first account takes the whole amount. Each one you add gets the amount you type, and the first adjusts by itself, so a split always adds up.
- **Split while sorting:** ✂️ Split in the Sort game and on the "What was it?" notification. Spread one payment over several accounts; it earns the same stars and can be undone.
- **Sorting:** swipe → to go back and ← to skip or go forward; Undo (bar at the bottom, and on the reward notification); ← Previous to step back through what you just sorted; Skip moves an item to the end, "Same for all N from X", search plus recent accounts in "Something else…", and an optional note.
- **Start over:** Me → Start over. Either keep accounts and budgets or wipe everything; you then go back to setup and re-import history. A copy is saved first.

## Tracking start date

- Set during setup (the history window you chose, or today if you skipped), and changeable under Me → 📅 Tracking starts.
- M-Pesa SMS from before the start date are not imported. Waiting items from before it are hidden from Sort (kept, not deleted).
- **Pull earlier history:** 1 month, 3 months, 6 months, 1 year or everything before the start date. Morph imports that period, moves the start date back, and offers 🪄 Sort them for me.
- M-Pesa balances stay correct either way: reconciliation re-anchors to the earliest imported SMS.

## Backups

- **Automatic:** Me → Backup → pick a file once (choose Google Drive in the picker), then choose how often: daily, weekly or monthly. Morph rewrites it on that schedule and whenever you open the app and the last backup is over a day old. If it fails, you get a notification.
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

- **Financial tips:** a short tip of the day based on your own numbers, plus a weekly "lesson".
- **AI coach (optional, opt-in):** "Ask Morph" questions and a weekly review from an AI model. Only summaries leave the phone (category totals, trends, envelope status), never SMS text, names or phone numbers. The API key lives in a small server Morph calls, never inside the APK.
- Hustler Fund / HELB messages: confirm the exact wording from real SMS and add interest/fee splitting.
- M-Pesa statement PDF import for older history.
- A home-screen widget for quick cash entries.
- In-app update check (needs the Releases to be reachable by friends; see RELEASING.md).
