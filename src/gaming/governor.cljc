(ns gaming.governor
  "GamingOperationsGovernor — the independent safety/traceability
  layer named in this repository's README/business-model.md, gating
  every wager settlement an advisor may propose for a table. The
  governor never dispatches hardware itself and never pays out above
  a table's registered limit. Modeled on cloud-itonami-isco-4311's
  bookkeeping.governor. Task twist: a proposed payout amount is an
  arithmetic ceiling against the table's registered payout limit, and
  a wager cannot be settled until the patron's age/identity has been
  verified.

  HARD invariants (:hard? true, ALWAYS :hold, never overridable):
    1. client provenance   — the gaming venue/event operator must be
                             registered.
    2. no-actuation        — proposal :effect must be :propose (the
                             governor never dispatches hardware and
                             never pays out above a table's registered
                             limit; it only gates what the advisor may
                             settle).
    3. table basis         — a wager-settlement proposal must cite a
                             REGISTERED table belonging to this
                             client.
    4. payout ceiling      — the proposed payout amount must not
                             exceed the table's registered
                             `:max-payout` (a payout beyond the
                             table's registered limit is an
                             unauthorized payout, not a lucky night).
    5. patron-verified     — the proposal must have
                             `:patron-verified?` true before any wager
                             can be settled (settling a wager without
                             age/identity verification is an
                             underage/fraud risk, not efficient
                             service).
  ESCALATION invariants (:escalate? true, ALWAYS human sign-off per
  business-model.md's Trust Controls — these are :high/
  :safety-critical regardless of confidence):
    6. :op :approve-over-limit-payout (no payout above the table's
                             registered limit without the governor
                             gate).
    7. :op :approve-unverified-wager (a wager cannot be accepted until
                             age/identity verification is complete and
                             human sign-off is given).
    8. low confidence (< `confidence-floor`)."
  (:require [gaming.store :as store]))

(def confidence-floor 0.6)

(def ^:private always-escalate-ops #{:approve-over-limit-payout
                                     :approve-unverified-wager})

(defn- hard-violations [{:keys [request proposal]} client-record t]
  (let [{:keys [op payout-amount patron-verified?]} proposal
        settle? (= :approve-wager-settlement op)]
    (cond-> []
      (nil? client-record)
      (conj {:rule :no-client :detail "未登録 client"})

      (not= :propose (:effect proposal))
      (conj {:rule :no-actuation :detail "effect は :propose のみ許可（governor は登録上限超過の支払いを直接実行しない）"})

      (and settle? (nil? t))
      (conj {:rule :unknown-table :detail "未登録 table への精算提案は不可"})

      (and settle? t (not= (:client-id t) (:client-id request)))
      (conj {:rule :table-wrong-client :detail "table が別 client のもの"})

      (and settle? t (number? payout-amount) (> payout-amount (:max-payout t)))
      (conj {:rule :payout-exceeds-limit
             :detail (str "配当額 " payout-amount " > 登録済みテーブル上限 "
                          (:max-payout t) "（登録済み上限を超える配当は無許可支払いであって幸運な夜ではない）")})

      (and settle? (not patron-verified?))
      (conj {:rule :patron-not-verified
             :detail "年齢/本人確認未完了のウェイジャー精算は未成年/詐欺リスクであって効率的サービスではない"}))))

(defn check
  "Assess a proposal against `request`/`context`/`proposal` and a
  `store` implementing `gaming.store/Store`. Pure — never mutates the
  store, never pays out above a table's registered limit."
  [request context proposal store]
  (let [client-record (store/client store (:client-id request))
        t (some->> (:table-id proposal) (store/table store))
        hard (hard-violations {:request request :proposal proposal}
                              client-record t)
        hard? (boolean (seq hard))
        conf (or (:confidence proposal) 0.0)
        low? (< conf confidence-floor)
        always-risky? (contains? always-escalate-ops (:op proposal))]
    {:ok? (and (not hard?) (not low?) (not always-risky?))
     :violations hard
     :confidence conf
     :hard? hard?
     :escalate? (and (not hard?) (or low? always-risky?))}))
