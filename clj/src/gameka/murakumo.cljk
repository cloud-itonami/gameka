(ns gameka.murakumo
  "gameka's binding of the shared murakumo generation client to this JVM host.

  Everything expensive to learn about the API — which host actually accepts a
  generation token, rebasing the advertised artifact URL, the accepted
  ranges, digest verification, cold-start retry, the poll schedule — lives in
  `murakumo.generation` / `murakumo.generation.client`, so this namespace
  supplies only the four host primitives that genuinely differ between the
  JVM and nbb: JSON over HTTP, download, file digest, sleep.

  A game needs the fleet's `sound` type twice: the bed (`sound_kind=music`,
  looping) and the cue set (`sound_kind=sfx`). It does not use `video` or
  `voice` — a survivors build has no narration and no moving footage, and
  requesting them to look busy would spend GPU time on artifacts nothing
  loads.

  Auth: env `MURAKUMO_GENERATION_TOKEN` (scope `generation`). Read here,
  passed down, never logged or written into an artifact. Its absence is a
  normal state, not an error: production degrades to a silent build and says
  so per leg."
  (:require [clojure.java.io :as io]
            [kotoba.lang.text :as str]
            [jsonista.core :as j]
            [murakumo.generation :as gen]
            [murakumo.generation.client :as client])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
            HttpResponse$BodyHandlers]
           [java.security MessageDigest]
           [java.nio.file Files]
           [java.time Duration]))

(def ^:private mapper (j/object-mapper {:decode-key-fn true}))

(defn token
  "Generation-scope bearer token, or nil."
  []
  (let [t (str/trim (str (or (System/getenv "MURAKUMO_GENERATION_TOKEN") "")))]
    (when-not (str/blank? t) t)))

(defn base-url [] (gen/endpoint (System/getenv "MURAKUMO_GENERATION_URL")))

(defn- http ^HttpClient []
  (-> (HttpClient/newBuilder) (.connectTimeout (Duration/ofSeconds 20)) (.build)))

(defn request!
  "method+url(+body map) -> {:status int :body map}. A transport failure comes
  back as status 0 rather than an exception, so the client's own reporting
  stays the single place a failure is explained."
  [method url body tok]
  (try
    (let [b (doto (HttpRequest/newBuilder (URI/create url))
              (.timeout (Duration/ofSeconds 60))
              (.header "authorization" (str "Bearer " tok)))
          _ (if body
              (doto b
                (.header "content-type" "application/json")
                (.method method (HttpRequest$BodyPublishers/ofString
                                 (j/write-value-as-string body))))
              (.method b method (HttpRequest$BodyPublishers/noBody)))
          resp (.send (http) (.build b) (HttpResponse$BodyHandlers/ofString))]
      {:status (.statusCode resp)
       :body (when (seq (.body resp))
               (try (j/read-value (.body resp) mapper) (catch Exception _ nil)))})
    (catch Exception e {:status 0 :error (.getMessage e)})))

(defn download! [url out tok]
  (try
    (let [b (doto (HttpRequest/newBuilder (URI/create url))
              (.timeout (Duration/ofMinutes 5)))
          _ (when tok (.header b "authorization" (str "Bearer " tok)))
          resp (.send (http) (.build b) (HttpResponse$BodyHandlers/ofInputStream))]
      (when (<= 200 (.statusCode resp) 299)
        (io/copy (.body resp) (io/file out))
        out))
    (catch Exception _ nil)))

(defn sha256-hex [f]
  (let [d (.digest (MessageDigest/getInstance "SHA-256")
                   (Files/readAllBytes (.toPath (io/file f))))]
    (apply str (map #(format "%02x" (bit-and % 0xff)) d))))

(defn opts
  "The injected seam, built once per call site."
  []
  {:base (base-url) :token (token)
   :request! request! :download! download! :sha256-hex sha256-hex
   :delete! (fn [p] (io/delete-file (io/file p) true))
   :sleep! (fn [ms] (Thread/sleep (long ms)))})

(defn ms->seconds
  "The client states duration in seconds; the craft lib states it in
  milliseconds, because a 700 ms telegraph is not expressible as whole
  seconds and rounding it would make the cue outlast its dodge window."
  [ms]
  (/ (double (long (or ms 0))) 1000.0))

(defn cue!
  "One `game-production.audio` cue -> a rendered wav at `out`, or nil.

  The cue's own duration goes straight through: a telegraph is exactly its
  dodge window. `:cue/loop?` reaches the fleet only for the bed — `music!`
  sets `loop` itself and `sfx!` has no loop parameter, so a looping sfx (a
  chainsaw's sustained grind) comes back as a one-shot the runtime must
  repeat. That is a real limitation of the current sound function, recorded
  here rather than papered over.

  Models are not pinned — omitting `:model` lets murakumo resolve the
  function's own default, so a fleet-side swap reaches this repo without a
  release (ADR-2607173100)."
  [{:cue/keys [kind prompt duration-ms]} out]
  (let [req {:prompt prompt :seconds (ms->seconds duration-ms)}]
    (if (= :music kind)
      (client/music! req (str out) (opts))
      (client/sfx! req (str out) (opts)))))
