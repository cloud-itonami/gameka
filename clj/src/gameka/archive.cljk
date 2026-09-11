(ns gameka.archive
  "JVM I/O for kotobase.net's content-addressed archive — the storage plane of
  a gameka build. Pure CID math lives in `gameka.cid`.

  Why a build needs one: a title's generated audio and its rendered runtime
  source only ever existed under `target/gameka/<id>/`, so a build could not
  be reproduced, re-scored or audited after the fact — and unlike a video,
  a game's assets are *loaded at play time*, which means an artifact that
  cannot be fetched later is a title that stops working, not just one that
  cannot be re-cut. Naming every artifact by the sha2-256 of its own bytes
  makes a build reproducible from CIDs alone.

  Idempotent by construction: the same bytes are the same CID, so re-storing
  is a no-op and a re-run that produced identical output costs nothing.

  Auth: env `KOTOBASE_ARCHIVE_TOKEN` (bearer). Absent -> every put returns nil
  with a warning and the build continues; storage is a mirror, never a gate."
  (:require [clojure.java.io :as io]
            [kotoba.lang.text :as str]
            [gameka.cid :as cid])
  (:import [java.net URI]
           [java.net.http HttpClient HttpRequest HttpRequest$BodyPublishers
            HttpResponse$BodyHandlers]
           [java.nio.file Files]
           [java.security MessageDigest]
           [java.time Duration]))

(def default-host "https://kotobase.net")

(def content-types
  "Build artifact kinds -> the content-type stored alongside the bytes."
  {:wav "audio/wav"
   :png "image/png"
   :clj "text/plain"
   :edn "text/plain"
   :wasm "application/wasm"})

(defn token []
  (let [t (str/trim (str (or (System/getenv "KOTOBASE_ARCHIVE_TOKEN") "")))]
    (when-not (str/blank? t) t)))

(defn host []
  (let [s (str/trim (str (or (System/getenv "KOTOBASE_ARCHIVE_HOST") "")))]
    (if (str/blank? s) default-host (str/replace s #"/+$" ""))))

(defn archive-url [h c] (str (str/replace (str h) #"/+$" "") "/ipfs/" c))

(defn- warn [& xs]
  (binding [*out* *err*] (apply println "[kotobase]" xs)))

(defn file-bytes ^bytes [f] (Files/readAllBytes (.toPath (io/file f))))

(defn cid-of
  "File -> its raw CIDv1. This is the name the artifact will have forever."
  [f]
  (cid/raw-cid (map #(bit-and % 0xff)
                    (.digest (MessageDigest/getInstance "SHA-256") (file-bytes f)))))

(defn put!
  "Store a file under its own CID. Returns {:cid :url} on success (including
  when the archive already had it — 409 is a hit, not a failure), nil
  otherwise.

  The server re-derives the digest from the body and rejects a mismatch with
  422, so a wrong CID here can never mislabel stored bytes."
  [f kind]
  (let [file (io/file f)]
    (cond
      (not (.exists file)) (do (warn "no such file:" (str f)) nil)
      (nil? (token)) (do (warn "skipped" (.getName file)
                               "— no KOTOBASE_ARCHIVE_TOKEN in env")
                         nil)
      :else
      (try
        (let [c (cid-of file)
              url (archive-url (host) c)
              req (-> (HttpRequest/newBuilder (URI/create url))
                      (.timeout (Duration/ofMinutes 10))
                      (.header "authorization" (str "Bearer " (token)))
                      (.header "content-type" (get content-types kind
                                                   "application/octet-stream"))
                      (.PUT (HttpRequest$BodyPublishers/ofFile (.toPath file)))
                      (.build))
              resp (.send (HttpClient/newHttpClient) req (HttpResponse$BodyHandlers/ofString))
              status (.statusCode resp)]
          (if (or (<= 200 status 299) (= 409 status))
            {:cid c :url url}
            (do (warn "PUT" url "->" status) nil)))
        (catch Exception e (warn "PUT failed:" (.getMessage e)) nil)))))

(defn put-all!
  "Store a seq of [file kind] pairs. Returns the successful {:cid :url :file}
  maps, skipping (and warning about) the rest — a partial mirror is reported
  honestly rather than failing the build."
  [pairs]
  (vec (keep (fn [[f kind]]
               (when-let [r (put! f kind)]
                 (assoc r :file (str f))))
             pairs)))
