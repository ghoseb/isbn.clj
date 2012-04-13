(defproject isbn "0.1"
  :description "Fetch book prices in INR given ISBN."
  :dependencies [[org.clojure/clojure "1.3.0"]
                 [org.clojure/data.json "0.1.2"]
                 [org.clojure/core.cache "0.5.0"]
                 [ring/ring-core "1.1.0-RC1"]
                 [ring/ring-jetty-adapter "1.1.0-RC1"]
                 [compojure "1.0.2"]
                 [enlive "1.0.0"]])
