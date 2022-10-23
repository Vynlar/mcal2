(ns app.model.class
  (:require
   [com.fulcrologic.fulcro.mutations :as m :refer [defmutation]]
   [com.fulcrologic.fulcro.routing.dynamic-routing :as dr]
   [com.fulcrologic.fulcro.components :as comp]))

(defmutation register [params]
  (remote [env] (m/returning env (comp/registry-key->class :app.ui.root/RegistrationDetails)))
  (ok-action [{:keys [result app] :as env}]
             (let [{:registration/keys [id success?]} (get-in result [:body `register])]
               (when success?
                 (dr/change-route app ["registration" id])))))
