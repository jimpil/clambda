(ns clambda.line-streams
  (:require [clambda.core :as core]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (java.nio.file Files Paths)
           (java.net URL URI)
           (java.io File BufferedWriter)
           (clojure.lang IReduceInit)))

(defprotocol LocalPath
  (local-path [this]))

(extend-protocol LocalPath
  String
  (local-path [s]
    (Paths/get s (make-array String 0)))

  File
  (local-path [file]
    (.toPath file))
  URL
  (local-path [url]
    (if (= "file" (.getProtocol url))
      (Paths/get (.toURI url))
      (throw (IllegalArgumentException. "Non-local URL!"))))
  URI
  (local-path [uri]
    (if (= "file" (.getScheme uri))
      (Paths/get uri)
      (throw (IllegalArgumentException. "Non-local URI!"))))
  )
;;===========================================================
(defn stream-lines
  "Returns an `eduction` encapsulating the
   computation for processing each line of
   <in> (anything compatible with `io/reader`,
   or something already reducible) with <f>."
  [f in]
  (eduction (map f)
            (if (instance? IReduceInit in)
              in
              (core/lines-reducible (io/reader in)))))

(defn pstream-lines
  "Parallel version of `stream-lines` that only works on local files.
   Relies on the parallel Stream returned by `Files/lines`,
   and therefore it requires at least Java-9 for the
   expected/intuitive performance improvements.

   <in> should be an instance of java.io.File, java.net.URL/URI, or simply a String.

   <combine-f> is the fn that will combine the results from the
   various threads, so it depends on the transducing context in which
   the returned `eduction` will be eventually used. For example,
   if the end-goal is collecting everything, then use `into` as
   the <combine-f>, and `conj` as the reducing-f.

   See `core/stream-into` for an example of a collecting context,
   and `core/stream-some` for an example of a short-circuiting one.

   Files greater than 2GB cannot be processed this way due to JVM array
   indexing using ints. Consider splitting huge files into 2GB chunks."
  [f combine in]
  (let [rs (-> in
               local-path
               Files/lines
               .parallel
               (core/stream-reducible combine))]
    (stream-lines f rs)))

(defn copy-lines!
  "Copy (by means of streaming) the lines from <in>
   (anything compatible with `io/reader`) to <out>
   (anything compatible with `io/writer`), transforming
   them with <f> along the way (no laziness).
   Returns nil."
  [f in out]
  (with-open [^BufferedWriter wrt (io/writer out)]
    (run!
      (fn [^String line]
        (.write wrt line)
        (.newLine wrt))
      (stream-lines f in))
    (.flush wrt)))

(comment

  (defn ->json [x t]
    (case t
      ("int", "long")     (Long/parseLong x)
      ("double", "float") (Double/parseDouble x)
      ("bool", "boolean") (Boolean/parseBoolean x)
      (str x)))

  (defn json-tuple
    [csv-vals [i path t]]
    [(str/split path #"\.")
     (-> csv-vals
         (nth (unchecked-dec i))
         (->json t))])

  (defn csv->json-lines
    ([file-in file-out ks-and-types]
     (csv->json-lines file-in file-out ks-and-types \,))
    ([file-in file-out ks-and-types sep]
     (copy-lines!
       (fn [line]
         (let [csv-row (first (csv/read-csv line :separator sep))]
           (->> ks-and-types
                (eduction (map (partial json-tuple csv-row)))
                (reduce (partial apply assoc-in) {})
                json/write-str)))
       file-in
       file-out)))

  (csv->json-lines
    "/Users/dimitrios/Desktop/in.csv"
    "/Users/dimitrios/Desktop/out.json"
    [[1 "event.uuid"]
     [2 "user.id" "int"]
     [4 "event.timestamp"]
     [6 "admin" "bool"]])



  ;; SERIAL JSON-LINES PARSER
  (->> input ;; anything compatible with `io/reader`
       (stream-lines json/read-str)
       (into []))

  ;; PARALLEL JSON-LINES PARSER
  (->> input ;; local File/URL/URL/String
       (pstream-lines json/read-str into)
       (reduce conj []))

  )
