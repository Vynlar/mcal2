(ns app.model.session
  (:require
   [app.model.mock-database :as db]
   [app.model.account :as m.acc]
   [datascript.core :as d]
   [com.fulcrologic.guardrails.core :refer [>defn => | ?]]
   [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
   [taoensso.timbre :as log]
   [clojure.spec.alpha :as s]
   [com.fulcrologic.fulcro.server.api-middleware :as fmw]))

(defresolver current-session-resolver [env input]
  {::pc/output [{::current-session [:session/valid? :account/name :account/id]}]}
  (let [{:keys [account/name session/valid? account/id]} (get-in env [:ring/request :session])]
    (if valid?
      (do
        (log/info name "already logged in!")
        {::current-session {:session/valid? true :account/name name :account/id id}})
      {::current-session {:session/valid? false}})))

(defn response-updating-session
  "Uses `mutation-response` as the actual return value for a mutation, but also stores the data into the (cookie-based) session."
  [mutation-env mutation-response]
  (let [existing-session (some-> mutation-env :ring/request :session)]
    (fmw/augment-response
     mutation-response
     (fn [resp]
       (let [new-session (merge existing-session mutation-response)]
         (assoc resp :session new-session))))))

(>defn get-account-by-email [db email]
       [any? :account/email => ::m.acc/account]
       (d/pull db [:account/id :account/email :account/password]
               [:account/email email]))

(defmutation login [{:keys [db] :as env} {:keys [username password]}]
  {::pc/output [:session/valid? :account/name :account/id]}
  (log/info "Authenticating" username)
  (let [{expected-email    :account/email
         expected-password :account/password
         account-id        :account/id} (get-account-by-email db username)]
    (if (and (= username expected-email) (= password expected-password))
      (response-updating-session env
                                 {:session/valid? true
                                  :account/name   username
                                  :account/id account-id})
      (do
        (log/error "Invalid credentials supplied for" username)
        (throw (ex-info "Invalid credentials" {:username username}))))))

(defmutation logout [env params]
  {::pc/output [:session/valid?]}
  (response-updating-session env {:session/valid? false :account/name ""}))

(def default-account
  {:account/active? true})

(>defn insert-account!
       [conn account]
       [any? ::m.acc/account => any?]
       (d/transact! conn [(merge default-account account)]))

(defmutation signup! [{:keys [connection]} {:keys [email password]}]
  {::pc/output [:signup/result]}
  (insert-account! connection {:account/id (java.util.UUID/randomUUID)
                               :account/email email
                               :account/password password})
  {:signup/result "OK"})

(def resolvers [current-session-resolver login logout signup!])

(comment
  (insert-account! db/conn {:account/id (java.util.UUID/randomUUID)
                            :account/email "adrian@example.com"
                            :account/password "password"})
  (get-account-by-email @db/conn "adrian@example.com"))
