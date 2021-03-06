(ns atlas.domain.sequence-diagram-test
  (:require [atlas.domain.sequence-diagram :as nut]
            [clojure.test :refer [is testing]]
            [common-clj.clojure-test-helpers.core :refer [deftest]]
            [matcher-combinators.matchers :as m]
            [matcher-combinators.test :refer [match?]]))

(def trace
  {:trace-id  "1"
   :spans     [{:trace-id       "1"
                :span-id        "1"
                :process-id     :p1
                :operation-name "http.in GET /api/orders/1"
                :start-time     1500000000000000
                :duration       1000000
                :references     []
                :tags           [{:key   "span.kind"
                                  :type  "string"
                                  :value "server"}
                                 {:key   "http.method"
                                  :type  "string"
                                  :value "GET"}
                                 {:key   "http.url"
                                  :type  "string"
                                  :value "/api/orders/1"}]}

               {:trace-id       "1"
                :span-id        "2"
                :process-id     :p1
                :operation-name "http.out GET /api/orders/1"
                :start-time     1500000000100000
                :duration       300000
                :references     [{:ref-type :child-of
                                  :trace-id "1"
                                  :span-id  "1"}]
                :tags           [{:key   "span.kind"
                                  :type  "string"
                                  :value "client"}
                                 {:key   "http.method"
                                  :type  "string"
                                  :value "GET"}
                                 {:key   "http.url"
                                  :type  "string"
                                  :value "/api/orders/1"}]}

               {:trace-id       "1"
                :span-id        "3"
                :process-id     :p2
                :operation-name "http.in GET /api/orders/1"
                :start-time     1500000000200000
                :duration       100000
                :references     [{:ref-type :child-of
                                  :trace-id "1"
                                  :span-id  "2"}]
                :tags           [{:key   "span.kind"
                                  :type  "string"
                                  :value "server"}
                                 {:key   "http.method"
                                  :type  "string"
                                  :value "GET"}
                                 {:key   "http.url"
                                  :type  "string"
                                  :value "/api/orders/1"}]}
               {:trace-id       "1"
                :span-id        "4"
                :process-id     :p2
                :operation-name "kafka.out PROCESS_ORDER"
                :start-time     1500000000250000
                :duration       50000
                :references     [{:ref-type :child-of
                                  :trace-id "1"
                                  :span-id  "3"}]
                :tags           [{:key   "span.kind"
                                  :type  "string"
                                  :value "producer"}
                                 {:key   "message_bus.destination"
                                  :type  "string"
                                  :value "PROCESS_ORDER"}]}
               {:trace-id       "1"
                :span-id        "5"
                :process-id     :p3
                :operation-name "kafka.in PROCESS_ORDER"
                :start-time     1500000000350000
                :duration       50000
                :references     [{:ref-type :child-of
                                  :trace-id "1"
                                  :span-id  "4"}]
                :tags           [{:key   "span.kind"
                                  :type  "string"
                                  :value "consumer"}
                                 {:key   "message_bus.destination"
                                  :type  "string"
                                  :value "PROCESS_ORDER"}]}]

   :processes {:p1 {:service-name "bff"}
               :p2 {:service-name "orders"}
               :p3 {:service-name "orders"}}})

(deftest start-time
  (testing "returns the oldest span start time"
    (is (= #epoch 1500000000000
           (nut/start-time trace)))))

(deftest duration-ms
  (testing "time between the beginning of the first started span and the end of the last finished span"
    (is (= 1000
           (nut/duration-ms trace)))))

(deftest lifelines
  (testing "builds lifelines from trace"
    (is (match? (m/in-any-order [{:name "bff"
                                  :kind :service}
                                 {:name "orders"
                                  :kind :service}
                                 {:name "PROCESS_ORDER"
                                  :kind :topic}])
                (nut/lifelines trace)))))

(deftest execution-boxes
  (testing "builds execution boxes from trace"
    (is (= [{:id          "1"
             :start-time  #epoch 1500000000000
             :duration-ms 1000
             :lifeline    "bff"}
            {:id          "3"
             :start-time  #epoch 1500000000200
             :duration-ms 100
             :lifeline    "orders"}
            {:id          "5"
             :start-time  #epoch 1500000000350
             :duration-ms 50
             :lifeline    "orders"}]
           (nut/execution-boxes trace)))))

(deftest arrows
  (testing "builds arrows from trace"
    (is (= [{:id         "2"
             :from       "bff"
             :to         "orders"
             :start-time #epoch 1500000000100
             :prefix     "GET"
             :label      "/api/orders/1"}
            {:id         "3"
             :from       "orders"
             :to         "bff"
             :start-time #epoch 1500000000400
             :label      "response"}
            {:id         "4"
             :from       "orders"
             :to         "PROCESS_ORDER"
             :start-time #epoch 1500000000250
             :label      "produce"}
            {:id         "5"
             :from       "PROCESS_ORDER"
             :to         "orders"
             :start-time #epoch 1500000000350
             :label      "consume"}]
           (nut/arrows trace)))))
