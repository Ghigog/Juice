# Intake type classification: why prose with a colon became a List

Spike T-74; the recommended rules below were applied in T-67-T4 (`RulesFallback`: `bulletList`, `colonList`, `plainList`, `areListItems`). The evidence is `core/src/test/kotlin/com/juice/core/intake/TypeCorpusTest.kt`
(49 labelled captures, all six types, fixed `ZonedDateTime`, no clock or randomness).

## Root cause

Groq does not decide the type. Everything below is checked in the code:

- `CaptureIntake.kt:22` lands `RulesFallback.classify(...)` before any cloud call.
- `IntakeParser.kt:33` merges the model's answer with `parsed.improvingOn(RulesFallback.classify(rawText, now))`, and
  `IntakeMerge.kt` (`improvingOn`) copies `type = rules.type` and `cadence = rules.cadence`. The model's `type` is discarded.
- The prompt tells the model the same (`GroqClient.kt:157`, "The app decides the type from the wording").

The rule that made the card a List is `RulesFallback.listRule` (`RulesFallback.kt:131`, first entry of `typeRules`, line 83), through
`colonList` (`RulesFallback.kt:205-211`): any text with a `:` followed by two or more parts, split on `,` or `and`, is a List. There is
no cap on item length, no check on the title before the colon, and no check that the parts are noun phrases. `plainList`
(`RulesFallback.kt:217-229`) has `MAX_LIST_ITEM_WORDS = 4` (line 55); `colonList` has nothing, so it is the culprit. Confirmed.

### The screenshot capture

`formic factories: instead of a board, make a Factorio like factory to have multiple steps such as artists, designers and modellers`

- Rule that fired: `colonList` (colon at index 16, 4 parts after the split). No date phrase, so List, not Project.
- `classify` returns type `List`, title `formic factories`, steps `Instead of a board`, `Make a Factorio like factory to have multiple steps such as artists`, `Designers`, `Modellers`.
- Where the card's steps came from: the card shows 5 steps. The rules' split cannot produce `Such as artists` as its own step (it stays glued to the "make" clause), so that step, the cut-down "Make a Factorio like facto…" and the "Done when: Factory built with all steps completed" line came from Groq. `improvingOn` keeps the model's `sub_items` whenever the type has steps (`subItems.ifEmpty { rules.subItems }`). So Groq had no part in the **type**, but it did write the **steps** and the completion condition; the rules only decided that the card has steps at all.
- Test row: `TypeCorpusTest.theScreenshotCaptureIsAListFromTheColonRule`, and the row is listed in `knownMisses` (expected Note, gets List).

## Corpus and current results

49 rows: true colon and verb lists, prose with colons, commas or 'and', notes, single tasks, goals, projects with dates, routines, and two bulleted blocks.
The current rules get 35 of 49 right. 14 rows are known misses, all listed in `TypeCorpusTest.knownMisses`:

| Miss | Expected | Got | Cause |
|---|---|---|---|
| 7 prose captures with a colon (screenshot, "Idea: …", "Note to self: …", "Thought: …", "Reminder: …", "Recipe idea: …", "Why the build failed: …") | Note | List | uncapped `colonList` |
| "write the quarterly report and send it to Priya" | Task | List | `plainList` takes two verb-led parts as steps |
| "make a Factorio like factory, with artists, designers and modellers" | Task | List | `plainList` accepts "with artists" as an item |
| "Sunday roast: lamb, potatoes, carrots and gravy" | List | Project | `dateOrTimePhrase` matches the bare "Sun" in the title |
| "Shopping" with `- ` bullet lines | List | Note | line breaks and bullets are not a marker |
| "Notes from the call" with `- ` bullet lines | List | Note | same (a bullet block is a list by its marker) |
| "Book recommendation from Ana: Piranesi" | Note | Task | leading verb "book" is a noun here; not a list problem |
| "weekly review on Friday" | Routine | Goal | `weekly` alone is not a cadence; not a list problem |

## Candidate signals

Measured over all 49 rows with "is a List or Project" as the positive class (16 rows). Each signal is applied as an extra
condition on the current verdict (S5 stands alone). Prototype only, not committed.

| Signal | TP | FP | FN | Precision | Recall | Notes |
|---|---|---|---|---|---|---|
| current rules | 14 | 9 | 2 | 0.61 | 0.88 | |
| S1 each item ≤ 4 words | 12 | 3 | 4 | 0.80 | 0.75 | loses "…book van and change address by 20 February" unless the trailing date/place is cut first, as `listEnd` already does |
| S2 at least 3 items | 14 | 4 | 2 | 0.78 | 0.88 | cannot see the screenshot: it has 4 items |
| S3a no clause opener ("instead of", "such as", "to", "so", "but", "then", "maybe", "with", "that", "which", "because", "and", "or", "if", "when", "while", "like") | 14 | 3 | 2 | 0.82 | 0.88 | |
| S3b S3a plus no finite-verb word ("is", "are", "was", "said", "should", "still", …) | 14 | 1 | 2 | 0.93 | 0.88 | word lists are brittle; one miss left |
| S4 title before the colon ≤ 3 words | 14 | 8 | 2 | 0.64 | 0.88 | nearly useless alone: prose titles are short too |
| S5 line breaks or bullets only | 2 | 0 | 14 | 1.00 | 0.13 | perfect but rare; a good override, not a detector |
| S6 items cover ≥ 70% of the words | 6 | 8 | 10 | 0.43 | 0.38 | worst: prose is mostly "items", true lists have a title |
| **Recommended set (below)** | 16 | 0 | 0 | 1.00 | 1.00 | |

## Recommendation

A capture is a List (Project when a date or time phrase follows the title) if either:

1. **Explicit marker:** 3 or more non-empty lines where every line after the first starts with `-`, `•` or `*`. The first line is the title, the rest are the steps. Wins over everything below.
2. **Colon list:** a title before the first `:` of **at most 3 words**, at least **2** items after it (split on `,` and `and`), every item **at most 4 words** (after cutting a trailing "by Friday"/"on 12 March" with `listEnd`, as `plainList` does), and **no item starting with a clause opener** (S3a's words).
3. **Verb list:** `plainList` as today (leading verb, items ≤ 4 words) but with **at least 3 items always** (today two verb-led parts are enough), and the same clause-opener check on every item.

Also: look for the date phrase only after the colon (or in the whole text when there is no colon), so a title like "Sunday roast" is not a date.

Everything else falls through to the routine, goal, task and note rules unchanged. S3b's finite-verb list is left out: S3a plus the 4-word cap already gets the corpus to 100%, and a verb list is a maintenance cost.
Prototype result: list detection P = 1.00, R = 1.00; types right **35 → 47 of 49**. The two left are not list problems ("Book recommendation from Ana: Piranesi", "weekly review on Friday"). (The prototype's "after" types used the real rules for non-lists, with `,` and `and` blanked so `listRule` cannot fire; it is a close approximation, not the final code.)
Under these rules the screenshot's text is a Note (the first item "Instead of a board" opens with "instead of", and an item exceeds 4 words), and `Groceries: milk, eggs and bread` stays a List with the steps Milk, Eggs, Bread.

### Caveat on overfitting

The corpus is 49 hand-written rows, and the thresholds were fitted by looking at it. 100% on it does not mean 100% in use. The 3-word title and 4-word item caps are guesses that happen to work here; the follow-up should add rows before it locks them in.

## Should Groq have a say in the type?

No key was available locally (`local.properties` does not exist), so Groq's own `type` field was not compared on the corpus. Reasoning from the prompt and code instead: the prompt (`GroqClient.kt:141-157`) already tells the model the app decides the type, and the merge already ignores its `type`. It could only be consulted for the borderline cases, and a model that makes "prose with a colon" a list of clauses is not better than the rules at this.

Recommendation: **type stays fully deterministic and on-device**. Groq keeps supplying what rules cannot: a clean `title`, `sub_items` (only for types that have steps), the `deadline` and the `completion_condition`. One thing to watch: as this spike shows, Groq's `sub_items` are what the card shows, so once a Note stops being a List the model's steps are dropped (`hasSteps` is false for Notes), which is the intended result.

## Follow-up ticket (ready to file)

**Title:** Stop prose with a colon becoming a List (3 pt)

**Scope:** `core/src/main/kotlin/com/juice/core/intake/RulesFallback.kt`, `core/src/test/kotlin/com/juice/core/intake/IntakeTest.kt`, and `TypeCorpusTest.kt` (flip the rows out of `knownMisses`).

**Change:** apply the recommended rule set above to `colonList`, `plainList` and the date check in `listRule`, and add the bullet-block marker.

**Acceptance criteria:**
- Given `formic factories: instead of a board, make a Factorio like factory … such as artists, designers and modellers`, when classified, then the type is Note.
- Given `Groceries: milk, eggs and bread`, when classified, then it is a List with steps Milk, Eggs and Bread; `get tape, rope and boxes` is still a List; `Launch: write copy, record demo and ship by Friday` is still a Project.
- Given `Sunday roast: lamb, potatoes, carrots and gravy`, when classified, then it is a List, not a Project.
- Given a title line followed by 2 or more `-` bullet lines, when classified, then it is a List with the bullets as steps.
- Given `write the quarterly report and send it to Priya` or `make a Factorio like factory, with artists, designers and modellers`, when classified, then it is a Task.
- Given `TypeCorpusTest`, when it runs, then every row but "Book recommendation from Ana: Piranesi" and "weekly review on Friday" gets its expected type, and those two stay in `knownMisses`.
- `./gradlew :core:test` and `./gradlew :core:koverVerify` pass, and the AGENTS.md determinism grep prints nothing.

## Outcome (applied)

The recommendation is in `RulesFallback`. `TypeCorpusTest` now has 64 rows, 15 of them held out (written after the thresholds were set); all
but the two non-list misses ("Book recommendation from Ana: Piranesi", "weekly review on Friday") get their expected type. The held-out rows
exposed one thing the list rules do not fix: a bare weekday or day word anywhere in a capture (e.g. "Monday standup: …", a book titled "…the Sun")
is still a date, so those become a Goal or Project.
