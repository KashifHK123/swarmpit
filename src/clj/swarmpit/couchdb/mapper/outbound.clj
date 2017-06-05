(ns swarmpit.couchdb.mapper.outbound
  (:require [digest :as d]))

(defn ->password
  [password]
  (d/digest "sha-256" password))

(defn ->user
  [user]
  (assoc user :password (->password (:password user))
              :type "user"
              :id (hash (:username user))))

(defn ->registry
  [registry]
  (assoc registry :type "registry"
                  :id (hash (:name registry))))


