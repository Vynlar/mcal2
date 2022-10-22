(ns app.model.class
  (:require
   [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
   [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]))

(defmutation register [params]
  (action [{:keys [app]}]
          (dr/change-route app ["my-classes"]))
  (remote [env] true))
