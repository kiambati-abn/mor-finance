package com.morphlabs.morph.core.academy

import com.morphlabs.morph.core.academy.Action.ACCOUNTS
import com.morphlabs.morph.core.academy.Action.AUTOSORT
import com.morphlabs.morph.core.academy.Action.BUDGETS
import com.morphlabs.morph.core.academy.Action.PATTERNS
import com.morphlabs.morph.core.academy.Kind.CONCEPT
import com.morphlabs.morph.core.academy.Kind.QUIZ
import com.morphlabs.morph.core.academy.Kind.YOU
import com.morphlabs.morph.core.academy.Signal.BIG_SPEND
import com.morphlabs.morph.core.academy.Signal.FULIZA_RECENT
import com.morphlabs.morph.core.academy.Signal.HAS_DEBT
import com.morphlabs.morph.core.academy.Signal.HIGH_FEES
import com.morphlabs.morph.core.academy.Signal.MONEY_IN_RECENT
import com.morphlabs.morph.core.academy.Signal.NO_EMERGENCY_FUND
import com.morphlabs.morph.core.academy.Signal.OVER_BUDGET
import com.morphlabs.morph.core.academy.Signal.SAVING_LOW
import com.morphlabs.morph.core.academy.Track.DEALS
import com.morphlabs.morph.core.academy.Track.DEBT
import com.morphlabs.morph.core.academy.Track.FOUNDATIONS
import com.morphlabs.morph.core.academy.Track.FOUNDER
import com.morphlabs.morph.core.academy.Track.FREEDOM
import com.morphlabs.morph.core.academy.Track.INVESTING
import com.morphlabs.morph.core.academy.Track.KENYA_TOOLS
import com.morphlabs.morph.core.academy.Track.MIND
import com.morphlabs.morph.core.academy.deck.DealsCards
import com.morphlabs.morph.core.academy.deck.DebtCards
import com.morphlabs.morph.core.academy.deck.EarningCards
import com.morphlabs.morph.core.academy.deck.EconomyCards
import com.morphlabs.morph.core.academy.deck.FoundationsCards
import com.morphlabs.morph.core.academy.deck.FounderCards
import com.morphlabs.morph.core.academy.deck.FreedomCards
import com.morphlabs.morph.core.academy.deck.InvestingCards
import com.morphlabs.morph.core.academy.deck.KenyaToolsCards
import com.morphlabs.morph.core.academy.deck.MindCards
import com.morphlabs.morph.core.academy.deck.PropertyCards
import com.morphlabs.morph.core.academy.deck.ProtectionCards

/**
 * The starter deck (researched October 2026; see docs/ACADEMY.md for sources). Education, not
 * financial advice. Figures that change are marked timeSensitive so the app says "check current".
 */
object Deck {

    private fun quiz(id: String, t: Track, q: String, options: List<String>, correct: Int, why: String,
                     tryThis: String? = null, action: Action = Action.NONE, signals: Set<Signal> = emptySet(), timeSensitive: Boolean = false) =
        Card(id, t, QUIZ, q, why, options, correct, tryThis, action, signals, timeSensitive)

    private fun concept(id: String, t: Track, q: String, a: String, tryThis: String? = null, action: Action = Action.NONE,
                        signals: Set<Signal> = emptySet(), timeSensitive: Boolean = false) =
        Card(id, t, CONCEPT, q, a, emptyList(), -1, tryThis, action, signals, timeSensitive)

    private fun you(id: String, t: Track, q: String, a: String, tryThis: String? = null, action: Action = Action.NONE, signals: Set<Signal> = emptySet()) =
        Card(id, t, YOU, q, a, emptyList(), -1, tryThis, action, signals)

    private val starter: List<Card> = listOf(
        // ---- 1 · Foundations ---------------------------------------------------------------
        quiz("f-budget", FOUNDATIONS, "What is a budget really for?",
            listOf("Spending less on everything", "Giving every shilling a job before the month spends it", "Tracking what you already spent"), 1,
            "A budget is a plan, not a punishment: you decide where money goes before it quietly goes somewhere else.",
            "Give your top 3 spending accounts a monthly amount.", BUDGETS),
        quiz("f-payfirst", FOUNDATIONS, "Salary lands: Ksh 40,000. Which way actually builds savings?",
            listOf("Spend first, save what's left", "Move Ksh 6,000 to savings the same day, then live on the rest", "Save whatever is left at month end"), 1,
            "\"Pay yourself first.\" Savings that wait for leftovers rarely happen.",
            "Next time money comes in, move your savings amount out within the hour.", signals = setOf(MONEY_IN_RECENT)),
        quiz("f-emergency", FOUNDATIONS, "How big should an emergency fund be?",
            listOf("One week of spending", "3–6 months of fixed costs", "A year of salary"), 1,
            "3–6 months of fixed costs (rent, food, transport, bills), kept safe and quick to reach, e.g. in a money market fund. It stops one bad month from becoming debt.",
            "Create a Savings envelope named Emergency fund.", BUDGETS, setOf(NO_EMERGENCY_FUND)),
        quiz("f-inflation", FOUNDATIONS, "Inflation is about 6.5%. Your savings earn 4%. What happens?",
            listOf("You're growing slowly", "You're losing buying power each year", "Nothing changes"), 1,
            "Money earning less than inflation shrinks in what it can buy. Kenya's target band is 2.5–7.5% (check current).",
            timeSensitive = true),
        quiz("f-compound", FOUNDATIONS, "Ksh 1,000 a month at 10% a year for 10 years grows to about…",
            listOf("Ksh 120,000", "Ksh 205,000", "Ksh 1,000,000"), 1,
            "About Ksh 205,000, of which you put in 120,000. Returns earning returns: time does most of the work."),
        quiz("f-72", FOUNDATIONS, "Rule of 72: at 10% a year, money doubles in about…",
            listOf("3 years", "7 years", "15 years"), 1,
            "72 ÷ 10 ≈ 7.2 years. At 6% inflation, prices double in about 12 years."),
        quiz("f-503020", FOUNDATIONS, "In the 50/30/20 guide, what is the 20%?",
            listOf("Rent", "Fun", "Saving and paying off debt"), 2,
            "About 50% needs, 30% wants, 20% saving and debt repayment. It's a starting point; Morph shows your real split in insights.",
            "Look at your split in Me → What your money says.", PATTERNS),
        quiz("f-networth", FOUNDATIONS, "What is net worth?",
            listOf("Your salary", "What you own minus what you owe", "Your M-Pesa balance"), 1,
            "Net worth is the scoreboard of wealth. Income is just the speed at which you can move it."),
        quiz("f-fees", FOUNDATIONS, "Which M-Pesa payment is free for you as the payer?",
            listOf("Sending money to a person", "Paying a till (Buy Goods)", "Withdrawing at an agent"), 1,
            "Buy Goods tills are free for the customer; sending, paybills (often) and withdrawals cost. Fewer, bigger withdrawals also help (check current tariffs).",
            "See what fees cost you in Patterns.", PATTERNS, setOf(HIGH_FEES), timeSensitive = true),
        you("f-you-fees", FOUNDATIONS, "You paid {fees_month} in M-Pesa fees this month. Over a year that's about {fees_year}.",
            "That's real money for nothing in return. Paying tills directly and withdrawing less often are the two biggest fixes.",
            signals = setOf(HIGH_FEES)),
        you("f-you-emergency", FOUNDATIONS, "Your fixed costs are about {fixed_month} a month, so 3 months of safety is {emergency_target}. You have {savings} saved.",
            "Building this first protects everything else you'll do with money, including starting a business.",
            "Set a monthly amount on your savings envelope.", BUDGETS, setOf(NO_EMERGENCY_FUND)),

        // ---- 2 · Debt ------------------------------------------------------------------------
        quiz("d-price", DEBT, "How should you compare two loans?",
            listOf("By the daily fee", "By the total cost per year, fees included", "By how fast you get the money"), 1,
            "Always compare the total yearly cost (interest + all fees). Small daily fees can hide huge yearly rates."),
        quiz("d-fuliza", DEBT, "Ksh 1,000 of Fuliza costs about Ksh 6 a day after the free days. Kept for a year, that's roughly…",
            listOf("6% a year", "60% a year", "Over 200% a year"), 2,
            "About 219% a year (Ksh 6 × 365 ÷ 1,000). Fuliza is fine for a day or two, very expensive for weeks (check current tariff).",
            "Keep a small buffer in M-Pesa or Ziidi so you stop needing Fuliza.", signals = setOf(FULIZA_RECENT), timeSensitive = true),
        quiz("d-hustler", DEBT, "Hustler Fund personal loan of Ksh 5,000: how much reaches your M-Pesa?",
            listOf("Ksh 5,000", "Ksh 4,750", "Ksh 4,500"), 1,
            "5% is saved automatically, so Ksh 4,750 arrives. The rate is 8% a year for 14 days (about Ksh 15 on 5,000), rising if late (check current terms on *254#).",
            signals = setOf(HAS_DEBT), timeSensitive = true),
        quiz("d-goodbad", DEBT, "Which is closer to \"good\" debt?",
            listOf("A phone on credit for status", "A loan for equipment in a profitable business", "Fuliza for weekend outings"), 1,
            "Good debt buys something that earns more than it costs. Bad debt pays for things that are gone tomorrow."),
        quiz("d-avalanche", DEBT, "You have Fuliza, a SACCO loan and HELB. The avalanche method pays extra to…",
            listOf("The smallest balance", "The most expensive one per year", "The newest one"), 1,
            "Avalanche (most expensive first) saves the most money. Snowball (smallest first) gives quick wins. Both beat only paying minimums.",
            signals = setOf(HAS_DEBT)),
        quiz("d-crb", DEBT, "Why does repaying on time matter beyond avoiding penalties?",
            listOf("It doesn't", "Lenders report it to credit bureaus; a clean record gets cheaper loans later", "It raises your salary"), 1,
            "Credit reference bureaus (CRBs) record your repayment behaviour. This matters a lot when you later need business finance."),
        quiz("d-helb", DEBT, "Ignoring a HELB loan usually leads to…",
            listOf("It being forgiven", "Penalties and a damaged credit record", "Nothing"), 1,
            "Missed HELB payments add penalties and can affect your credit record (check current terms). Paying on schedule is the cheap option.",
            signals = setOf(HAS_DEBT)),
        you("d-you-debts", DEBT, "You owe {debts} in total. Which one costs you the most per year?",
            "Put any extra money on that one first (avalanche), and give each debt a monthly repayment target.",
            "Set a monthly repayment target on your debt accounts.", ACCOUNTS, setOf(HAS_DEBT)),

        // ---- 3 · Kenyan money tools ------------------------------------------------------------
        quiz("k-mmf", KENYA_TOOLS, "What does a money market fund (MMF) invest in?",
            listOf("Shares of startups", "Short-term, low-risk things like T-bills and bank deposits", "Crypto"), 1,
            "MMFs pool money into short-term, low-risk instruments. Interest is calculated daily, and withdrawals usually take about 1–3 working days. Check the fund is CMA-approved."),
        quiz("k-mmf-real", KENYA_TOOLS, "An MMF advertises 10%. After 15% withholding tax and ~6.5% inflation, your real gain is about…",
            listOf("10%", "8.5%", "2%"), 2,
            "10% → 8.5% after tax → about 2% after inflation. Great for safety and emergency funds; not enough on its own to get rich (check current rates).",
            timeSensitive = true),
        quiz("k-ziidi", KENYA_TOOLS, "Ziidi (inside M-Pesa) is…",
            listOf("A loan", "A money market fund you can start with Ksh 100", "A bank account"), 1,
            "Ziidi is a CMA-regulated MMF in M-Pesa (*334# or the app): from Ksh 100, daily interest, free moves in and out. There's also a Shari'ah version (check current terms).",
            "Try auto-investing a small amount after each transaction.", timeSensitive = true),
        quiz("k-tbill", KENYA_TOOLS, "Treasury bills are…",
            listOf("Loans to the government for 91, 182 or 364 days", "Shares in the CBK", "Electricity bills"), 0,
            "You lend to the government short-term, buying through the CBK's DhowCSD app and paying by M-Pesa. Non-competitive bids start at Ksh 50,000 (CBK 2026; check current).",
            timeSensitive = true),
        quiz("k-noncomp", KENYA_TOOLS, "For a first T-bill or bond, most individuals choose…",
            listOf("A competitive bid", "A non-competitive bid", "A broker loan"), 1,
            "A non-competitive bid accepts the average rate the auction sets: simple and safe for individuals."),
        quiz("k-bond", KENYA_TOOLS, "A government bond typically pays you…",
            listOf("Nothing until the end", "Interest twice a year for years", "A share of profits"), 1,
            "Bonds are longer loans to the government paying a coupon twice a year. Some retail issues allowed Ksh 50,000 bids; bond interest has withholding tax (check each prospectus).",
            timeSensitive = true),
        quiz("k-sacco", KENYA_TOOLS, "In a SACCO you can earn…",
            listOf("Only interest", "Dividends on shares and interest on deposits", "Nothing; it's only for loans"), 1,
            "Members earn dividends on share capital and interest on deposits, and can borrow (often a multiple of deposits). Averages have been around 10% (check current)."),
        quiz("k-sacco-safe", KENYA_TOOLS, "Before joining a deposit-taking SACCO, check that it's licensed by…",
            listOf("SASRA", "KRA", "KPLC"), 0,
            "SASRA regulates deposit-taking SACCOs. Also read its results, and don't keep everything in one SACCO: some have failed."),
        quiz("k-nse", KENYA_TOOLS, "Buying shares on the NSE needs…",
            listOf("A CDS account through a stockbroker", "Only an M-Pesa account", "A business permit"), 0,
            "You hold shares in a CDS account through a licensed broker. Prices swing: it's long-term money only."),
        quiz("k-nssf", KENYA_TOOLS, "NSSF contributions in 2026 are…",
            listOf("Only from the employer", "6% from you and 6% from your employer, up to limits", "Optional"), 1,
            "6% + 6% on pensionable pay within Tier I and Tier II limits (check current). It's slow, boring compounding for later life.",
            timeSensitive = true),
        quiz("k-where", KENYA_TOOLS, "No emergency fund yet. Where should Ksh 20,000 of savings go first?",
            listOf("NSE shares", "A money market fund", "A 10-year bond"), 1,
            "Emergency money must be safe and quick to reach, so an MMF. Once that's built, longer-term options (T-bills, bonds, shares) make sense.",
            signals = setOf(NO_EMERGENCY_FUND)),

        // ---- 4 · Investing ---------------------------------------------------------------------
        quiz("i-risk", INVESTING, "\"High returns, no risk\" usually means…",
            listOf("A great deal", "Someone is hiding the risk, or it's a scam", "Government backing"), 1,
            "Higher possible returns always come with bigger possible losses."),
        quiz("i-diversify", INVESTING, "Diversification means…",
            listOf("Putting everything in the best option", "Spreading money so one failure doesn't sink you", "Changing investments often"), 1,
            "Mix cash, bonds, shares, property or business so one bad outcome can't wipe you out."),
        quiz("i-horizon", INVESTING, "Money you'll need in 6 months belongs in…",
            listOf("Shares", "A money market fund", "Land"), 1,
            "Short-term money stays safe and liquid. Money for 5+ years can ride ups and downs for higher growth."),
        quiz("i-fees", INVESTING, "An investment charges 2% a year. Over 20 years that…",
            listOf("Barely matters", "Can take a large slice of your result", "Is always worth it"), 1,
            "Fees compound against you. Always ask what it costs per year, all-in."),
        quiz("i-dca", INVESTING, "Investing the same amount every month regardless of price is called…",
            listOf("Market timing", "Dollar-cost averaging", "Day trading"), 1,
            "You buy more when prices are low and avoid guessing the market. Auto-invest features do this for you."),
        quiz("i-index", INVESTING, "An index fund…",
            listOf("Picks a few hot stocks", "Owns a whole market at low cost", "Guarantees returns"), 1,
            "Broad, cheap and diversified: the default long-term choice for many investors worldwide."),
        quiz("i-real", INVESTING, "12% return in a year with 7% inflation is about…",
            listOf("12% real growth", "5% real growth", "19% real growth"), 1,
            "Real return ≈ return − inflation. Always think in buying power."),
        quiz("i-ponzi", INVESTING, "A friend shows screenshots: \"double your money in 30 days\". You…",
            listOf("Try a small amount", "Walk away", "Ask for more screenshots"), 1,
            "Guaranteed quick doubling is the signature of Ponzi and pyramid schemes. Screenshots prove nothing."),

        // ---- 5 · Financial freedom --------------------------------------------------------------
        quiz("x-what", FREEDOM, "Financial freedom means…",
            listOf("Being rich", "Your assets cover your life, so work becomes a choice", "Never spending"), 1,
            "It's about your money covering your costs, not about luxury."),
        concept("x-stages", FREEDOM, "What are the 5 stages of financial freedom?",
            "1 Survival (paycheck to paycheck) → 2 Stability (bills covered, small buffer) → 3 Security (3–6 month fund, no bad debt) → 4 Independence (investments cover basic costs) → 5 Freedom (they cover the life you want). Your next stage is your goal."),
        quiz("x-number", FREEDOM, "You spend Ksh 600,000 a year. The classic freedom number (×25) is…",
            listOf("Ksh 6M", "Ksh 15M", "Ksh 60M"), 1,
            "Yearly spending × 25 = Ksh 15M. It comes from the \"4% rule\": withdrawing 4% a year."),
        quiz("x-4pct", FREEDOM, "Why might 4% be too high a withdrawal rate in Kenya?",
            listOf("It isn't", "It's based on US market history; studies found emerging markets less safe", "Taxes are zero"), 1,
            "The 4% rule comes from US data and ignores fees and taxes. Research on 25 emerging markets found 4% often wasn't safe. A more careful plan uses 3–3.5% (×30 to ×33)."),
        quiz("x-rate", FREEDOM, "What matters most for how fast you reach freedom?",
            listOf("Your income", "Your savings rate", "Your luck"), 1,
            "Roughly (5% real return, from zero): save 10% → ~50 years, 20% → ~37, 30% → ~28, 50% → ~17, 70% → ~8.5. Savings rate dominates.",
            signals = setOf(SAVING_LOW)),
        quiz("x-assets", FREEDOM, "Which one is an asset?",
            listOf("A car loan", "A small rental room you let out", "A phone on credit"), 1,
            "Assets put money in your pocket; liabilities take it out. Wealth = buying assets with income."),
        concept("x-ladder", FREEDOM, "What is the passive income ladder?",
            "MMF interest → T-bills and bonds → dividends → rental income → business profits run by others. Each rung needs more money, knowledge or time, so climb one at a time."),
        quiz("x-coast", FREEDOM, "\"Coast FI\" means…",
            listOf("You stop working now", "Your investments will grow to your number alone, so you only need to earn your living costs", "You retire at the coast"), 1,
            "Once you hit Coast FI, any work only has to pay for today: real freedom of choice, early."),
        quiz("x-sequence", FREEDOM, "Why keep 1–2 years of spending in cash when living off investments?",
            listOf("No reason", "A crash in the first years hurts much more (sequence risk)", "For taxes"), 1,
            "Selling investments during an early crash locks in losses. A cash buffer lets you wait."),
        quiz("x-creep", FREEDOM, "You get a 20% raise. The freedom move is…",
            listOf("Upgrade everything", "Bank at least half of the raise", "Take a loan for a car"), 1,
            "Lifestyle creep (spending rising as fast as income) keeps freedom forever out of reach.",
            signals = setOf(MONEY_IN_RECENT)),
        quiz("x-human", FREEDOM, "Early in life, your biggest asset is usually…",
            listOf("Your savings", "Your skills and earning power", "Your phone"), 1,
            "Investing in skills, and later a business, can move you up the stages faster than any fund."),
        you("x-you-number", FREEDOM, "You spend about {spend_year} a year. At ×30, your freedom number is {freedom_number}. Your net worth is {net_worth}: {freedom_pct} of the way.",
            "Raising your savings rate and cutting yearly spending both shrink the gap; spending less also lowers the number itself."),
        you("x-you-rate", FREEDOM, "Last month you kept {savings_rate} of what came in. Your freedom stage: {stage}.",
            "Try nudging your savings rate up by 5 points for 3 months. It compounds into years of earlier freedom.",
            "Set a monthly savings target.", BUDGETS, setOf(SAVING_LOW)),
        you("x-you-runway", FREEDOM, "If income stopped today, your savings would cover about {runway} of fixed costs.",
            "Runway is freedom you can measure. 6 months buys you the time to choose; for a founder it's time to build.",
            signals = setOf(NO_EMERGENCY_FUND)),

        // ---- 6 · Founder -----------------------------------------------------------------------
        quiz("b-problem", FOUNDER, "Great businesses usually start from…",
            listOf("A cool idea", "A painful, frequent problem people already pay to solve", "Funding"), 1,
            "Find the pain first. If people already spend money or time on it, there's a business."),
        concept("b-journal", FOUNDER, "What is a problem journal?",
            "A running list of frustrations you notice: yours and others'. Rate each: how painful, how often, how many people, would they pay? Your best idea is usually in there.",
            "Write down 3 problems you noticed this week."),
        quiz("b-interview", FOUNDER, "The best question when testing an idea is…",
            listOf("\"Would you use my app?\"", "\"When did this problem last happen, and what did you do?\"", "\"Do you like it?\""), 1,
            "Ask about past behaviour, not your idea. People are polite about ideas and honest about what they actually did."),
        quiz("b-mvp", FOUNDER, "A good first MVP for a delivery idea is…",
            listOf("A full app", "A WhatsApp number and you doing deliveries by hand", "A big launch event"), 1,
            "Test demand with the smallest thing that works, before building anything big."),
        quiz("b-margin", FOUNDER, "You sell at Ksh 100; it costs Ksh 60 to make. Gross margin is…",
            listOf("60%", "40%", "100%"), 1,
            "(100 − 60) ÷ 100 = 40%. Low margins need huge volume to survive."),
        quiz("b-breakeven", FOUNDER, "Rent is Ksh 30,000 a month and you make Ksh 50 per item. To break even you must sell…",
            listOf("60 items", "600 items", "6,000 items"), 1,
            "Fixed costs ÷ profit per unit = 30,000 ÷ 50 = 600 a month."),
        quiz("b-ltv", FOUNDER, "A customer brings Ksh 200 profit a month for 10 months; winning them costs Ksh 1,000. LTV:CAC is…",
            listOf("2× (weak)", "3× (healthy)", "10×"), 0,
            "LTV 2,000 ÷ CAC 1,000 = 2×. Aim for at least 3×, by keeping customers longer, raising margin or spending less to win them."),
        quiz("b-cash", FOUNDER, "A \"profitable\" business can still die because…",
            listOf("It can't", "Cash arrives later than bills are due", "Taxes"), 1,
            "Cash flow beats profit. Track cash in and out weekly."),
        quiz("b-raise", FOUNDER, "When does raising investor money make sense?",
            listOf("Always, as early as possible", "When money, not knowledge, is what's holding growth back", "Never"), 1,
            "Bootstrapping keeps control and teaches discipline; raise once you know the model works and money is the bottleneck."),
        quiz("b-mpesa", FOUNDER, "Why did M-Pesa take off?",
            listOf("Heavy advertising only", "It solved a frequent pain (sending money home) using a habit people had (airtime agents)", "It was free"), 1,
            "Look for problems that ride on habits people already have."),
        you("b-you-runway", FOUNDER, "Your savings are {savings}; your fixed costs are {fixed_month} a month. That's {runway} of runway to build something.",
            "Founders measure runway in months. Growing it is the first step towards building your business.",
            "Create a 🚀 Business fund savings account with a monthly target.", ACCOUNTS),

        // ---- 7 · Deals (Shark Tank) ---------------------------------------------------------------
        quiz("s-valuation", DEALS, "\"Ksh 2M for 10%\" values the business at…",
            listOf("Ksh 2M", "Ksh 20M", "Ksh 200M"), 1,
            "2M ÷ 0.10 = Ksh 20M."),
        quiz("s-multiple", DEALS, "A business makes Ksh 1M profit a year and asks for a Ksh 20M valuation. That's…",
            listOf("Cheap", "20× profit: expensive unless it's growing fast", "Fair for any business"), 1,
            "Small, steady businesses often sell for a few times their yearly profit. 20× needs fast, believable growth."),
        quiz("s-mboga", DEALS, "Mama mboga makes Ksh 540,000 profit a year and asks Ksh 100,000 for 20%. The key question is…",
            listOf("Is she friendly?", "Is that profit really her own salary, and does it continue without her?", "Is the stall big?"), 1,
            "A Ksh 500,000 valuation looks cheap, but value what the business earns without the owner's daily work."),
        quiz("s-dilution", DEALS, "You own 100%, sell 20%, then later sell 25% of the company. You now own…",
            listOf("55%", "60%", "75%"), 1,
            "80% × (1 − 0.25) = 60%. New shares shrink everyone's slice."),
        quiz("s-royalty", DEALS, "A founder with steady sales who wants to keep ownership might prefer…",
            listOf("Selling 50% equity", "A royalty or loan deal", "No deal"), 1,
            "Royalty (a % of sales until a cap) or debt lets them raise money without giving away the company."),
        quiz("s-redflag", DEALS, "Biggest red flag in a pitch?",
            listOf("Few competitors", "\"No competitors at all\" and no paying customers", "A small team"), 1,
            "No competitors usually means no market or no research. No paying customers means the idea is untested."),
        concept("s-case", DEALS, "How do you judge any pitch, Shark Tank style?",
            "Ask: What's the problem? Who pays? What's the margin? How many customers so far? CAC vs LTV? What's the ask, and the valuation it implies? Then: invest, counter-offer, or pass, and say why."),

        // ---- 8 · Money mind --------------------------------------------------------------------------
        quiz("m-present", MIND, "The most reliable way to save despite temptation is…",
            listOf("Willpower", "Automation (auto-save, envelopes)", "Checking your balance often"), 1,
            "Present bias makes today feel bigger than tomorrow. Automation decides once, then works every day.",
            "Turn on Auto-sort or envelopes so the system does the work.", AUTOSORT),
        quiz("m-social", MIND, "You spend more when out with friends because…",
            listOf("Prices are higher", "Social pressure and keeping up", "Bad luck"), 1,
            "Social spending is normal; seeing it is the first step. Check Friends & social and Fun in Patterns.",
            "Look at your weekend spending in Patterns.", PATTERNS, setOf(OVER_BUDGET)),
        quiz("m-sunk", MIND, "You've put Ksh 50,000 into a failing side project. The sunk-cost trap is…",
            listOf("Stopping now", "Continuing only because you've already spent so much", "Asking for advice"), 1,
            "Past money shouldn't drive the next decision. Ask: knowing what I know now, would I start this today?"),
        quiz("m-scam", MIND, "Which is a scam warning sign?",
            listOf("Written terms", "\"Today only\", secrecy, guaranteed returns, pay-to-recruit", "A licensed company"), 1,
            "Any one of these is reason to stop and check with the regulator (CMA, SASRA, CBK)."),
        quiz("m-scarcity", MIND, "Why do people under money stress make expensive choices?",
            listOf("They don't care", "Scarcity narrows focus to today", "They lack intelligence"), 1,
            "Stress tunnels thinking towards the urgent. Building a buffer literally improves your decisions.",
            signals = setOf(FULIZA_RECENT, BIG_SPEND)),
    )

    /** Every card: the starter set (all level 1) plus each area's file in deck/. */
    val cards: List<Card> = starter + listOf(
        FoundationsCards.cards, DebtCards.cards, KenyaToolsCards.cards, InvestingCards.cards, FreedomCards.cards, FounderCards.cards,
        DealsCards.cards, MindCards.cards, EconomyCards.cards, ProtectionCards.cards, EarningCards.cards, PropertyCards.cards,
    ).flatten()

    private val index: Map<String, Card> by lazy { cards.associateBy { it.id } }

    fun byId(id: String): Card? = index[id]
}
