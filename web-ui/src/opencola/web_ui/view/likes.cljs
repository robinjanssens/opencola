(ns opencola.web-ui.view.likes
  (:require [opencola.web-ui.time :refer [format-time]]
            [opencola.web-ui.view.common :refer [action-img]]))

(defn item-like [like-action on-click-authority]
  (let [{authority-name :authorityName
         epoch-second :epochSecond} like-action]
    [:tr.item-attribution
     [:td [:span.authority {:on-click #(on-click-authority authority-name)} authority-name]]
     [:td (format-time epoch-second)]]))

;; TODO: Templatize this - same for saves and comments
(defn item-likes [expanded?! like-actions on-click-authority]
  (when @expanded?!
    [:div.item-likes
     [:div.list-header "Likes:"]
     [:table
      [:tbody
       (doall (for [like-action like-actions]
                ^{:key like-action} [item-like like-action on-click-authority]))]]]))

(defn like-edit-control [edit-item!]
  [:span {:class (when (:like @edit-item!) "highlight")
          :on-click (fn [] (swap! edit-item! update-in [:like] #(if % nil true)))}
   (action-img "like")])