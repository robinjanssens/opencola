(ns ^:figwheel-hooks opencola.web-ui.view.search 
  (:require
   [opencola.web-ui.location :as location] 
   [opencola.web-ui.view.common :refer [button-component select-menu]]
   [reagent.core :as r]))

(defn search-box [query! on-enter]
  (fn []
    [:div.search-wrapper
     [:div.form-wrapper
      [button-component {:class "search-button" :icon-class "icon-search"} #(on-enter @query!)]
      [:input.reset.text-input
       {:type "text"
        :name "search-input"
        :value @query!
        :placeholder "Search..."
        :on-change #(reset! query! (-> % .-target .-value))
        :on-keyUp #(when (= (.-key %) "Enter")
                     (on-enter @query!))}]]]))

(defn persona-select-menu [config page personas! persona! on-select!]
  (let [persona-list (cons {:name "All" :id ""} (:items @personas!))
        current-persona (if @persona! {:id @persona!} {:id "" :name "All"})] 
    [select-menu {:class "persona-select-menu"} persona-list current-persona :name :id on-select!]))

(defn header-menu 
  [menu-open?!] 
  [:div.menu
   [:div.toggled-element.overlay {:data-visible @menu-open?! :on-click #(reset! menu-open?! false)}]
   [:div.toggled-element.container {:data-visible @menu-open?!}
    [:div.menu-control
     [:span.title "Menu"]
     [button-component {:class "action-button" :icon-class "icon-close"}
      #(swap! menu-open?! not)]
     ]
    [button-component {:class "menu-item" :text "Feed" :icon-class "icon-feed"}  #(location/set-page! :feed)]
    [button-component {:class "menu-item" :text "Personas" :icon-class "icon-persona"} #(location/set-page! :personas)]
    [button-component {:class "menu-item" :text "Peers" :icon-class "icon-peers"} #(location/set-page! :peers)] 
    [button-component {:class "menu-item" :text "Settings" :icon-class "icon-settings"} #(location/set-page! :settings)]
    [:a.reset.menu-item.button-component {:href "help/help.html" :target "_blank"}
     [:span.button-icon {:class "icon-help"}]
     [:span "Help"]]
    [button-component {:class "menu-item caution-color" :text "Log out" :icon-class "icon-logout"} #(location/set-location "/logout")]]]
  )

(defn header-actions [page adding-item?! menu-open?!] 
  [:div.container.header-actions
   (when (= page :feed)
     [:div.container
      [button-component {:class "action-button" :icon-class "icon-new-post"} #(swap! adding-item?! not)]
      [button-component {:class "action-button" :icon-class "icon-peers"} #(location/set-page! :peers)]])
   (when (= page :peers)
     [:div.container
      [button-component {:class "action-button" :icon-class "icon-new-peer"} #(swap! adding-item?! not)]
      [button-component {:class "action-button" :icon-class "icon-feed"} #(location/set-page! :feed)]])
   (when (= page :personas)
     [:div.container
      [button-component {:class "action-button" :icon-class "icon-new-persona"} #(swap! adding-item?! not)]
      [button-component {:class "action-button" :icon-class "icon-feed"} #(location/set-page! :feed)]])
   [button-component {:class "action-button" :icon-class "icon-menu"} #(swap! menu-open?! not)]])


(defn search-header [page personas! persona! on-persona-select query! on-enter adding-item?!]
  (let [menu-open?! (r/atom false)]
   (fn []
     [:nav.nav-bar
      [:div.container.mr-a
       [:a.fs-0 {:href "#/feed"} [:img.logo {:src "../img/pull-tab.png"}]] 
       [persona-select-menu {:show-all? true :show-manage? true} page personas! persona! on-persona-select]
       ]
      [search-box query! on-enter]
      [header-actions page adding-item?! menu-open?!] 
      [header-menu menu-open?!]])))


