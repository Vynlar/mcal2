(ns development
  (:require
   [clojure.tools.namespace.repl :as tools-ns :refer [set-refresh-dirs]]
   [app.model.session :refer [insert-account!]]
   [app.model.mock-database :refer [reset-db conn]]
   [mount.core :as mount]
   ;; this is the top-level dependent component...mount will find the rest via ns requires
   [app.server-components.http-server :refer [http-server]]))

(defn start
  "Start the web server"
  [] (mount/start))

(defn stop
  "Stop the web server"
  [] (mount/stop))

(defn restart
  "Stop, reload code, and restart the server. If there is a compile error, use:

  ```
  (tools-ns/refresh)
  ```

  to recompile, and then use `start` once things are good."
  []
  (stop)
  (tools-ns/refresh :after 'development/start))

(defonce mock-uuid (java.util.UUID/randomUUID))

(defn reset-and-seed []
  (reset-db)
  (restart)
  (insert-account! conn {:account/id mock-uuid
                         :account/email "adrian@example.com"
                         :account/password "password"}))

(comment
  (start)
  (reset-and-seed)
  (restart))
