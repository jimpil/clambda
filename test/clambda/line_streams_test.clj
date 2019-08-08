(ns clambda.line-streams-test
  (:require [clambda.line-streams :as ls]
            [clojure.test :refer :all]
            [clojure.string :as str])
  (:import (java.io StringReader)))

(defonce CSV-DATA
  (let [lines (repeatedly 20 #(str/join \, (shuffle ["foo" "bar" "baz"])))]
    (cons "a,b,c" lines)))

(deftest core-tests
  (testing "stream-lines"
    (->> CSV-DATA
         (str/join \newline)
         StringReader.
         (ls/stream-lines identity)
         (into [] (map str/upper-case))
         (= (map str/upper-case CSV-DATA))
         is))
  )
