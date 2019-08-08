(ns clambda.line-streams
  (:require [clambda.core :as core]
            [clojure.java.io :as io])
  (:import (java.nio.file Files Paths)
           (java.net URL URI)
           (java.io File)
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

   See `clambda.core/stream-into` for an example of a collecting context,
   and `clambda.core/stream-some` for an example of a short-circuiting one.

   Files greater than 2GB cannot be processed this way due to JVM array
   indexing using ints. Consider splitting huge files into 2GB chunks."
  [f combine in]
  (->> (-> in
           local-path
           Files/lines
           .parallel
           (core/stream-reducible combine))
       (stream-lines f)))

(comment

  ;; SERIAL JSON-LINES PARSER
  (->> input ;; anything compatible with `io/reader`
       (stream-lines data.json/read-str)
       (into []))

  ;; PARALLEL JSON-LINES PARSER
  (->> input ;; local File/URL/URL/String
       (pstream-lines data.json/read-str into)
       (reduce conj []))

  )
