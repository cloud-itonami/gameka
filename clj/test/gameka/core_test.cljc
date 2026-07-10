(ns gameka.core-test
  (:require [clojure.edn :as edn]
            [clojure.test :refer [deftest is]]
            [gameka.cid :as cid]
            [gameka.graphs.registry :as reg]))

;; clj/langgraph.edn was datomized (Phase 3 EDN datomize fanout): its top-level
;; map is now wrapped as Datomic/Datascript tx-data ([{:db/id -1 :langgraph/... }]),
;; with non-scalar values (like :graphs) pr-str'd into a blob string. Reconstitute
;; the original un-namespaced map here so the assertion below is unchanged.
(defn- unblob [v]
  (if (string? v)
    (try (let [parsed (edn/read-string v)] (if (coll? parsed) parsed v))
         (catch #?(:clj Exception :cljs :default) _ v))
    v))

(defn- reconstitute-entity [tx-data]
  (into {} (map (fn [[k v]] [(keyword (name k)) (unblob v)]))
        (dissoc (first tx-data) :db/id)))

(deftest registry-surface
  (let [r (reg/build)]
    (is (= #{"ai.gftd.apps.gameka.health"
             "ai.gftd.apps.gameka.generate"
             "ai.gftd.apps.gameka.proposeSpec"
             "ai.gftd.gameka.proposeGame"
             "ai.gftd.gameka.generateGame"
             "ai.gftd.gameka.playtestGame"
             "ai.gftd.gameka.publishGame"}
           (set (keys r))))
    (doseq [id ["health" "generate" "propose_spec" "generate_game" "playtest_game" "publish_game"]]
      (is (reg/resolve-entry r id)))))

(deftest manifest-matches-registry
  (let [tx-data (edn/read-string (slurp "langgraph.edn"))
        manifest (reconstitute-entity tx-data)]
    (is (= (set (keys (reg/build))) (set (keys (:graphs manifest)))))))

(deftest cid-is-deterministic
  (let [a (cid/spec-cid "{:weapons []}" "{:scene/biome \"x\"}")
        b (cid/spec-cid "{:weapons []}" "{:scene/biome \"x\"}")
        c (cid/spec-cid "{:weapons [1]}" "{:scene/biome \"x\"}")]
    (is (= a b))
    (is (not= a c))
    (is (and (.startsWith a "b") (> (count a) 40)))))

(deftest generate-scaffold-is-honest
  (let [h (:handler (reg/resolve-entry (reg/build) "generate"))]
    (is (= "error" (:status (h {} nil))))
    (let [out (h {:prompt "a cozy pixel-art town"} nil)]
      (is (= "not-implemented" (:status out)))
      (is (= "" (:blobCid out))))))

(deftest studio-loop-graphs
  (let [r (reg/build)
        prop ((:handler (reg/resolve-entry r "ai.gftd.gameka.proposeGame")) {:brief "zombie mall survivors"} nil)
        gen ((:handler (reg/resolve-entry r "generate_game")) {:specId (:specId prop)} nil)
        qa ((:handler (reg/resolve-entry r "playtest_game")) {:specId (:specId prop)} nil)
        pub-missing ((:handler (reg/resolve-entry r "publish_game")) {:specId (:specId prop)} nil)
        pub ((:handler (reg/resolve-entry r "publish_game")) {:specId (:specId prop) :artifactId (:artifactId gen)} nil)]
    (is (= "rejected" (:status prop)))
    (is (= "done" (:status gen)))
    (is (= "sources_ready" (:buildStatus gen)))
    (is (.contains (:script gen) "max-alive"))
    (is (= "done" (:status qa)))
    (is (= false (:publish qa)))
    (is (= "error" (:status pub-missing)))
    (is (= "done" (:status pub)))
    (is (= "https://gamers.gftd.ai/play/zombie-mall-survivors" (:playUrl pub)))))
