(ns gameka.build-test
  "Against this studio's own catalog — not a fixture. The bug these tests
  exist to prevent was invisible precisely because nothing ever read a real
  spec."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [gameka.build :as build]
            [gameka.catalog :as catalog]
            [gameka.graphs.registry :as reg]))

(def zombie (delay (catalog/find-spec "survivors-zombie-v1")))
(def mall (delay (catalog/find-spec "survivors-zombie-mall")))

(deftest the-catalog-is-readable
  (let [{:keys [specs unreadable]} (catalog/specs)]
    (is (seq specs) "specs/ holds at least one gamespec")
    (is (= [] unreadable) "no spec in the catalog fails to parse")
    (testing "a spec resolves by id and by slug"
      (is (some? @zombie))
      (is (some? @mall))
      (is (= "survivors-zombie-v1" (catalog/spec-id @zombie))))))

(deftest shipped-specs-are-checked
  ;; Two findings came out of the first run of this check against the real
  ;; catalog, which had never been validated because nothing read it:
  ;;
  ;; 1. survivors-zombie.gamespec.edn carried `:boss/finale true` AND
  ;;    `:boss/finale {...}` on the same map — a duplicate key, so the file
  ;;    failed `clojure.edn/read-string` outright. Fixed in this change.
  ;; 2. survivors-zombie-mall has `perfume-torch` evolving to
  ;;    "aerosol-inferno", a weapon that is not in its weapons list. NOT
  ;;    fixed here: inventing that weapon's damage, cooldown and radius is a
  ;;    design decision, not a typo repair. The consequence is exactly what
  ;;    the system is for — jsic-3914's governor holds a build of that title
  ;;    until a human decides.
  ;;
  ;; Pinned rather than tolerated: if the mall spec is fixed, or a new spec
  ;; arrives broken, this test says so.
  (let [by-id (into {} (map (juxt catalog/spec-id #(build/design-report %)))
                    (:specs (catalog/specs)))]
    (is (= 0 (:problem-count (get by-id "survivors-zombie-v1"))))
    (is (= [{:kind :evolves-to-unknown-weapon
             :from "perfume-torch" :ref "aerosol-inferno"}]
           (:problems (get by-id "survivors-zombie-mall-v1"))))
    (testing "no spec has an unknown problem kind"
      (is (= #{:evolves-to-unknown-weapon}
             (set (mapcat #(map :kind (:problems %)) (vals by-id))))))))

(deftest the-build-is-a-function-of-the-spec
  (let [a (build/build-spec @zombie)
        b (build/build-spec @mall)]
    (is (= "sources_ready" (:status a)))
    (is (= "sources_ready" (:status b)))
    (testing "two different designs no longer produce byte-identical sources"
      (is (not= (:script a) (:script b)))
      (is (not= (:scriptCid a) (:scriptCid b))))
    (testing "the constants come from the spec, not from the renderer"
      (let [max-alive (get-in @zombie [:mechanic :waves :waves/max-alive])]
        (is (re-find (re-pattern (str "\\(def max-alive\\s+" max-alive "\\)")) (:script a)))
        (is (not (re-find #"\(def max-alive\s+200\)" (:script a)))
            "the old hardcoded 200 must not appear unless the spec says 200")
        (testing "and the spawn period is the spec's interval in ticks, not 20"
          (is (re-find #"\(def spawn-period\s+72\)" (:script a))))))
    (testing "every placeholder in the template was filled"
      (is (= [] (:unfilled a)))
      (is (not (str/includes? (:script a) "{{")))))
  (testing "the same spec twice is the same build — the CID is a real name"
    (is (= (:scriptCid (build/build-spec @zombie))
           (:scriptCid (build/build-spec @zombie))))))

(deftest knobs-are-traceable-to-the-design
  (let [k (:knobs (build/build-spec @zombie))]
    (is (= (get-in @zombie [:mechanic :waves :waves/max-alive])
           (get-in k [:max-alive :value])))
    (testing "the fire cadence names the weapon it came from"
      (is (string? (get-in k [:fire-period :from])))
      (is (contains? (set (map :weapon/id (get-in @zombie [:mechanic :weapons])))
                     (get-in k [:fire-period :from]))))))

(deftest a-missing-template-is-not-a-ready-build
  (let [b (build/build-spec (assoc @zombie :gamespec/runtime "no-such-runtime"))]
    (is (= "no_template" (:status b)))
    (is (nil? (:script b)))
    (testing "the design report is still produced — a build can fail and still be reviewed"
      (is (= 0 (:problem-count (:design b)))))))

(deftest review-spec-answers-without-a-critic
  (let [h (:handler (reg/resolve-entry (reg/build) "review_spec"))
        out (h {:specId "survivors-zombie-v1"} nil)]
    (is (= "done" (:status out)))
    (is (= 0 (:problemCount out)))
    (is (= "consistent" (:verdict out)))
    (testing "there is no score — that would be the fabrication this repo refuses"
      (is (not (contains? out :score)))))
  (testing "an unknown spec is an error, not an empty review"
    (is (= "error" (:status ((:handler (reg/resolve-entry (reg/build) "review_spec"))
                             {:specId "nope"} nil))))))

(deftest generate-game-reports-what-it-built
  (let [h (:handler (reg/resolve-entry (reg/build) "generate_game"))
        out (h {:specId "survivors-zombie-v1"} nil)]
    (is (= "done" (:status out)))
    (is (= "sources_ready" (:buildStatus out)))
    (is (str/starts-with? (:scriptCid out) "b"))
    (testing "it no longer claims a wasm artifact it did not produce"
      (is (= "" (:wasmCid out)))
      (is (= 0 (:wasmSize out))))))
