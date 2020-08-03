(ns clambda.file-streams
  (:require [clambda.core :as core])
  (:import (java.nio.file Files Path FileVisitOption Paths)
           (java.io File)))


(defn files-reducible
  "A reducible alternative to `clojure.core/file-seq`,
   based on the Stream returned by `Files/walk`.
   Will work with either File or Path input.
   Returns an eduction representing the stream of File objects."
  [dir]
  (eduction
    (map (fn [^Path p]
           (.toFile p)))
    (cond-> dir
            (instance? File dir)
            .toPath

            (instance? String dir)
            (Paths/get (make-array String 0))

            true
            (Files/walk (make-array FileVisitOption 0))

            true
            core/stream-reducible)))

(defn dir-filter
  "Returns a transducer which filters files
   that belong to a specific <dir> (File or Path)."
  [dir]
  (let [dir-str (condp instance? dir
                  File (.getPath dir)
                  Path (str dir)
                  (throw
                    (IllegalArgumentException.
                      "<dir> MUST be an instance of a File or Path!")))]
    (filter (fn [^File f]
              (= dir-str (.getParent f))))))

(def regular-file-filter
  (filter (fn [^File f]
            (.isFile f))))

(defn extension-filter
  [^String extension]
  (filter (fn [^File f]
            (.endsWith (.getName f) extension))))

(defn regular-files-in-dir
  "Returns a vector of all regular files in <dir>."
  [dir]
  (into []
        (comp
          (dir-filter dir)
          regular-file-filter)
  (files-reducible dir)))

(defn regular-files-with-extension
  "Returns a vector of all regular files under <dir>
   with extension <ext>."
  [dir ext]
  (into []
        (comp
          (extension-filter ext)
          regular-file-filter)
  (files-reducible dir)))
