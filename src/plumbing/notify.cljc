(ns plumbing.notify
  "Mail/phone outreach for escalations -- safety hazard flags and
  inspection review requests that ALWAYS require human sign-off from a
  licensed professional. The mock-notifier (deterministic, no network --
  the default) is suitable for dev/tests/demo; real Resend/Twilio
  notifiers can be added later when deploying to production.

  This namespace sends notifications; it does NOT decide whether to send one
  -- that is `plumbing.governor` and `plumbing.phase`. The notifier is
  injected (protocol swap), not wired.")
  (:require [clojure.string :as str]))

(defprotocol Notifier
  (-send-mail! [n msg] "msg: {:to :subject :body} -> {:status :channel :to ..}")
  (-send-phone-call! [n msg] "msg: {:to :message} -> {:status :channel :to ..}"))

;; ----------------------------- mock (default) -----------------------------

(defrecord MockNotifier [log]
  Notifier
  (-send-mail! [_ {:keys [to subject body]}]
    (let [result {:status :sent :channel :mail :to to :subject subject :body body}]
      (swap! log conj result)
      result))
  (-send-phone-call! [_ {:keys [to message]}]
    (let [result {:status :sent :channel :phone :to to :message message}]
      (swap! log conj result)
      result)))

(defn mock-notifier
  "A deterministic notifier -- no network, records every send to an
  internal log atom. Default everywhere (dev/tests/demo)."
  ([] (mock-notifier (atom [])))
  ([log] (->MockNotifier log)))

(defn sent-log [^MockNotifier n] @(:log n))

(defn dispatch-alert!
  "Fan out a safety hazard alert (mail + phone) to all contacts on the
  project roster. Isolates one contact's send failure from every other
  contact's -- a bad phone number must never suppress the mail (or the
  other workers' calls) for a safety hazard alert.

  This is a mock implementation. Real implementations would use Resend
  (mail) and Twilio (phone), with JVM-only HTTP transport."
  [notifier contacts hazard-type description]
  (let [results (atom [])]
    (doseq [{:keys [name email phone]} contacts]
      ;; Mail send, isolate from phone send
      (when email
        (swap! results conj
          (-send-mail! notifier
            {:to email
             :subject (str "Safety Alert: " hazard-type " detected")
             :body (str "SAFETY HAZARD ALERT\n\nHazard: " hazard-type
                       "\nDescription: " description
                       "\n\nThis must be addressed by a licensed professional immediately.\n"
                       "DO NOT work around this flagged condition.\n")})))
      ;; Phone send, isolate from mail send
      (when phone
        (swap! results conj
          (-send-phone-call! notifier
            {:to phone
             :message (str "Safety alert: " hazard-type " detected. "
                          "This must be addressed by a licensed professional immediately.")}))))
    @results))
