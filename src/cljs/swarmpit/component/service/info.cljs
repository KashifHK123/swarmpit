(ns swarmpit.component.service.info
  (:require [material.icon :as icon]
            [material.components :as comp]
            [material.component.grid.masonry :as masonry]
            [material.component.list.basic :as list]
            [swarmpit.component.state :as state]
            [swarmpit.component.mixin :as mixin]
            [swarmpit.component.progress :as progress]
            [swarmpit.component.service.info.settings :as settings]
            [swarmpit.component.service.info.ports :as ports]
            [swarmpit.component.service.info.networks :as networks]
            [swarmpit.component.service.info.mounts :as mounts]
            [swarmpit.component.service.info.secrets :as secrets]
            [swarmpit.component.service.info.configs :as configs]
            [swarmpit.component.service.info.variables :as variables]
            [swarmpit.component.service.info.labels :as labels]
            [swarmpit.component.service.info.logdriver :as logdriver]
            [swarmpit.component.service.info.resources :as resources]
            [swarmpit.component.service.info.deployment :as deployment]
            [swarmpit.component.task.list :as tasks]
            [swarmpit.component.message :as message]
            [swarmpit.url :refer [dispatch!]]
            [swarmpit.ajax :as ajax]
            [swarmpit.routes :as routes]
            [sablono.core :refer-macros [html]]
            [rum.core :as rum]))

(enable-console-print!)

(defn- label [item]
  (str (:state item) "  " (get-in item [:status :info])))

(defn- service-handler
  [service-id]
  (ajax/get
    (routes/path-for-backend :service {:id service-id})
    {:state      [:loading?]
     :on-success (fn [{:keys [response]}]
                   (state/update-value [:service] response state/form-value-cursor))}))

(defn- service-networks-handler
  [service-id]
  (ajax/get
    (routes/path-for-backend :service-networks {:id service-id})
    {:on-success (fn [{:keys [response]}]
                   (state/update-value [:networks] response state/form-value-cursor))}))

(defn- service-tasks-handler
  [service-id]
  (ajax/get
    (routes/path-for-backend :service-tasks {:id service-id})
    {:on-success (fn [{:keys [response]}]
                   (state/update-value [:tasks] response state/form-value-cursor))}))

(defn- delete-service-handler
  [service-id]
  (ajax/delete
    (routes/path-for-backend :service-delete {:id service-id})
    {:on-success (fn [_]
                   (dispatch!
                     (routes/path-for-frontend :service-list))
                   (message/info
                     (str "Service " service-id " has been removed.")))
     :on-error   (fn [{:keys [response]}]
                   (message/error
                     (str "Service removing failed. " (:error response))))}))

(defn- redeploy-service-handler
  [service-id]
  (ajax/post
    (routes/path-for-backend :service-redeploy {:id service-id})
    {:on-success (fn [_]
                   (message/info
                     (str "Service " service-id " redeploy triggered.")))
     :on-error   (fn [{:keys [response]}]
                   (message/error
                     (str "Service redeploy failed. " (:error response))))}))

(defn- rollback-service-handler
  [service-id]
  (ajax/post
    (routes/path-for-backend :service-rollback {:id service-id})
    {:on-success (fn [_]
                   (message/info
                     (str "Service " service-id " rollback triggered.")))
     :on-error   (fn [{:keys [response]}]
                   (message/error
                     (str "Service rollback failed. " (:error response))))}))

(defn form-tasks [tasks]
  (comp/card
    {:className "Swarmpit-card"
     :key       "ftc"}
    (comp/card-header
      {:className "Swarmpit-table-card-header"
       :key       "ftch"
       :title     "Tasks"})
    (comp/card-content
      {:className "Swarmpit-table-card-content"
       :key       "ftcc"}
      (rum/with-key
        (list/responsive
          tasks/render-metadata
          (filter #(not (= "shutdown" (:state %))) tasks)
          tasks/onclick-handler) "ftccrl"))))

(defn form-actions
  [service service-id]
  [{:onClick #(dispatch! (routes/path-for-frontend :service-edit {:id service-id}))
    :icon    (comp/svg icon/edit-path)
    :name    "Edit service"}
   {:onClick #(dispatch! (routes/path-for-frontend :stack-create nil {:from service-id}))
    :icon    (comp/svg icon/stacks-path)
    :more    true
    :name    "Compose stack"}
   {:onClick #(redeploy-service-handler service-id)
    :icon    (comp/svg icon/redeploy-path)
    :more    true
    :name    "Redeploy service"}
   {:onClick  #(rollback-service-handler service-id)
    :disabled (not (get-in service [:deployment :rollbackAllowed]))
    :icon     (comp/svg icon/rollback-path)
    :more     true
    :name     "Rollback service"}
   {:onClick #(delete-service-handler service-id)
    :icon    (comp/svg icon/trash-path)
    :name    "Delete service"}])

(defn- init-form-state
  []
  (state/set-value {:menu?    false
                    :loading? true} state/form-state-cursor))

(defn- init-form-value
  []
  (state/set-value {:service  {}
                    :tasks    []
                    :networks []} state/form-value-cursor))

(def mixin-init-form
  (mixin/init-form
    (fn [{{:keys [id]} :params}]
      (init-form-state)
      (init-form-value)
      (service-handler id)
      (service-networks-handler id)
      (service-tasks-handler id))))

(rum/defc form-info < rum/static [{:keys [service networks tasks]}]
  (let [ports (:ports service)
        mounts (:mounts service)
        secrets (:secrets service)
        configs (:configs service)
        variables (:variables service)
        labels (:labels service)
        logdriver (:logdriver service)
        resources (:resources service)
        deployment (:deployment service)
        id (:id service)
        is-even-and-not-third? #(and (even? %) (not (= 2 %)))]
    (comp/mui
      (html
        [:div.Swarmpit-form
         [:div.Swarmpit-form-context
          (masonry/grid
            {:first-col-pred is-even-and-not-third?}
            (settings/form service tasks (form-actions service id))
            (deployment/form deployment id)
            (when (not-empty networks)
              (networks/form networks id))
            (when (not-empty ports)
              (ports/form ports id))
            (when (not-empty mounts)
              (mounts/form mounts id))
            (when (not-empty secrets)
              (secrets/form secrets id))
            (when (not-empty configs)
              (configs/form configs id))
            (when (not-empty variables)
              (variables/form variables id))
            (when (not-empty labels)
              (labels/form labels id))
            (when (not-empty (:opts logdriver))
              (logdriver/form logdriver id)))
          (comp/grid
            {:container true
             :key       "scg"
             :spacing   40}
            (comp/grid
              {:item true
               :key  "scitg"
               :xs   12}
              (form-tasks tasks)))]]))))

(rum/defc form < rum/reactive
                 mixin-init-form
                 mixin/subscribe-form [_]
  (let [state (state/react state/form-state-cursor)
        item (state/react state/form-value-cursor)]
    (progress/form
      (:loading? state)
      (form-info item))))
