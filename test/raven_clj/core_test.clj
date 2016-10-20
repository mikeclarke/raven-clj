(ns raven-clj.core-test
  (:require [clj-http.client :as http])
  (:use clojure.test
        raven-clj.core)
  (:import [java.sql Timestamp]
           [java.util Date UUID]))

(def example-dsn
  (str "https://"
       "b70a31b3510c4cf793964a185cfe1fd0:b7d80b520139450f903720eb7991bf3d"
       "@example.com/1"))

(deftest test-make-sentry-url
  (testing "secure url"
    (is (= (make-sentry-url "https://example.com" "1")
           "https://example.com/api/1/store/")))
  (testing "insecure url"
    (is (= (make-sentry-url "http://example.com" "1")
           "http://example.com/api/1/store/"))))

(deftest test-make-sentry-header
  (testing "sentry header"
    (let [ts (str (Timestamp. (.getTime (Date.))))
          key "b70a31b3510c4cf793964a185cfe1fd0"
          secret "b7d80b520139450f903720eb7991bf3d"
          client-version "1.4.2"
          hdr (make-sentry-header ts key secret)]

      (is (.contains hdr "sentry_version=2.0")
          "includes sentry version")
      (is (.contains hdr (str "sentry_client=raven-clj/" client-version))
          "includes client version")
      (is (.contains hdr (format "sentry_timestamp=%s" ts))
          "includes timestamp")
      (is (.contains hdr (str "sentry_key=" key))
          "includes key")
      (is (.contains hdr (str "sentry_secret=" secret))
          "includes secret")
      (is (= hdr (format "Sentry sentry_version=2.0, sentry_client=raven-clj/%s, sentry_timestamp=%s, sentry_key=%s, sentry_secret=%s" client-version ts key secret))))))

(deftest test-send-packet
  (testing "send-packet"
    (let [actual-opts (atom nil)
          packet {:key "key"
                  :secret "secret"
                  :uri "uri"
                  :project-id "project-id"
                  :ts "ts"}]
      (with-redefs [http/post (fn [url opts]
                                (reset! actual-opts opts))]
        (send-packet packet)

        (is (= (-> @actual-opts :headers (get "User-Agent")) "raven-clj/1.4.2")
            "includes User-Agent header in request")))))

(deftest test-parse-dsn
  (testing "dsn parsing"
    (is (= (parse-dsn "https://b70a31b3510c4cf793964a185cfe1fd0:b7d80b520139450f903720eb7991bf3d@example.com/1")
           {:key "b70a31b3510c4cf793964a185cfe1fd0"
            :secret "b7d80b520139450f903720eb7991bf3d"
            :uri "https://example.com"
            :project-id 1})))

  (testing "dsn parsing with path"
    (is (= (parse-dsn "https://b70a31b3510c4cf793964a185cfe1fd0:b7d80b520139450f903720eb7991bf3d@example.com/sentry/1")
           {:key "b70a31b3510c4cf793964a185cfe1fd0"
            :secret "b7d80b520139450f903720eb7991bf3d"
            :uri "https://example.com/sentry"
            :project-id 1})))

  (testing "dsn parsing with port"
    (is (= (parse-dsn "https://b70a31b3510c4cf793964a185cfe1fd0:b7d80b520139450f903720eb7991bf3d@example.com:9000/1")
           {:key "b70a31b3510c4cf793964a185cfe1fd0"
            :secret "b7d80b520139450f903720eb7991bf3d"
            :uri "https://example.com:9000"
            :project-id 1})))

  (testing "dsn parsing with port and path"
    (is (= (parse-dsn "https://b70a31b3510c4cf793964a185cfe1fd0:b7d80b520139450f903720eb7991bf3d@example.com:9000/sentry/1")
           {:key "b70a31b3510c4cf793964a185cfe1fd0"
            :secret "b7d80b520139450f903720eb7991bf3d"
            :uri "https://example.com:9000/sentry"
            :project-id 1}))))

(deftest test-capture
  (testing "capture"
    (testing "with a valid dsn"
      (let [event-info (atom nil)]
        (with-redefs [send-packet (fn [ev] (reset! event-info ev))]
          (capture example-dsn {})
          (is (= (:platform @event-info) "clojure")
              "should set :platform in event-info to clojure"))))
    (testing "with an explicit uuid"
      (let [event-info (atom nil)
            uuid (UUID/randomUUID)
            event-id (#'raven-clj.core/generate-uuid uuid)]
        (with-redefs [send-packet (fn [ev] (reset! event-info ev))]
          (capture example-dsn {} :uuid uuid)
          (is (= (:event_id @event-info) event-id)
              (str "should set :event_id to " event-id)))))))
