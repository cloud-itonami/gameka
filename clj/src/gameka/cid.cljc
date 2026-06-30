(ns gameka.cid
  (:require [clojure.string :as str]))

(def ^:private alphabet "abcdefghijklmnopqrstuvwxyz234567")

(defn- bytes->base32 [bytes]
  (let [bits (apply str (map #(let [s (Integer/toBinaryString (bit-and % 0xff))]
                                (str (apply str (repeat (- 8 (count s)) "0")) s))
                             bytes))]
    (apply str (for [chunk (partition-all 5 bits)
                     :let [padded (apply str (concat chunk (repeat (- 5 (count chunk)) \0)))
                           idx (Integer/parseInt padded 2)]]
                 (.charAt alphabet idx)))))

(defn cidv1-b32-sha256 [s]
  #?(:clj
     (let [md (java.security.MessageDigest/getInstance "SHA-256")
           bs (.digest md (.getBytes (str s) "UTF-8"))]
       (str "b" (bytes->base32 bs)))
     :cljs
     (str "bcljs" (hash s))))

(defn spec-cid [mechanic scene]
  (cidv1-b32-sha256 (str (str/trim (str mechanic)) "\n" (str/trim (str scene)))))
