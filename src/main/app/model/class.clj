(ns app.model.class
  (:require
   [clojure.spec.alpha :as s]
   [com.fulcrologic.guardrails.core :refer [>defn => | ?]]
   [com.wsscode.pathom.connect :as pc :refer [defresolver defmutation]]
   [datascript.core :as d]
   [app.model.mock-database :as db]
   [app.model.account :as acct]
   [app.model.session :as session]))

(s/def :class/id uuid?)
(s/def :class/name (s/and string? (comp not empty?)))
(s/def ::class (s/keys :req [:class/id
                             :class/name]))

(>defn insert-class! [conn account-id {:class/keys [name]}]
       [d/conn? :account/id (s/keys :req [:class/name]) => any?]

       (let [class-id (java.util.UUID/randomUUID)]
         (d/transact! conn
                      [{:class/id class-id
                        :class/name name}
                       [:db/add [:account/id account-id]
                        :account/classes [:class/id class-id]]])))

(defresolver class-resolver [{:keys [db]} {:class/keys [id]}]
  {::pc/input #{:class/id}
   ::pc/output [:class/name]}
  (d/pull db [:class/name]
          [:class/id id]))

(defresolver classes-by-account-resolver [{:keys [db]} {account-id :account/id}]
  {::pc/input #{:account/id}
   ::pc/output [:class/id]}
  (d/q '[:find ?cid
         :in $ ?aid
         :where
         [?c :class/id ?cid]
         [?a :account/classes ?c]
         [?a :account/id ?aid]]
       db account-id))

(defresolver my-classes-resolver [{:keys [db] :as env} params]
  {::pc/output [{::my-classes [:class/id]}]}
  (let [{:keys [account/id]} (get-in env [:ring/request :session])
        classes
        (:account/classes (d/pull db [{:account/classes [:class/id]}]
                                  [:account/id id]))]
    {::my-classes classes}))

(def resolvers [class-resolver classes-by-account-resolver my-classes-resolver])

(comment
  (def adrian (:account/id
               (session/get-account-by-email @db/conn "adrian@example.com")))
  (d/pull @db/conn '[* {:account/classes [*]}]
          [:account/id adrian])
  (insert-class! db/conn
                 adrian
                 {:class/name "heyo"}))
