(ns gaming.advisor
  "Gaming Advisor — the advisor named in this repository's README,
  proposing a gaming operation (settle a wager, approve an over-limit
  payout, approve an unverified wager) from a patron registration,
  wager request and table limits. Swappable mock/llm; the advisor
  ONLY proposes — `gaming.governor` checks the payout ceiling and
  patron verification independently and always escalates over-limit-
  payout and unverified-wager decisions. Modeled on
  cloud-itonami-isco-4311's advisor.

  A proposal: {:op :approve-wager-settlement|:approve-over-limit-payout|:approve-unverified-wager
               :effect :propose :table-id str :payout-amount number
               :patron-verified? boolean :stake kw :confidence n
               :rationale str}")

(defprotocol Advisor
  (-advise [advisor store request] "request -> proposal map"))

(defn- infer [_store {:keys [op stake table-id payout-amount patron-verified?] :as request}]
  {:op op
   :effect :propose
   :table-id table-id
   :payout-amount payout-amount
   :patron-verified? (boolean patron-verified?)
   :stake (or stake :low)
   :confidence (case (or stake :low) :high 0.7 :medium 0.85 :low 0.95)
   :rationale (str "proposed " (name op) " for client " (:client-id request))})

(defn mock-advisor []
  (reify Advisor
    (-advise [_ store request] (infer store request))))

(def ^:private system-prompt
  "You are a gaming-operations advisor. Given a request, propose an
   :op, the :table-id, :payout-amount and whether the patron is
   verified, an honest :confidence and a :stake. Never propose a
   payout beyond the table's registered limit, or a wager settlement
   without patron verification — the governor checks both against the
   registered table record. Over-limit payouts and unverified wagers
   always require human sign-off regardless of confidence.")

(defn- parse-proposal [content]
  (try
    (let [p (read-string content)]
      (if (map? p)
        (assoc p :effect :propose)
        {:op :unknown :effect :propose :confidence 0.0 :stake :high
         :rationale "unparseable LLM response"}))
    (catch #?(:clj Exception :cljs js/Error) _
      {:op :unknown :effect :propose :confidence 0.0 :stake :high
       :rationale "LLM response parse failure"})))

(defn llm-advisor
  [chat-model model-generate-fn gen-opts]
  (reify Advisor
    (-advise [_ _store request]
      (let [msgs [{:role :system :content system-prompt}
                  {:role :user :content (str "operation request: " (pr-str request))}]
            resp (model-generate-fn chat-model msgs gen-opts)]
        (parse-proposal (:content resp))))))
