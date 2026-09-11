(ns gameka.build
  "A gameSpec becomes a build: checked, costed, and rendered into runtime
  source with the spec's own constants in it.

  What this replaces is worth naming, because it was the repo's central
  fiction. `generate-game` used to emit

      (def max-alive 200)
      (def enemy-speed (f32 120))
      (def spawn-period 20)
      (def fire-period 30)

  for **every** spec — the same four numbers whether the design said 400
  alive or 40, a 1200 ms spawn interval or 250. It returned
  `:buildStatus \"sources_ready\"` and a CID over that constant string, so
  two entirely different games produced byte-identical artifacts with the
  same content address. The pipeline reported success at every step.

  Now the constants come from `game-production.knobs` and the source comes
  from the spec's own runtime template, so a build is a function of the
  design. A build also carries the design report — problems and endgame — so
  a caller can refuse it; this namespace does not refuse on its own, because
  the decision of whether a flawed design may still be built belongs to the
  governor (`cloud-itonami-jsic-3914`), not to the renderer."
  (:require [game-production.balance :as balance]
            [game-production.knobs :as knobs]
            [game-production.spec :as gamespec]
            [gameka.catalog :as catalog]
            [gameka.cid :as cid]))

(def ^:const default-ticks-per-second knobs/default-ticks-per-second)

(defn design-report
  "The facts a build decision needs: consistency findings, the pressure the
  spec states, and whether any weapon can hold the terminal rate."
  [spec]
  (let [problems (gamespec/problems spec)
        enemies (balance/enemy-table spec)
        common (->> enemies (sort-by #(- (or (:spawn-weight %) 0))) first)
        required (when-let [id (:id common)] (balance/clear-rate-required-milli spec id))
        best (or (:crit-dps-milli (first (balance/weapon-table spec))) 0)]
    {:spec-id (catalog/spec-id spec)
     :problems problems
     :problem-count (count problems)
     :pressure (balance/pressure-bounds spec)
     :endgame (when (and required (pos? (:required-dps-milli required)))
                {:enemy (:id common)
                 :best-dps-milli best
                 :required-dps-milli (:required-dps-milli required)
                 :holdable? (>= best (:required-dps-milli required))})}))

(defn render-runtime
  "spec + template -> runtime source, with the knobs that produced it.

  `:unfilled` is passed through from `knobs/render`: a template that gained a
  placeholder this repo does not supply comes back named, rather than being
  emitted with a literal `{{...}}` that fails at compile time far from here."
  ([spec template] (render-runtime spec template default-ticks-per-second))
  ([spec template tps]
   (let [k (knobs/knobs spec tps)
         subs (knobs/substitutions spec k)
         {:keys [text unfilled complete?]} (knobs/render template subs)]
     {:source text
      :knobs k
      :substitutions subs
      :unfilled unfilled
      :complete? complete?})))

(defn build-spec
  "spec (+ optional template) -> a build result.

  `:status` is `\"sources_ready\"` only when a template was found and every
  placeholder was filled. Without a template the result is `\"no_template\"`
  and carries no source — the old code would have emitted its hardcoded
  string here and called it ready."
  ([spec] (build-spec spec (catalog/runtime-template spec) default-ticks-per-second))
  ([spec template] (build-spec spec template default-ticks-per-second))
  ([spec template tps]
   (let [design (design-report spec)
         id (or (catalog/spec-id spec) (catalog/spec-slug spec) "unknown")
         slug (or (catalog/spec-slug spec) id)]
     (if-not template
       {:status "no_template" :specId id :slug slug :design design}
       (let [{:keys [source knobs unfilled complete?]} (render-runtime spec template tps)]
         {:status (if complete? "sources_ready" "incomplete_template")
          :specId id
          :slug slug
          :artifactId (str "art-" id)
          :uri (str "at://did:web:gameka.gftd.ai/ai.gftd.gameka.buildArtifact/art-" id)
          :scriptCid (cid/cidv1-b32-sha256 source)
          :script source
          :knobs knobs
          :unfilled unfilled
          :design design})))))
