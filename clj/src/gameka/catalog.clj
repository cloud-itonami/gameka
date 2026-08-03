(ns gameka.catalog
  "Reading this studio's own catalog off disk: the gameSpecs in `specs/` and
  the runtime templates in `runtime/`.

  Path resolution is explicit because two callers disagree about cwd: the
  graph server runs from `clj/`, and the production loop runs a channel's
  producer with cwd = the repo root (ADR-2800002700). `GAMEKA_ROOT` wins when
  set; otherwise the first ancestor of cwd that actually contains a `specs/`
  directory is used, so neither caller has to know about the other."
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn- has-specs? [^java.io.File d]
  (and d (.isDirectory (io/file d "specs"))))

(defn root
  "Repo root — the directory holding `specs/` and `runtime/`."
  []
  (let [env (System/getenv "GAMEKA_ROOT")]
    (if (and env (not (str/blank? env)))
      (io/file env)
      (loop [d (.getAbsoluteFile (io/file "."))]
        (cond
          (nil? d) (io/file ".")
          (has-specs? d) d
          :else (recur (.getParentFile d)))))))

(defn spec-files
  "Every `*.gamespec.edn` in the catalog, sorted by name so a run order is
  stable across machines."
  ([] (spec-files (root)))
  ([r]
   (let [d (io/file r "specs")]
     (if (.isDirectory d)
       (->> (.listFiles d)
            (filter #(str/ends-with? (.getName ^java.io.File %) ".gamespec.edn"))
            (sort-by #(.getName ^java.io.File %))
            vec)
       []))))

(defn read-spec
  "Read one spec file. Returns nil on a malformed file rather than throwing —
  one broken spec must not make the whole catalog unreadable — but the caller
  can tell the two apart, because `specs` reports what it skipped."
  [f]
  (try (edn/read-string (slurp f))
       (catch Exception _ nil)))

(defn spec-id [spec]
  (or (:gamespec/id spec) (get spec "gamespec/id")))

(defn spec-slug [spec]
  (or (:gamespec/slug spec) (get spec "gamespec/slug")))

(defn specs
  "{:specs [...] :unreadable [path ...]}.

  `:unreadable` is reported, not logged: a catalog that silently drops a file
  reads as a smaller catalog, which is the same trap the production loop hit
  when an unreadable channel looked exhausted."
  ([] (specs (root)))
  ([r]
   (reduce (fn [acc f]
             (if-let [s (read-spec f)]
               (update acc :specs conj s)
               (update acc :unreadable conj (.getPath ^java.io.File f))))
           {:specs [] :unreadable []}
           (spec-files r))))

(defn find-spec
  "Look a spec up by `:gamespec/id`, by slug, or by an id with the version
  suffix stripped — proposals carry `\"<slug>-v1\"` and the catalog file may
  be keyed either way."
  ([id] (find-spec (root) id))
  ([r id]
   (let [want (str/trim (str id))
         bare (str/replace want #"-v\d+$" "")]
     (->> (:specs (specs r))
          (filter (fn [s]
                    (let [sid (str (spec-id s)) slug (str (spec-slug s))]
                      (or (= sid want) (= slug want)
                          (= (str/replace sid #"-v\d+$" "") bare)
                          (= slug bare)))))
          first))))

(defn runtime-template
  "The `.clj.tmpl` a spec's `:gamespec/runtime` names. nil when absent — a
  missing template is a degraded build, not an exception."
  ([spec] (runtime-template (root) spec))
  ([r spec]
   (let [n (or (:gamespec/runtime spec) (get spec "gamespec/runtime") "survivors-runtime")
         base (str/replace (str n) #"-runtime$" "")
         f (io/file r "runtime" (str base ".clj.tmpl"))]
     (when (.isFile f) (slurp f)))))
