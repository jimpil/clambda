(defproject clambda "0.1.5-SNAPSHOT"
  :description "Utilities for working with Java Streams/Lambdas from Clojure."
  :url "https://github.com/jimpil/clambda"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]]
  ;; exact match of the test dictionary
  :jar-exclusions [#"ddict\.txt"])
