(ns plumbing.notify-test
  (:require [clojure.test :refer [deftest is]]
            [plumbing.notify :as notify]))

(deftest mock-notifier-test
  "MockNotifier logs sends without network."
  (let [n (notify/mock-notifier)
        _ (notify/-send-mail! n {:to "test@example.com" :subject "test" :body "test message"})
        log (notify/sent-log n)]
    (is (= 1 (count log)))
    (is (= :mail (:channel (first log))))
    (is (= "test@example.com" (:to (first log))))))

(deftest dispatch-alert-test
  "dispatch-alert! sends mail + phone to all contacts, isolating failures."
  (let [n (notify/mock-notifier)
        contacts [{:name "Person A" :email "a@test.com" :phone "+1234"}
                  {:name "Person B" :email "b@test.com" :phone "+5678"}]
        results (notify/dispatch-alert! n contacts "gas-leak" "smell detected")]
    (is (= 4 (count results)))
    (is (= 2 (count (filter #(= :mail (:channel %)) results))))
    (is (= 2 (count (filter #(= :phone (:channel %)) results))))))

(deftest dispatch-alert-email-only-test
  "dispatch-alert! handles contacts with no phone."
  (let [n (notify/mock-notifier)
        contacts [{:name "Person A" :email "a@test.com"}]
        results (notify/dispatch-alert! n contacts "water-leak" "water pooling")]
    (is (= 1 (count results)))
    (is (= :mail (:channel (first results))))))

(deftest dispatch-alert-phone-only-test
  "dispatch-alert! handles contacts with no email."
  (let [n (notify/mock-notifier)
        contacts [{:name "Person A" :phone "+1234"}]
        results (notify/dispatch-alert! n contacts "carbon-monoxide" "detector alarm")]
    (is (= 1 (count results)))
    (is (= :phone (:channel (first results))))))
