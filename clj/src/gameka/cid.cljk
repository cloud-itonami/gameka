(ns gameka.cid
  "Content addressing for gameka artifacts — raw CIDv1 over sha2-256.

  This used to compute `\"b\" + base32(sha256)` and call it a CIDv1. It is
  not one: a raw CIDv1 is multibase `b` over the multihash prefix
  `0x01 0x55 0x12 0x20` followed by the digest, and without that prefix the
  name kotobase's `PUT /ipfs/:cid` re-derives never matches — so every
  artifact this repo named was unstorable under its own id. The cljs branch
  was worse: `(str \"bcljs\" (hash s))` is not a hash of the content at all.

  Both are now delegated to `murakumo.generation.digest`, which is the same
  implementation the generation client verifies downloads against and the
  same one kotobase names objects by. One name across compute and storage;
  no second base32 to drift."
  (:require [kotoba.lang.text :as str]
            [murakumo.generation.digest :as digest]))

(def raw-cid
  "sha2-256 digest bytes -> raw CIDv1."
  digest/raw-cid)

(def sha256-hex->cid
  "sha2-256 hex -> raw CIDv1. This is the form murakumo states artifact
  integrity in (`contentHash: \"sha256:<hex>\"`), so a generated artifact
  needs no re-hash before it is stored."
  digest/sha256-hex->cid)

(defn- sha256-bytes [s]
  #?(:clj (.digest (java.security.MessageDigest/getInstance "SHA-256")
                   (.getBytes (str s) "UTF-8"))
     :cljs (throw (ex-info "gameka.cid: hashing a string needs a host digest under cljs"
                           {:input-length (count (str s))}))))

(defn cidv1-b32-sha256
  "String -> raw CIDv1 of its UTF-8 bytes.

  JVM only. The cljs arm throws rather than returning the old fake, because
  a wrong CID does not fail loudly: it stores correct bytes under a name that
  does not describe them, or 422s much later with no clue why."
  [s]
  (raw-cid (sha256-bytes s)))

(defn spec-cid
  "The design's own name: the CID of its mechanic and scene, so two proposals
  differing only in prose share one build."
  [mechanic scene]
  (cidv1-b32-sha256 (str (str/trim (str mechanic)) "\n" (str/trim (str scene)))))
