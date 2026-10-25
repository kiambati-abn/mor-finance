package com.morphlabs.morph.core.academy.deck

import com.morphlabs.morph.core.academy.Action
import com.morphlabs.morph.core.academy.Card
import com.morphlabs.morph.core.academy.Kind
import com.morphlabs.morph.core.academy.Signal
import com.morphlabs.morph.core.academy.Track

// Card builders for the area files. Keep text short: question ≤ 160 chars, options ≤ 70, answer ≤ 320, tryThis ≤ 110.

/** A multiple-choice card. [correct] is the index into [options]; the app shuffles options when showing them. */
internal fun quiz(
    id: String, track: Track, level: Int, question: String, options: List<String>, correct: Int, why: String,
    source: String? = null, tryThis: String? = null, action: Action = Action.NONE,
    signals: Set<Signal> = emptySet(), timeSensitive: Boolean = false,
) = Card(id, track, Kind.QUIZ, question, why, options, correct, tryThis, action, signals, timeSensitive, level, source)

/** A question you answer in your head, then reveal and rate yourself. */
internal fun concept(
    id: String, track: Track, level: Int, question: String, answer: String,
    source: String? = null, tryThis: String? = null, action: Action = Action.NONE,
    signals: Set<Signal> = emptySet(), timeSensitive: Boolean = false,
) = Card(id, track, Kind.CONCEPT, question, answer, emptyList(), -1, tryThis, action, signals, timeSensitive, level, source)

/** A card built from your own numbers using {placeholders} (see MoneyFacts for the list). */
internal fun you(
    id: String, track: Track, level: Int, question: String, answer: String,
    tryThis: String? = null, action: Action = Action.NONE, signals: Set<Signal> = emptySet(),
) = Card(id, track, Kind.YOU, question, answer, emptyList(), -1, tryThis, action, signals, false, level, null)
