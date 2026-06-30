(ns gameka.graphs.registry
  (:require [clojure.string :as str]
            [gameka.cid :as cid]))

(defn- input-value [m & ks] (some #(get m %) ks))

(defn- slugify [s]
  (let [x (-> (or s "game") str str/lower-case
              (str/replace #"[^a-z0-9]+" "-")
              (str/replace #"(^-|-$)" ""))]
    (if (seq x) x "game")))

(defn- title-from [brief]
  (let [s (str/trim (str brief))]
    (if (seq s) (str/upper-case (subs s 0 (min 40 (count s)))) "UNTITLED GAME")))

(defn- health [_ _]
  {:ok true :status "ok" :app "gameka.gftd.ai" :impl "clj"})

(defn- generate [input _]
  (let [prompt (str/trim (str (or (input-value input :prompt "prompt") "")))]
    (if (seq prompt)
      {:blobCid "" :status "not-implemented" :renderMs 0
       :error "gameka generate is a scaffold; no game-asset generator backend yet"}
      {:blobCid "" :status "error" :renderMs 0 :error "prompt is required"})))

(defn- propose-spec [input _]
  (let [brief (str/trim (str (or (input-value input :brief "brief") "")))]
    (if-not (seq brief)
      {:status "error" :error "brief is required"}
      (let [slug (slugify brief)
            mechanic "{:waves {:waves/max-alive 200 :waves/base-spawn-interval-ms 320} :enemies [{:enemy/id \"z\" :enemy/speed 120}] :weapons [{:weapon/id \"p\" :weapon/cooldown-ms 480}]}"
            scene (str "{:scene/biome \"" slug "\"}")
            spec-id (str slug "-v1")]
        {:status "rejected"
         :specId spec-id
         :slug slug
         :title (title-from brief)
         :brief brief
         :genre (or (input-value input :genre "genre") "survivors")
         :mechanic mechanic
         :scene scene
         :score 0
         :modelId ""
         :iteration 0
         :rationale "deterministic CLJ fallback; no LLM critic score fabricated"
         :cid (cid/spec-cid mechanic scene)}))))

(defn- generate-game [input _]
  (let [spec-id (str/trim (str (or (input-value input :specId :spec-id "specId" "spec_id") "")))]
    (if-not (seq spec-id)
      {:status "error" :error "specId is required"}
      (let [slug (str/replace spec-id #"-v\d+$" "")
            script (str "(ns gameka.generated." (str/replace slug #"-" "_") ")\n"
                        "(def max-alive 200)\n(def enemy-speed (f32 120))\n"
                        "(def spawn-period 20)\n(def fire-period 30)\n"
                        "(defn tick [] :sources-ready)\n")]
        {:status "done"
         :artifactId (str "art-" spec-id)
         :slug slug
         :uri (str "at://did:web:gameka.gftd.ai/ai.gftd.gameka.buildArtifact/art-" spec-id)
         :wasmCid (cid/cidv1-b32-sha256 script)
         :wasmSize 0
         :wasmUrl ""
         :jsUrl ""
         :buildStatus "sources_ready"
         :scriptCid (cid/cidv1-b32-sha256 script)
         :script script}))))

(defn- playtest-game [input _]
  (let [spec-id (str/trim (str (or (input-value input :specId :spec-id "specId" "spec_id") "")))
        iteration (long (or (input-value input :iteration "iteration") 0))]
    (if-not (seq spec-id)
      {:status "error" :error "specId is required"}
      {:status "done"
       :qaId (str "qa-" spec-id "-i" iteration)
       :uri (str "at://did:web:gameka.gftd.ai/ai.gftd.gameka.gameQa/qa-" spec-id "-i" iteration)
       :visualScore 0
       :perfScore 0
       :combinedScore 0
       :iteration iteration
       :publish false
       :outcome "revise"
       :issuesJson "[{\"category\":\"qa\",\"severity\":\"info\",\"description\":\"static CLJ scaffold; no headless runner\"}]"})))

(defn- publish-game [input _]
  (let [spec-id (str/trim (str (or (input-value input :specId :spec-id "specId" "spec_id") "")))
        artifact-id (str/trim (str (or (input-value input :artifactId :artifact-id "artifactId" "artifact_id") "")))]
    (cond
      (not (seq spec-id)) {:status "error" :error "specId is required"}
      (not (seq artifact-id)) {:status "error" :error "artifactId is required"}
      :else
      (let [slug (str/replace spec-id #"-v\d+$" "")
            title-id (str "ttl-" slug "-v1")
            sub-did (str "did:web:gameka.gftd.ai:game:" slug)]
        {:status "done"
         :titleId title-id
         :uri (str "at://" sub-did "/ai.gftd.gameka.gameTitle/" title-id)
         :subDid sub-did
         :slug slug
         :playUrl (str "https://gamers.gftd.ai/play/" slug)
         :postUri ""
         :version "v1"}))))

(defn build []
  {"ai.gftd.apps.gameka.health" {:assistant :health :handler health}
   "ai.gftd.apps.gameka.generate" {:assistant :generate :handler generate}
   "ai.gftd.apps.gameka.proposeSpec" {:assistant :propose_spec :handler propose-spec}
   "ai.gftd.gameka.proposeGame" {:assistant :propose_game :handler propose-spec}
   "ai.gftd.gameka.generateGame" {:assistant :generate_game :handler generate-game}
   "ai.gftd.gameka.playtestGame" {:assistant :playtest_game :handler playtest-game}
   "ai.gftd.gameka.publishGame" {:assistant :publish_game :handler publish-game}})

(defn nsids [registry] (vec (sort (keys registry))))

(defn resolve-entry [registry ident]
  (or (get registry ident)
      (some (fn [[_ entry]] (when (= (name (:assistant entry)) ident) entry)) registry)))
