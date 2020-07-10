(defproject clambda "0.1.5"
  :description "Utilities for working with Java Streams/Lambdas from Clojure."
  :url "https://github.com/jimpil/clambda"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies []
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.10.1"]
                                  [org.clojure/data.csv "1.0.0"]
                                  [org.clojure/data.json "1.0.0"]]}}
  ;; exact match of the test dictionary
  :jar-exclusions [#"ddict\.txt"]

  :release-tasks [["vcs" "assert-committed"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ;["vcs" "push"]
                  ]
  :deploy-repositories [["releases" :clojars]] ;; lein release :patch
  :signing {:gpg-key "jimpil1985@gmail.com"})
