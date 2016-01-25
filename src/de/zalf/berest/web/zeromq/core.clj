(ns de.zalf.berest.web.zeromq.core
  (:refer-clojure :exclude [send])
  (:gen-class)
  (:require [zeromq.zmq :as zmq]
            [zeromq.device :as zmqd]
            [de.zalf.berest.core.data :as data]
            [de.zalf.berest.core.api :as api]
            [de.zalf.berest.core.datomic :as db]
            [de.zalf.berest.core.core :as bc]
            [clojure.pprint :as pp]
            [cheshire.core :as json]))

(defn create-plot
  [db {:strs [crop-id fcs-mm pwps-mm isms-mm ka5s lambdas slope dc-assertions technology]}]
  (let [crop-template (data/db->crop-by-id (db/current-db) crop-id)

        {:strs [stepSize minDonation optDonation maxDonation type
                sprinkleLossFactor outletHeightCm cycleDays]} technology]
    {:plot/ka5-soil-types ka5s

     :plot/field-capacities fcs-mm
     :plot/fc-pwp-unit :soil-moisture.unit/mm

     :plot/permanent-wilting-points pwps-mm
     :plot/pwp-unit :soil-moisture.unit/mm

     :plot.annual/abs-day-of-initial-soil-moisture-measurement 90
     :plot.annual/initial-soil-moistures isms-mm
     :plot.annual/initial-sm-unit :soil-moisture.unit/mm

     :lambdas lambdas

     :plot.annual/crop-instances [{:crop.instance/template crop-template
                                   ;:crop.instance/name "dummy name"
                                   :crop.instance/dc-assertions (for [[abs-day dc] dc-assertions]
                                                                  {:assertion/abs-assert-dc-day abs-day
                                                                   :assertion/assert-dc dc})}]

     :fallow (data/db->crop-by-name db 0 :cultivation-type 0 :usage 0)

     :plot.annual/technology {:donation/step-size stepSize
                              :donation/opt optDonation
                              :donation/max maxDonation
                              :donation/min minDonation
                              :technology/type (keyword type)
                              :technology/sprinkle-loss-factor sprinkleLossFactor
                              :technology/outlet-height outletHeightCm
                              :technology/cycle-days cycleDays}

     #_:plot/slope #_{:slope/key 1
                      :slope/description "eben"
                      :slope/symbol "NFT 01" }

     :slope slope

     :plot.annual/donations []

     :plot/damage-compaction-area 0.0
     :plot/damage-compaction-depth 300
     :plot/irrigation-area 1.0
     :plot/crop-area 1.0
     :plot/groundwaterlevel 300}))

(defn calculate-from-remote-data
  [db {:strs [custom-id weather-data layer-sizes slope] :as data}]
  (binding [de.zalf.berest.core.core/*layer-sizes* (or layer-sizes (repeat 20 10))]
    (let [sorted-weather-map (into (sorted-map)
                                   (for [[doy precip evap] weather-data]
                                     [doy {:weather-data/precipitation precip
                                           :weather-data/evaporation evap}]))
          ;_ (println "sorted-weather-map: ")
          ;_ (pp/pprint sorted-weather-map)

          #_sorted-climate-data #_(into (sorted-map)
                                    (map (fn [[year years-data]]
                                           [year (into (sorted-map)
                                                       (for [[doy precip evap] years-data]
                                                         [doy {:weather-data/precipitation precip
                                                               :weather-data/evaporation evap}]))])
                                         weather-data))
          ;_ (println "sorted-climate-data: ")
          ;_ (pp/pprint sorted-climate-data)

          plot (create-plot db data)
          ;_ (println "plot: ")
          ;_ (pp/pprint plot)

          res (let [inputs (bc/create-input-seq plot
                                                sorted-weather-map
                                                365
                                                []
                                                (-> plot :plot.annual/technology :technology/type))]
                #_(println "inputs:")
                #_(pp/pprint inputs)
                (println "custom-id: " custom-id)
                (bc/calculate-sum-donations-by-auto-donations
                  inputs
                  (:plot.annual/initial-soil-moistures plot)
                  (int slope)
                  (:plot.annual/technology plot)
                  5))

          #_res #_(map (fn [[year sorted-weather-map]]
                     #_(println "calculating year: " year)
                     [year (let [inputs (bc/create-input-seq plot
                                                             sorted-weather-map
                                                             365
                                                             []
                                                             (-> plot :plot.annual/technology :technology/type))]
                             #_(println "inputs:")
                             #_(pp/pprint inputs)
                             (bc/calculate-sum-donations-by-auto-donations
                               inputs (:plot.annual/initial-soil-moistures plot)
                               (int slope)
                               (:plot.annual/technology plot)
                               5))])
                   sorted-climate-data)
          ;_ (println "res: " res)
          ;_ (println "calculated run-id: " custom-id)
          ]
      {:run-id custom-id
       :sum-donations res
       #_:result #_(into {} res)})))


(defn calculate-recommendation-from-remote-data
  [db {:strs [date weather-data layer-sizes slope] :as data}]
  (binding [de.zalf.berest.core.core/*layer-sizes* (or layer-sizes (repeat 20 10))]
    (let [sorted-weather-map (into (sorted-map)
                                   (for [[doy precip evap] weather-data]
                                     [doy {:weather-data/precipitation precip
                                           :weather-data/evaporation evap}]))
          ;_ (println "sorted-weather-map: ")
          ;_ (pp/pprint sorted-weather-map)

          plot (create-plot db data)
          ;_ (println "plot: ")
          ;_ (pp/pprint plot)

          inputs (bc/base-input-seq plot
                                    sorted-weather-map
                                    []
                                    (-> plot :plot.annual/technology :technology/type))
          ;_ (println "inputs:")
          ;_ (pp/pprint inputs)
          ]
      (assoc (bc/calc-recommendation
               6
               (int slope)
               (:plot.annual/technology plot)
               inputs
               (:plot.annual/initial-soil-moistures plot))
        :date date))))


(defonce context (zmq/context))

(defn -main
  [& args]
  (with-open [clients (doto (zmq/socket context :router)
                        (zmq/bind "tcp://*:6666"))
              workers (doto (zmq/socket context :dealer)
                        (zmq/bind "inproc://workers"))]
    (let [no-of-threads (or (when (and (= (count args) 2)
                                       (= (first args) "-no-of-threads"))
                              (Integer/parseInt (second args)))
                            1)
          _ (println "starting " no-of-threads " worker threads")
          _ (println "started ")]
      (dotimes [i no-of-threads]
        (-> (Thread. #(with-open [receiver (doto (zmq/socket context :rep)
                                             (zmq/connect "inproc://workers"))]
                       (println (inc i))
                       (while true
                         (let [string (zmq/receive-str receiver)
                               ;_ (println "received request str: " string)
                               clj (json/parse-string string)
                               ;_ (println "received request parsed: " (pr-str clj))
                               fname (first clj)
                               f (resolve (symbol fname))
                               data (second clj)
                               ;_ (println "data: " (pr-str data))
                               res (if f
                                     (f (db/current-db) data)
                                     (str "Error: rpc function '" fname "' couldn't be resolved!"))
                               res-str (json/generate-string res)
                               _ (println "sending response json-str: " res-str)
                               ]
                           #_(Thread/sleep 1)
                           (zmq/send-str receiver res-str)))))
            .start)))
    (zmqd/proxy context clients workers)))

(-main)
