(ns clambda.dictionary-attack
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [clambda.core :refer :all])
  (:import (java.nio.file Paths Files)
           (java.util.stream Stream)
           (java.io BufferedWriter)))

(defn spit-lines
  "Like `clojure.core/spit`, but for several lines
   without having to construct a giant String."
  [f lines & options]
  (with-open [^BufferedWriter wrt (apply io/writer f options)]
    (run! #(.write wrt ^String %)
          (interpose (System/getProperty "line.separator")
                     lines))))


(defn- random-dictionary!
  "Helper fn to be used on the REPL for generating dummy dictionaries."
  ([fpath]
   (random-dictionary! "mysecret" fpath))
  ([secret fpath]
   (random-dictionary! 100000 secret fpath))
  ([n your-secret fpath]
   (let [eng-chars (mapv char (range (int \a)
                                     (inc (int \z))))
         random-password #(apply str
                                 (repeatedly (+ 5 (rand-int 6)) ;; 6-11 chars long
                                             (partial rand-nth eng-chars)))
         ]
     (->> (repeatedly n random-password)
          (cons your-secret)
          shuffle
          (spit-lines fpath)))))


(defn- do-line-stream-test
  [parallel? java?]
  (let [try-fn #(and (= "mysecret" %) %)
        ^Stream line-stream (cond-> "ddict.txt" ;; 100,000 random entries + 'mysecret' (851 KB)
                                    true io/resource
                                    true .toURI
                                    true Paths/get
                                    true Files/lines
                                    parallel? .parallel)] ;; no point doing that on any JDK prior to 9
    (time
      (if java?
        (let [found (-> line-stream
                        (.map (jlambda :function try-fn))
                        (.filter (jlambda :predicate boolean))
                        .findAny
                        .get)]
          (is (= "mysecret"  found)))
        (let [found (stream-some (map try-fn) line-stream)]
          (is (= "mysecret" found)))))))


;; uncomment & run the following test after having generated `resources/ddict.txt`
;; feel free to adjust the size of the dictionary, by adjusting the number of entries in it.
;; see `random-dictionary!`

#_(deftest dictionary-attacks ;; 4 ways of performing a dictionary attack without OOM

    (System/gc)

    (testing "parallel dictionary-attack *in java* via JDK9 line stream"
      (do-line-stream-test true true))  ;; => "Elapsed time: 15.562752 msecs"

    (System/gc)

    (testing "sequential dictionary-attack *in java* via JDK9 line stream"
      (do-line-stream-test false true)) ;; => "Elapsed time: 20.294618 msecs"

    (System/gc)

    (testing "parallel dictionary-attack via JDK9 *reducible* line stream"
      (do-line-stream-test true false))  ;; => "Elapsed time: 17.66712 msecs" (on dual-core with HT)

    (System/gc)

    (testing "sequential dictionary-attack via JDK9 *reducible* line stream"
      (do-line-stream-test false false)) ;; => "Elapsed time: 25.27566 msecs"

    (System/gc)

    (testing "sequential dictionary-attack via *lazy* BufferedReader (line-seq)"
      (time                        ;; => "Elapsed time: 42.78569 msecs"
        (with-open [rdr (io/reader (io/resource "ddict.txt"))]
          (is (some?
                (->> rdr
                     line-seq
                     (some #(and (= "mysecret" %) %))))))))

    (System/gc)

    (testing "sequential dictionary-attack with *reducible* BufferedReader"
      (time                        ;; => "Elapsed time: 26.588302 msecs"
        (is (some?
              (transduce
                (filter (partial = "mysecret"))
                rf-some
                (lines-reducible (io/reader (io/resource "ddict.txt"))))))))

    )
