(ns ^:figwheel-hooks opencola.web-ui.view.feed 
  (:require
   [clojure.string :as string :refer [lower-case]]
   [goog.dom :as gdom]
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [cljs-time.coerce :as c]
   [cljs-time.format :as f]
   [lambdaisland.uri :refer [uri]]
   [cljs.reader :as reader]
   [opencola.web-ui.config :as config]
   [opencola.web-ui.view.search :as search]
   [opencola.web-ui.model.error :as error]
   [opencola.web-ui.model.feed :as feed]))

;; TODO: Look at https://github.com/Day8/re-com

(def inline-divider [:span.divider " | "])

(defn get-feed [query feed!]
  (feed/get-feed query feed!))

(defn format-time [epoch-second]
  (f/unparse (f/formatter "yyyy-MM-dd hh:mm") (c/from-long (* epoch-second 1000))))

(defn authority-actions-of-type [authority-id type item]
  (filter #(= authority-id (:authorityId %)) (-> item :activities type)))

(defn tags-as-string [authority-id item]
  (string/join " " (map :value (authority-actions-of-type authority-id :tag item))))

;; TODO - Use https://clj-commons.org/camel-snake-kebab/
(defn edit-item [authority-id item]
  (let [summary (:summary item)
        likes (-> item :activities :like)
        tags (-> item :activities :tag)] 
    {:entityId (:entityId item)
     :name (:name summary)
     :imageUri (:imageUri summary)
     :description (:description summary)
     :like (some #(if (= authority-id (:authorityId %)) (reader/read-string (:value %))) likes)
     :tags (tags-as-string authority-id item)}))


(defn action-img [name]
  [:img.action-img {:src (str "../img/" name ".png") :alt name :title name}])

(defn action-item [action]
  [:span.action-item (action-img (:type action))
   (when-let [value (:value action)] 
     [:pre (str value)])])


(defn activity [action-counts action-value]
  (when-let [count (action-counts action-value)]
    ^{:key action-value} [:span.activity-item count " " (apply action-item action-value) " " 
                          (name (first action-value)) " "]))


(defn data-url [host data-id]
  (when data-id
    (str (if (not= host "") "http://") host "/data/" data-id))) 


(defn edit-control [editing?!]
  [:span.delete-entity {:on-click #(reset! editing?! true)} (action-img "edit")])


(defn comment-control [feed! entity-id comment-id text expanded?!]
  (let [text! (atom text)]
    (fn []
      (if @expanded?!
        [:div.item-comment
         [:div.item-comment-text
          [:textarea.comment-text-edit {:type "text"
                                        :value @text!
                                        :on-change #(reset! text! (-> % .-target .-value))}]
          [:button {:on-click #(feed/add-comment feed! entity-id comment-id @text! expanded?!)} "Save"] " "
          [:button {:on-click #(reset! expanded?! false)} "Cancel"]
          (if comment-id
            [:button.delete-button {:on-click #(feed/delete-comment feed! entity-id comment-id)} "Delete"])]]))))

(defn item-comment [feed! entity-id comment-action]
(let [editing?! (atom false)]
  (fn []
    (let [root-authority-id (:authorityId @feed!)
          {authority-id :authorityId
           authority-name :authorityName 
           epoch-second :epochSecond 
           text :value
           comment-id :id} comment-action]
      [:div.item-comment 
       [:span.item-attribution 
        authority-name " " (format-time epoch-second) " "
        (if (= authority-id root-authority-id)
          [:span {:on-click #(reset! editing?! true)} [action-img "edit"]])
        ":"]
       (if @editing?!
         [comment-control feed! entity-id comment-id text editing?!]
         [:div.item-comment-text text])]))))

(defn item-comments [preview-fn? expanded?! comment-actions feed! entity-id]
  (let [preview? (preview-fn?)
        comment-actions (if preview? (take 3 comment-actions) comment-actions)]
    (if (or @expanded?! (and preview? (not-empty comment-actions)))
      [:div.item-comments
       [:span {:on-click (fn [] (swap! expanded?! #(not %)))} "Comments"]
       #_[comment-control feed! entity-id nil "" expanded?!] 
       (doall (for [comment-action comment-actions]
                ^{:key comment-action} [item-comment feed! entity-id comment-action]))])))

(defn item-save [save-action]
  (let [{authority-name :authorityName 
         epoch-second :epochSecond 
         data-id :id
         host :host} save-action]
    [:div.item-save 
     [:span.item-attribution (str authority-name " " (format-time epoch-second))] " "
     (if data-id
       [:span
        [:a.action-link  {:href (str (data-url host data-id) "/0.html") :target "_blank"} [action-img "archive"]]
        inline-divider
        [:a.action-link  {:href (data-url host data-id) :target "_blank"} [action-img "download"]]])]))
 

(defn item-saves [expanded?! save-actions]
  (if @expanded?!
    [:div.item-saves 
     "Saves:"
     (doall (for [save-action save-actions]
              ^{:key save-action} [item-save save-action]))]))

(defn tag [name]
  [:span.tag name])


(defn item-tag [tag-action]
  (let [{authority-name :authorityName
         epoch-second :epochSecond} tag-action] 
    [:div.item-tag
     [:span.item-attribution (str authority-name " " (format-time epoch-second))] " " (tag (:value tag-action))]))

(defn item-list [name expanded?! actions item-action]
  (if @expanded?!
    [(keyword (str "div.item-" (lower-case name)))
     [:div.list-header (str name ":")]
     (doall (for [action actions]
              ^{:key action} [item-action action]))]))

(defn item-tags-summary [actions]
  (when (not-empty actions)
    [:div.tags 
     (interpose " " 
                (doall (for [action actions]
                         ^{:key action} [tag (:value action)])))]))


(defn item-tags [preview-fn? expanded?! actions]
  (if @expanded?!
    [:div.item-tags
     [:div.list-header "Tags:"]
     (doall (for [action actions]
              ^{:key action} [item-tag action]))]))


(defn item-like [like-action]
  (let [{authority-name :authorityName 
         epoch-second :epochSecond } like-action]
    [:div.item-like 
     [:span.item-attribution (str authority-name " " (format-time epoch-second))]]))

;; TODO: Templatize this - same for saves and comments
(defn item-likes [expanded?! like-actions]
  (if (and @expanded?!) 
      [:div.item-likes 
       "Likes:"
       (doall (for [like-action like-actions]
                ^{:key like-action} [item-like like-action]))]))


(defn toggle-atom [atoms! atom!]
  (doall(for [a! atoms!]
          (if (not= a! atom!)
            (reset! a! false))))
  (swap! atom! #(not %)))

(defn action-summary [feed! key action-expanded? activities on-click]
  (let [authority-id (:authorityId @feed!)
        actions (key activities)
        expanded? (key action-expanded?)
        highlight (some #(= authority-id (:authorityId %)) actions)]
    [:span 
     [:span {:class (if highlight "highlight") :on-click on-click} (action-img (name key))]
     [:span {:on-click #(toggle-atom (map second action-expanded?) expanded?)} " " (count actions) 
      (action-img (if @expanded? "hide" "show"))]])) 

(defn save-item [feed! item]
  (let [authority-id (:authorityId @feed!)
        actions (-> item :activities :save)
        saved? (some #(= authority-id (:authorityId %)) actions)]
    (if (not saved?)
      (feed/save-entity feed! item))))

(defn like-item [feed! item]
   (let [authority-id (:authorityId @feed!)
         actions (-> item :activities :like)
         edit-item (edit-item authority-id item)
         like (some #(if (= authority-id (:authorityId %)) (reader/read-string (:value %))) actions)]
     (feed/update-entity feed! nil (update-in edit-item [:like ] #(if % nil true)))))


;; TODO - Combing with tags-edit-control?
(defn tags-control [feed! item tagging?!]
  (let [edit-item!  (atom (edit-item (:authorityId @feed!) item))]
    (fn []
      (if @tagging?!
        [:div.tags-edit-control
         [:div.field-header "Tags:"]
         [:input.tags-text
          {:type "text"
           :value (:tags @edit-item!)
           :on-change #(swap! edit-item! assoc-in [:tags] (-> % .-target .-value))}]
         [:button {:on-click #(feed/update-entity feed! tagging?! @edit-item!)} "Save"] " "
         [:button {:on-click #(reset! tagging?! false)} "Cancel"] " "]))))

(defn item-activities [feed! item editing?!]
  (let [action-expanded? (apply hash-map (mapcat #(vector % (atom false)) [:save :like :tag :comment]))
        commenting? (atom false)
        tagging? (atom false)
        preview-fn? (fn [] (every? #(not @%) (map second action-expanded?)))] 
    (fn [] 
      (let [entity-id (:entityId item)
            activities (:activities item)]  
        [:div.activities-summary
         [action-summary feed! :save action-expanded? activities #(save-item feed! item)]
         inline-divider
         [action-summary feed! :like action-expanded? activities #(like-item feed! item)] 
         inline-divider
         [action-summary feed! :comment action-expanded? activities #(swap! commenting? not)]
         inline-divider
         [action-summary feed! :tag action-expanded?  activities #(swap! tagging? not)] 
         inline-divider
         [edit-control editing?!] 
         [comment-control feed! entity-id nil "" commenting?]
         [tags-control feed! item tagging?]
         [item-saves (:save action-expanded?) (:save activities)]
         [item-likes (:like action-expanded?) (:like activities)]
         [item-comments preview-fn? (:comment action-expanded?) (:comment activities) feed! entity-id]
         [item-tags preview-fn? (:tag action-expanded?) (:tag activities)] ]))))


(defn display-feed-item [feed! item editing?!]
  (let [entity-id (:entityId item)
        summary (:summary item)
        item-uri (uri (:uri summary))]
    (fn []
      [:div.feed-item
       [:div.item-name 
        [:a.item-link {:href (str item-uri) :target "_blank"} (:name summary)]
        [:div.item-host (:host item-uri)]]
       [:div.item-body 
        [:div.item-img-box 
         [:a {:href (str item-uri) :taget "_blank"} [:img.item-img {:src (:imageUri summary)}]]]
        [:p.item-desc (:description summary)]]
       [item-tags-summary (-> item :activities :tag)]
       [item-activities feed! item editing?!]])))


(defn name-edit-control [edit-item!]
       [:div.item-name
        [:div.field-header "Name:"]
        [:input.item-link
         {:type "text"
          :value (:name @edit-item!)
          :on-change #(swap! edit-item! assoc-in [:name] (-> % .-target .-value))}]])

(defn image-uri-edit-control [edit-item!]
  [:div.item-uri-edit-control
   [:div.item-img-box 
    [:img.item-img {:src (str (:imageUri @edit-item!))}]]
   [:div.item-image-url 
    [:div.field-header "Image URL:"]
    [:input.item-img-url
     {:type "text"
      :value (:imageUri @edit-item!)
      :on-change #(swap! edit-item! assoc-in [:imageUri] (-> % .-target .-value))}]]])


(defn tags-edit-control [edit-item!]
  [:div.tags-edit-control
   [:div.field-header "Tags:"]
   [:input.tags-text
    {:type "text"
     :value (:tags @edit-item!)
     :on-change #(swap! edit-item! assoc-in [:tags] (-> % .-target .-value))}]])

(defn description-edit-control [edit-item!]
  [:div.description-edit-control
       [:div.field-header "Description:"]
       [:p.item-desc [:textarea.item-desc-edit
                      {:type "text"
                       :value (:description @edit-item!)
                       :on-change #(swap! edit-item! assoc-in [:description] (-> % .-target .-value))}]]])


;; TODO: Use keys to get 
(defn edit-feed-item [feed! item editing?!]
  (let [authority-id (:authorityId @feed!)
        entity-id (:entityId item)
        summary (:summary item)
        item-uri (uri (:uri summary))
        edit-item! (atom (edit-item (:authorityId @feed!) item))
        deletable? (some #(= authority-id (:authorityId %)) (-> item :activities :save))]
    (fn []
      [:div.feed-item
       [name-edit-control edit-item!]
       [image-uri-edit-control edit-item!]
       [description-edit-control edit-item!]
       [tags-edit-control edit-item!]
       (if deletable?
           [:button.delete-button {:on-click #(feed/delete-entity feed! editing?! entity-id)} "Delete"])])))



(defn feed-item [feed! item]
  (let [editing?! (atom false)]
    (fn []
      (if @editing?! [edit-feed-item feed! item editing?!] [display-feed-item feed! item editing?!]))))


(defn feed-status [feed!]
  [:div.feed-status 
   (let [query (:query @feed!)]
     (when (not-empty query)
       (str (count (:results @feed!)) " results for '" query "'")))])

(defn feed-list [feed!]
  (if @feed!
    [:div.feed
     [feed-status feed!]
     (let [results (:results @feed!)]
       (doall (for [item results]
                ^{:key item} [feed-item feed! item])))]))

(defn feed-error [feed!]
  (if-let [e (:error @feed!)]
    [:div.error e]))


;; TODO: view model should be passed in from outside to avoid "Singleton"
(def feed! (atom {}))

(defn feed-page []
  (feed/get-feed "" feed!)
  (fn []
    [:div#opencola.feed-page
     [search/search-header #(get-feed % feed!)]
     [feed-error feed!]
     [feed-list feed!]]))
