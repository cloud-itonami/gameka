# gameka (ゲーム家)

**The business layer of game making**: this studio's catalog of gameSpecs, the
build that turns one into playable runtime sources, the audio that stops it
being silent, and the graph server that fronts all of it.

Third of the three layers ADR-2607023000 defines for every creative `-ka`:

| layer | where | what it holds |
|---|---|---|
| 技芸 craft | [`kotoba-lang/game-production`](https://github.com/kotoba-lang/game-production) | the gameSpec vocabulary: consistency checks, balance arithmetic, runtime knobs, audio cue plans |
| 職能 occupation | [`cloud-itonami/cloud-itonami-jsic-3914`](https://github.com/cloud-itonami/cloud-itonami-jsic-3914) | ゲームソフトウェア業 blueprint: GameOpsAdvisor ⊣ GameProductionGovernor ⊣ ledger |
| 商売 business | **this repo** | catalog, templates, generation legs, serving, keys |

## What a build is

```bash
kbb -M:produce --spec survivors-zombie-v1 --out target/gameka --legs legs.edn
```

1. read the spec from `specs/`
2. check it and cost it (`game-production.spec` / `.balance`)
3. render runtime source from the spec's own template in `runtime/`
4. render the audio the build needs on **murakumo.cloud**, cue by cue
5. mirror every artifact to **kotobase.net** under its own CID

Each step reports what it *did*, not whether it threw. A missing token is a
normal state: the build completes, the audio legs come back `:placeholder`,
and the production loop holds it instead of shipping a silent game.

## What this repo used to claim, and what changed

`generate_game` emitted the same four constants for **every** spec —

```clojure
(def max-alive 200)   (def enemy-speed (f32 120))
(def spawn-period 20) (def fire-period 30)
```

— with a content address computed over that constant string, so two entirely
different games produced byte-identical artifacts with the same CID, while
reporting `:buildStatus "sources_ready"`. It now renders the spec's own
template with the spec's own numbers (`max-alive 400`, `spawn-period 72`
ticks from the spec's 1200 ms interval, and so on), and the CID is over that.

Three further things the first real run of that code surfaced:

- **`specs/survivors-zombie.gamespec.edn` had never been readable.** It carried
  both `:boss/finale true` and `:boss/finale {...}` on the same map — a
  duplicate key, so `clojure.edn/read-string` refused the whole file. Fixed
  here. Nothing had noticed because nothing had ever loaded a spec.
- **`survivors-zombie-mall` has a dangling evolution**: `perfume-torch`
  evolves to `aerosol-inferno`, which is not in its weapons list. *Not* fixed
  here — inventing that weapon's damage, cooldown and radius is a design
  decision, not a typo repair. The consequence is the system working: jsic-3914's
  governor holds a build of that title until a human decides.
- **`gameka.cid` was not producing CIDs.** It computed `"b" + base32(sha256)`
  without the `0x01 0x55 0x12 0x20` multihash prefix, so the name kotobase
  re-derives never matched and every artifact was unstorable under its own id.
  The cljs branch returned `(str "bcljs" (hash s))`, which is not a hash of the
  content at all. Both now delegate to `murakumo.generation.digest`.

`propose_spec` still returns `:status "rejected"` with `:score 0` and *"no LLM
critic score fabricated"*. That was right and is kept. What is new is
`ai.gftd.gameka.reviewSpec`, which answers the parts that need no critic —
consistency and whether the design has an endgame — and still offers no taste
score. `playtest_game` is still a scaffold and still says so: there is no
headless runner, and a fabricated visual score would be worse than a zero.

## Production loop

Registered as the `:gameka` channel in
[`kotoba-lang/loop-ka-production`](https://github.com/kotoba-lang/loop-ka-production)
(ADR-2800002700), which owns cadence, admission, verdict and evidence. The
producer reports `:video` per generated **audio cue** and `:voice []` — a game
has no narration — following the precedent `:ghosthacker` set for manga.

A game with silent weapons is not a release. The weapon cue is the only
feedback that auto-fire is working and the boss telegraph is the only warning
that a dodgeable attack is coming, so a placeholder cue set grades `:degraded`
and holds, exactly like a flat-colour card.

## Layout

```
specs/     gameSpecs — the running order, one build each
runtime/   .clj.tmpl runtime templates the knobs are rendered into
playtest/  static playtest pages
clj/       the graph server, build, and the murakumo/kotobase legs
```

```bash
cd clj && kbb -M:test && kbb -M:lint
```

Environment: `MURAKUMO_GENERATION_TOKEN` (scope `generation`),
`KOTOBASE_ARCHIVE_TOKEN`, optional `GAMEKA_ROOT`. Absent tokens degrade, never
fail.
