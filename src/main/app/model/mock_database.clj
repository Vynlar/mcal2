(ns app.model.mock-database
  "This is a mock database implemented via Datascript, which runs completely in memory, has few deps, and requires
  less setup than Datomic itself.  Its API is very close to Datomics, and for a demo app makes it possible to have the
  *look* of a real back-end without having quite the amount of setup to understand for a beginner."
  (:require
   [datascript.core :as d]
   [mount.core :refer [defstate]]))

;; In datascript just about the only thing that needs schema
;; is lookup refs and entity refs.  You can just wing it on
;; everything else.
(def schema {:account/id {:db/cardinality :db.cardinality/one
                          :db/unique      :db.unique/identity}
             :account/email {:db/cardinality :db.cardinality/one
                             :db/unique      :db.unique/identity}
             :account/classes {:db/cardinality :db.cardinality/many
                               :db/type :db.type/ref}
             :registration/id {:db/cardinality :db.cardinality/one
                               :db/unique      :db.unique/identity}
             :registration/account {:db/cardinality :db.cardinality/one
                                    :db/type :db.type/ref}
             :registration/class {:db/cardinality :db.cardinality/one
                                  :db/type :db.type/ref}
             :class/id {:db/cardinality :db.cardinality/one
                        :db/unique      :db.unique/identity}})

(defn new-database [] (d/create-conn schema))

(defonce stable-conn (new-database))

(defstate conn :start stable-conn)

(defn reset-db []
  (d/reset-conn! stable-conn @(new-database)))
