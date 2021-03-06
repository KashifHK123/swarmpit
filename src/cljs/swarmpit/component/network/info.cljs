(ns swarmpit.component.network.info
  (:require [material.icon :as icon]
            [material.components :as comp]
            [material.component.label :as label]
            [material.component.form :as form]
            [material.component.list.basic :as list]
            [swarmpit.component.mixin :as mixin]
            [swarmpit.component.state :as state]
            [swarmpit.component.message :as message]
            [swarmpit.component.progress :as progress]
            [swarmpit.component.service.list :as services]
            [swarmpit.url :refer [dispatch!]]
            [swarmpit.ajax :as ajax]
            [swarmpit.routes :as routes]
            [sablono.core :refer-macros [html]]
            [rum.core :as rum]))

(enable-console-print!)

(defn- network-services-handler
  [network-id]
  (ajax/get
    (routes/path-for-backend :network-services {:id network-id})
    {:on-success (fn [{:keys [response]}]
                   (state/update-value [:services] response state/form-value-cursor))}))

(defn- network-handler
  [network-id]
  (ajax/get
    (routes/path-for-backend :network {:id network-id})
    {:state      [:loading?]
     :on-success (fn [{:keys [response]}]
                   (state/update-value [:network] response state/form-value-cursor))}))

(defn- delete-network-handler
  [network-id]
  (ajax/delete
    (routes/path-for-backend :network-delete {:id network-id})
    {:on-success (fn [_]
                   (dispatch!
                     (routes/path-for-frontend :network-list))
                   (message/info
                     (str "Network " network-id " has been removed.")))
     :on-error   (fn [{:keys [response]}]
                   (message/error
                     (str "Network removing failed. " (:error response))))}))

(def form-driver-opts-render-metadata
  {:primary   (fn [item] (:name item))
   :secondary (fn [item] (:value item))})

(defn- section-general
  [{:keys [id stack networkName driver created internal attachable ingress enableIPv6 ipam]}]
  (comp/card
    {:className "Swarmpit-form-card"
     :key       "ngc"}
    (comp/card-header
      {:title     networkName
       :className "Swarmpit-form-card-header"
       :key       "ngch"
       :action    (comp/tooltip
                    {:title "Delete network"
                     :key   "ngchadt"}
                    (comp/icon-button
                      {:aria-label "Delete"
                       :onClick    #(delete-network-handler id)}
                      (comp/svg icon/trash-path)))})
    (when (and (:subnet ipam)
               (:gateway ipam))
      (comp/card-content
        {:key "ngcci"}
        (html
          [:div {:key "ngccid"}
           [:span "The subnet is listed as " [:b (:subnet ipam)]]
           [:br]
           [:span "The gateway IP in the above instance is " [:b (:gateway ipam)]]])))
    (comp/card-content
      {:key "ngccl"}
      (form/item-labels
        [(when driver
           (label/grey driver))
         (when internal
           (label/grey "Internal"))
         (when ingress
           (label/grey "Ingress"))
         (when attachable
           (label/grey "Attachable"))
         (when enableIPv6
           (label/grey "IPv6"))]))
    (comp/card-actions
      {:key "ngca"}
      (when stack
        (comp/button
          {:size  "small"
           :key   "ngcasb"
           :color "primary"
           :href  (routes/path-for-frontend :stack-info {:name stack})}
          "See stack")))
    (comp/divider
      {:key "ngd"})
    (comp/card-content
      {:key   "ngccf"
       :style {:paddingBottom "16px"}}
      (form/item-date created nil)
      (form/item-id id))))

(defn- section-driver
  [{:keys [driver options]}]
  (comp/card
    {:className "Swarmpit-card"
     :key       "ndc"}
    (comp/card-header
      {:className "Swarmpit-table-card-header"
       :title     "Driver options"
       :key       "ndch"})
    (comp/card-content
      {:className "Swarmpit-table-card-content"
       :key       "ndcc"}
      (when (not-empty options)
        (rum/with-key
          (list/list
            form-driver-opts-render-metadata
            options
            nil) "ndccl")))))

(defn- init-form-state
  []
  (state/set-value {:loading? true} state/form-state-cursor))

(def mixin-init-form
  (mixin/init-form
    (fn [{{:keys [id]} :params}]
      (init-form-state)
      (network-handler id)
      (network-services-handler id))))

(rum/defc form-info < rum/static [{:keys [network services]}]
  (comp/mui
    (html
      [:div.Swarmpit-form
       [:div.Swarmpit-form-context
        (comp/grid
          {:container true
           :spacing   40}
          (comp/grid
            {:item true
             :key  "ngg"
             :xs   12
             :sm   6}
            (section-general network))
          (when (not-empty (:options network))
            (comp/grid
              {:item true
               :key  "nog"
               :xs   12
               :sm   6}
              (section-driver network)))
          (when (not-empty services)
            (comp/grid
              {:item true
               :key  "nsg"
               :xs   12}
              (services/linked services))))]])))

(rum/defc form < rum/reactive
                 mixin-init-form
                 mixin/subscribe-form [_]
  (let [state (state/react state/form-state-cursor)
        item (state/react state/form-value-cursor)]
    (progress/form
      (:loading? state)
      (form-info item))))