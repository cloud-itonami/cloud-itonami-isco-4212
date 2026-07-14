(ns gaming.actor-test
  (:require [clojure.test :refer [deftest is testing]]
            [gaming.actor :as actor]
            [gaming.store :as store]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Gaming"})
    (store/register-table! st {:table-id "T-1" :client-id "client-1"
                               :name "table-042"
                               :max-payout 5000})
    st))

(deftest commits-a-within-limit-verified-settlement
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-wager-settlement :stake :low
                 :table-id "T-1" :payout-amount 2000 :patron-verified? true}
        result (actor/run-request! graph request {} "thread-1")]
    (is (= :done (:status result)))
    (is (some? (get-in result [:state :record])))
    (is (= 1 (count (store/records-of st "client-1"))))))

(deftest holds-an-over-limit-settlement
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-wager-settlement :stake :low
                 :table-id "T-1" :payout-amount 20000 :patron-verified? true}
        result (actor/run-request! graph request {} "thread-2")]
    (is (= :hold (:disposition (:state result))))
    (is (empty? (store/records-of st "client-1")))))

(deftest interrupts-then-approves-over-limit-payout-on-human-approval
  (let [st (fresh-store)
        graph (actor/build-graph {:store st})
        request {:client-id "client-1" :op :approve-over-limit-payout :stake :low
                 :table-id "T-1"}
        interrupted (actor/run-request! graph request {} "thread-3")]
    (is (= :interrupted (:status interrupted)))
    (is (empty? (store/records-of st "client-1")))
    (let [resumed (actor/approve! graph "thread-3")]
      (is (= :done (:status resumed)))
      (is (= 1 (count (store/records-of st "client-1")))))))
