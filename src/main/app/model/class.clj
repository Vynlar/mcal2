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

(defn compute-slots [{:keys [range duration interval]}]
  (let [{:keys [start end]} range]
    (if (.isAfter (.plus start duration) end)
      []
      (conj (compute-slots
             {:range {:start (.plus start interval) :end end}
              :duration duration
              :interval interval})
            {:slot/start start
             :slot/end (.plus start duration)}))))

(defn get-valid-range []
  (let [timezone (java.time.ZoneId/of "America/Chicago")
        duration (java.time.Duration/ofHours 8)
        now (java.time.LocalDate/now timezone)
        start (-> now
                  (.plusDays 1)
                  (.atStartOfDay timezone)
                  (.plusHours 8))
        end (.plus start duration)]
    {:start start
     :end end}))

(defn format-date [date]
  (.format date java.time.format.DateTimeFormatter/ISO_INSTANT))

(defn- format-slot [{:slot/keys [start end]}]
  {:slot/start (format-date start)
   :slot/end (format-date end)})

(comment
  (get-valid-range)
  (map format-slot
       (compute-slots {:range (get-valid-range)
                       :duration (java.time.Duration/ofMinutes 50)
                       :interval (java.time.Duration/ofMinutes 60)})))

(defresolver slots-from-class-resolver [env input]
  {::pc/onput #{:class/id}
   ::pc/output [{:class/slots [:slot/start :slot/end]}]}
  {:class/slots
   (into []
         (map format-slot
              (sort-by #(-> % :slot/start .toEpochSecond)
                       (compute-slots {:range (get-valid-range)
                                       :duration (java.time.Duration/ofMinutes 50)
                                       :interval (java.time.Duration/ofMinutes 60)}))))})

(defresolver my-classes-resolver [{:keys [db] :as env} params]
  {::pc/output [{::my-classes [:class/id]}]}
  (let [{:keys [account/id]} (get-in env [:ring/request :session])
        classes
        (:account/classes (d/pull db [{:account/classes [:class/id]}]
                                  [:account/id id]))]
    {::my-classes classes}))

(defmutation register [{:keys [connection] :as env} {class-id :class/id :slot/keys [start]}]
  {::pc/output [:registration/id]}
  (let [id (java.util.UUID/randomUUID)
        {account-id :account/id} (get-in env [:ring/request :session])]
    (d/transact! connection [{:registration/id id
                              :registration/class [:class/id class-id]
                              :registration/account [:account/id account-id]
                              :registration/start start}])
    {:registration/id id}))

(def resolvers [class-resolver classes-by-account-resolver my-classes-resolver slots-from-class-resolver register])

(comment
  (def adrian (:account/id
               (session/get-account-by-email @db/conn "adrian@example.com")))
  (d/pull @db/conn '[{:account/classes [*]}]
          [:account/id adrian])

  (insert-class! db/conn
                 adrian
                 {:class/name "My Class"}))
