(ns gaming.store
  "SSoT for the ISCO-08 4212 independent gaming-table & wagering
  operations practice actor (itonami actor pattern, ADR-2607011000 /
  CLAUDE.md Actors section; README's 'Robotics premise' — a chip/card
  handling and table-management robot performs card dealing, chip
  counting and table settlement tasks under this advisor/governor
  pair, which never dispatches hardware itself and never pays out
  above a table's registered limit). Modeled on
  cloud-itonami-isco-4311's bookkeeping.store.

  Domain:

    client — a registered gaming venue/event operator (:client-id,
             :name)
    table  — a registered gaming table {:table-id :client-id :name
             :max-payout number}. `:max-payout` is the registered
             payout ceiling a proposed wager settlement's payout
             amount must not exceed — a payout beyond the table's
             registered limit is an unauthorized payout, not a lucky
             night.
    record — a committed operating record (a settled wager) —
             written ONLY via commit-record!.
    ledger — append-only audit trail, commit or hold."
  )

(defprotocol Store
  (client [s client-id])
  (table [s table-id])
  (records-of [s client-id])
  (ledger [s])
  (register-client! [s client])
  (register-table! [s t])
  (commit-record! [s record])
  (append-ledger! [s fact]))

(defrecord MemStore [a]
  Store
  (client [_ client-id] (get-in @a [:clients client-id]))
  (table [_ table-id] (get-in @a [:tables table-id]))
  (records-of [_ client-id] (filter #(= client-id (:client-id %)) (:records @a)))
  (ledger [_] (:ledger @a))
  (register-client! [s client]
    (swap! a assoc-in [:clients (:client-id client)] client) s)
  (register-table! [s t]
    (swap! a assoc-in [:tables (:table-id t)] t) s)
  (commit-record! [s record]
    (swap! a update :records (fnil conj []) record) s)
  (append-ledger! [s fact]
    (swap! a update :ledger (fnil conj []) fact) s))

(defn mem-store
  ([] (mem-store {}))
  ([seed] (->MemStore (atom (merge {:clients {} :tables {} :records [] :ledger []}
                                   seed)))))
