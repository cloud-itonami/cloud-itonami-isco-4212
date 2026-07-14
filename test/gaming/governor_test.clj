(ns gaming.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [gaming.store :as store]
            [gaming.governor :as governor]))

(defn- fresh-store []
  (let [st (store/mem-store)]
    (store/register-client! st {:client-id "client-1" :name "Kobo Gaming"})
    (store/register-table! st {:table-id "T-1" :client-id "client-1"
                               :name "table-042"
                               :max-payout 5000})
    st))

(defn- settle-op [payout verified?]
  {:op :approve-wager-settlement :effect :propose :table-id "T-1"
   :payout-amount payout :patron-verified? verified?
   :confidence 0.9 :stake :low})

(def ^:private req {:client-id "client-1"})

(deftest ok-within-limit-and-verified
  (let [st (fresh-store)
        v (governor/check req {} (settle-op 2000 true) st)]
    (is (:ok? v))))

(deftest ok-at-exact-limit-boundary
  (testing "the payout ceiling is inclusive"
    (let [st (fresh-store)
          v (governor/check req {} (settle-op 5000 true) st)]
      (is (:ok? v)))))

(deftest hard-on-payout-exceeds-limit
  (testing "a payout beyond the table's registered limit is an unauthorized payout, not a lucky night"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (settle-op 20000 true) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :payout-exceeds-limit (:rule %)) (:violations v))))))

(deftest hard-on-patron-not-verified
  (testing "settling a wager without age/identity verification is an underage/fraud risk, not efficient service"
    (let [st (fresh-store)
          v (governor/check req {} (assoc (settle-op 2000 false) :confidence 0.99) st)]
      (is (:hard? v))
      (is (some #(= :patron-not-verified (:rule %)) (:violations v))))))

(deftest hard-on-unknown-table
  (let [st (fresh-store)
        v (governor/check req {} (assoc (settle-op 2000 true) :table-id "T-ghost") st)]
    (is (:hard? v))
    (is (some #(= :unknown-table (:rule %)) (:violations v)))))

(deftest hard-on-foreign-table
  (let [st (fresh-store)]
    (store/register-client! st {:client-id "client-2" :name "Other"})
    (let [v (governor/check {:client-id "client-2"} {} (settle-op 2000 true) st)]
      (is (:hard? v))
      (is (some #(= :table-wrong-client (:rule %)) (:violations v))))))

(deftest hard-on-unregistered-client
  (let [st (fresh-store)
        v (governor/check {:client-id "nobody"} {} (settle-op 2000 true) st)]
    (is (:hard? v))
    (is (some #(= :no-client (:rule %)) (:violations v)))))

(deftest hard-on-no-actuation-violation
  (let [st (fresh-store)
        v (governor/check req {} (assoc (settle-op 2000 true) :effect :direct-write) st)]
    (is (:hard? v))
    (is (some #(= :no-actuation (:rule %)) (:violations v)))))

(deftest always-escalates-over-limit-payout-even-at-high-confidence
  (testing "no payout above the table's registered limit without the governor gate"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-over-limit-payout :effect :propose
                                    :table-id "T-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest always-escalates-unverified-wager-even-at-high-confidence
  (testing "a wager cannot be accepted until age/identity verification is complete and human sign-off is given"
    (let [st (fresh-store)
          v (governor/check req {} {:op :approve-unverified-wager :effect :propose
                                    :table-id "T-1" :confidence 0.99 :stake :low} st)]
      (is (not (:hard? v)))
      (is (:escalate? v)))))

(deftest escalates-low-confidence
  (let [st (fresh-store)
        v (governor/check req {} (assoc (settle-op 2000 true) :confidence 0.3) st)]
    (is (not (:hard? v)))
    (is (:escalate? v))))
