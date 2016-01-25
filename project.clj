(defproject
  de.zalf.berest/berest-zeromq-service "0.2.0"

  :description "BEREST ZeroMQ service"
  :url ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]

                 [de.zalf.berest/berest-core "0.2.0"]

                 [com.datomic/datomic-pro "0.9.5344" :exclusions [joda-time]]
                 [com.amazonaws/aws-java-sdk-dynamodb "1.9.39" :exclusions [joda-time]]

                 [org.zeromq/jeromq "0.3.5"]
                 [org.zeromq/cljzmq "0.1.4" :exclusions [org.zeromq/jzmq]]

                 [simple-time "0.2.1"]
                 [clj-time "0.11.0"]

                 [cheshire "5.5.0"]

                 #_[clojure-csv "2.0.1"]
                 #_[org.clojure/core.match "0.2.0"]]

  :min-lein-version "2.0.0"

  :source-paths ["src"]
  :resource-paths ["resources"]

  :profiles {:dev {:dependencies []
                   :resource-paths []}}
  
  :main de.zalf.berest.web.zeromq.core
  )












