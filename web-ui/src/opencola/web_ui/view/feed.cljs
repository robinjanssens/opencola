(ns opencola.web-ui.view.feed
  (:require [cljs.reader :as reader]
            [clojure.string :as string]
            [lambdaisland.uri :refer [uri]]
            [opencola.web-ui.app-state :as state]
            [opencola.web-ui.util :refer [distinct-by]]
            [opencola.web-ui.common :refer [toggle-atom]]
            [opencola.web-ui.location :as location]
            [opencola.web-ui.model.feed :as model :refer [upload-files
                                                          upload-result-to-attachments]]
            [opencola.web-ui.view.attachments :refer [attachments-preview item-attachments distinct-attachments]]
            [opencola.web-ui.view.comments :refer [comment-control
                                                   comment-edit-control
                                                   item-comments]]
            [opencola.web-ui.view.common :refer [action-img hidden-file-input upload-progress
                                                 image-divider inline-divider error-control help-control
                                                 md->component select-files-control simple-mde text-input]]
            [opencola.web-ui.view.likes :refer [item-likes like-edit-control]]
            [opencola.web-ui.view.persona :refer [persona-select]]
            [opencola.web-ui.view.saves :refer [item-saves save-item]]
            [opencola.web-ui.view.search :as search]
            [opencola.web-ui.view.tags :refer [item-tags item-tags-summary
                                               item-tags-summary-from-string]]
            [reagent.core :as reagent :refer [atom]]))


;; TODO: Look at https://github.com/Day8/re-com

(defn get-feed [persona-id query feed! on-error]
  (model/get-feed
   nil
   persona-id
   query
   #(reset! feed! %)
   #(on-error %)))

(defn authority-actions-of-type [authority-id type item]
  (filter #(= authority-id (:authorityId %)) (-> item :activities type)))

(defn tags-as-string [authority-id item]
  (string/join " " (map :value (authority-actions-of-type authority-id :tag item))))


(defn update-feed-item [feed! view-item]
  (let [entity-id (:entityId view-item)
        updated-feed (update-in
                      @feed!
                      [:results]
                      #(map (fn [i] (if (= entity-id (:entityId i)) view-item i)) %))]
    (reset! feed! updated-feed)))

;; TODO - Use https://clj-commons.org/camel-snake-kebab/
;; Be careful with like
(defn edit-item
  ([]
   {:entityId ""
    :name ""
    :imageUri ""
    :description ""
    :like nil
    :attachments []
    :tags ""
    :comment ""})
  ([authority-id item]
   (let [summary (:summary item)
         likes (-> item :activities :like)]
     {:entityId (:entityId item)
      :name (:name summary)
      :imageUri (:imageUri summary)
      :description (:description summary)
      :like (some #(when (= authority-id (:authorityId %)) (reader/read-string (:value %))) likes)
      :attachments (-> item :activities :attach)
      :tags (tags-as-string authority-id item)
      :comment ""})))

(defn edit-control [editing?!]
  [:span.edit-entity {:on-click #(reset! editing?! true)} (action-img "edit")])

(defn get-item [feed entity-id]
  (->> feed :results (some #(when (= entity-id (:entityId %)) %))))

(defn action-summary [persona-id! key action-expanded? activities on-click]
  (let [actions (key activities)
        expanded? (key action-expanded?)
        highlight (some #(= @persona-id! (:authorityId %)) actions)]
    [:span
     [:span {:class (when highlight "highlight") :on-click on-click} (action-img (name key))]
     [:span {:on-click #(toggle-atom (map second action-expanded?) expanded?)} " " (count actions)
      (action-img (if @expanded? "hide" "show"))]]))

(defn attachment-summary [persona-id! action-expanded? activities on-change]
  (let [input-id (str (random-uuid))
        distinct-activities {:attach (distinct-attachments persona-id! (:attach activities))}]
    [:span
     [hidden-file-input input-id on-change]
     [action-summary persona-id! :attach action-expanded? distinct-activities #(.click (js/document.getElementById input-id))]]))

(defn update-display-entity [persona-id feed! edit-item on-error]
  (model/update-entity
   (:context @feed!)
   persona-id
   edit-item
   #(update-feed-item feed! %)
   #(on-error %)))

(defn update-edit-entity [persona-id feed! edit-item! on-success on-error]
  (model/update-entity
   (:context @feed!)
   persona-id
   @edit-item!
   (fn [i] 
     (update-feed-item feed! i)
     (on-success))
   #(on-error %)))

(defn like-item [persona-id feed! item on-error]
  (let [edit-item (edit-item persona-id item)]
    (update-display-entity persona-id feed! (update-in edit-item [:like] #(if % nil true)) on-error)))

;; TODO - Combing with tags-edit-control?
(defn tags-control [persona-id! feed! item tagging?!]
  (let [edit-item!  (atom (edit-item @persona-id! item))
        error! (atom nil)]
    (fn []
      (let [on-save (fn [] (update-edit-entity @persona-id! feed! edit-item! #(reset! tagging?! false) #(reset! error! %)))]
        (when @tagging?!
          [:div.tags-edit-control
           [:div.field-header "Tags:"]
           [:input.tags-text
            {:type "text"
             :value (:tags @edit-item!)
             :on-KeyUp #(case (-> % .-keyCode)
                          13 (on-save)
                          27 (reset! tagging?! false)
                          false)
             :on-change #(swap! edit-item! assoc-in [:tags] (-> % .-target .-value))}]
           [error-control error!]
           [:button {:on-click on-save} "Save"] " "
           [:button {:on-click #(reset! tagging?! false)} "Cancel"] " "])))))


(defn item-activities [persona-id! personas! feed! item editing?!]
  (let [action-expanded? (apply hash-map (mapcat #(vector % (atom false)) [:save :like :tag :comment :attach]))
        tagging? (atom false)
        commenting? (atom false)
        uploading?! (atom false)
        progress! (atom 0)
        context (:context @feed!)
        update-feed-item #(update-feed-item feed! %)
        error! (atom nil)
        on-error #(reset! error! %)
        preview-fn? (fn [] (every? #(not @%) (map second action-expanded?)))]
    (fn []
      (let [entity-id (:entityId item)
            activities (:activities item)]
        [:div.activities-summary
         (when personas!
           [:span [persona-select personas! persona-id!] inline-divider])
         [action-summary persona-id! :save action-expanded? activities #(save-item context @persona-id! item update-feed-item on-error)]
         inline-divider
         [action-summary persona-id! :like action-expanded? activities #(like-item @persona-id! feed! item on-error)]
         inline-divider
         [action-summary persona-id! :tag action-expanded?  activities #(swap! tagging? not)]
         inline-divider
         [attachment-summary
          persona-id!
          action-expanded?
          activities
          #(model/add-attachments context @persona-id! entity-id
                                  % (fn [p] (reset! uploading?! true) (reset! progress! p))
                                  (fn [i] (update-feed-item i) (reset! uploading?! false)) on-error)]
         inline-divider
         [action-summary persona-id! :comment action-expanded? activities #(swap! commenting? not)]
         inline-divider
         [edit-control editing?!]
         [:div.activity-block
          [upload-progress uploading?! progress!]
          [tags-control persona-id! feed! item tagging?]
          [comment-control context persona-id! (get-item @feed! entity-id) nil "" commenting? update-feed-item]
          [item-saves (:save action-expanded?) (:save activities)]
          [item-likes (:like action-expanded?) (:like activities)]
          [item-tags (:tag action-expanded?) (:tag activities)]
          [item-comments
           context persona-id!
           (get-item @feed! entity-id)
           (:comment activities) preview-fn?
           (:comment action-expanded?)
           update-feed-item]
          [item-attachments 
           persona-id!
           (:attach action-expanded?)
           (:attach activities)
           (fn [data-id]
             (model/delete-attachment context @persona-id! entity-id data-id #(update-feed-item %) #(reset! error! %)))]
          [error-control error!]]]))))

(defn item-name [summary]
  (let [item-uri (:uri summary)
        host (:host (uri item-uri))
        name (or (:name summary) (:hostName summary))]
    (when (seq name)
      (if (empty? item-uri)
        [:div.item-name name]
        [:div.item-name
         [:a.item-link {:href (str item-uri) :target "_blank"} name]
         [:div.item-host host]]))))

(defn item-image [summary]
  (let [item-uri (:uri summary)
        image-uri (:imageUri summary) 
        img [:img.item-img {:src image-uri}]
        posted-by-image-uri ( :postedByImageUri summary)]
    (if (not-empty image-uri)
      [:div.item-img-box
       (if (empty? item-uri)
         img
         [:a {:href item-uri :target "_blank"} img])]
      (when (not-empty posted-by-image-uri)
        [:img.posted-by-img {:src posted-by-image-uri}]))))

(defn display-feed-item [persona-id! personas! feed! item editing?!]
  (let [summary (:summary item)]
    (fn []
      [:div.feed-item
       [item-name summary] 
       [item-tags-summary (-> item :activities :tag)]
       [:div.item-body
        [item-image summary]
        [md->component {:class "item-desc"}  (:description summary)]] 
       [attachments-preview (-> item :activities :attach) true] 
       [:div.posted-by "Posted by: " (:postedBy summary)]
       [item-activities persona-id! personas! feed! item editing?!]])))

(defn on-change [item! key]
  #(swap! item! assoc-in [key] %))

(defn name-edit-control [name on-change]
  [:div.item-name
   [:div.field-header "Title:"]
   [text-input name on-change]])

(defn image-uri-edit-control [edit-item!]
  (let [image-uri (:imageUri @edit-item!)]
    [:div.item-uri-edit-control
     [:div.item-img-box
      (when (seq image-uri)
        [:img.item-img {:src image-uri}])]
     [:div.item-image-url
      [:div.field-header "Image URL:"]
      [:input.item-img-url
       {:type "text"
        :value (:imageUri @edit-item!)
        :on-change #(swap! edit-item! assoc-in [:imageUri] (-> % .-target .-value))}]]]))

(defn tags-edit-control [tags on-change]
  [:div.tags-edit-control
   [:div.field-header "Tags:"]
   [text-input tags on-change]])

(defn description-edit-control [edit-item! state!]
  [:div.description-edit-control
   [:div.item-desc
    [simple-mde (str (:entityId @edit-item!) "-desc") "Type your post here (or paste a url) ..." (:description @edit-item!) state!]]])

(defn attach-files [edit-item! persona-id! fs on-progress on-success on-error] 
  (upload-files
   @persona-id!
   fs
   on-progress
   (fn [r]
     (swap! edit-item! update-in [:attachments] (fn [as] (distinct-by :id (concat as (upload-result-to-attachments r)))))
     (on-success))
   #(on-error %)))

;; TODO: Put error in separate variable - then create and manage edit-iten only in here
(defn edit-item-control [personas! persona-id! item edit-item! error! on-save on-cancel on-delete]
  (let [description-state! (atom nil)
        comment-state! (atom nil)
        expanded?! (atom false)
        tagging?! (atom false)
        commenting?! (atom false)
        uploading?! (atom false)
        progress! (atom 0)
        on-change (partial on-change edit-item!)]
    (fn []
      (let [name-expanded? (or @expanded?! (seq (:name @edit-item!)))
            image-url-expanded? (or @expanded?! (seq (:imageUri @edit-item!)))
            deletable? (some #(= @persona-id! (:authorityId %)) (-> item :activities :save))]
        [:div.feed 
         [:div.feed-item
          (when name-expanded?
            [name-edit-control (:name @edit-item!) (on-change :name)])
          [item-tags-summary-from-string (:tags @edit-item!)] 
          (when image-url-expanded?
            [image-uri-edit-control edit-item!])
          (when (or name-expanded? image-url-expanded?)
            [:div.field-header "Description:"])
          [description-edit-control edit-item! description-state!]
          [attachments-preview (:attachments @edit-item!) true] 
          [:div.activities-summary
           (when personas!
             [:span [persona-select personas! persona-id!] inline-divider])
           [:span {:on-click #(swap! expanded?! not)} [action-img "expand"]] inline-divider
           [like-edit-control edit-item!] inline-divider
           [:span {:on-click #(swap! tagging?! not)} [action-img "tag"]] inline-divider
           [select-files-control 
            (action-img "attach") 
            (fn [fs]
              (reset! uploading?! true )
              (attach-files 
               edit-item! 
               persona-id!  
               fs 
               #(reset! progress! %) 
               #(reset! uploading?! false)
               #(reset! error! %)))] 
           inline-divider
           [:span {:on-click #(swap! commenting?! not)} [action-img "comment"]]]
          [:div.activity-block
           [upload-progress uploading?! progress!]
           (when @tagging?!
             [tags-edit-control (:tags @edit-item!) (on-change :tags)])
           (when @commenting?!
             [:div.item-comment-edit
              [comment-edit-control (:entity-id @edit-item!) nil comment-state!]])]
          [:div.edit-control-buttons
           [:button {:on-click (fn []
                                ; nil check on comment-state! needed, since it may not be opened
                                 (when @comment-state! (swap! edit-item! assoc-in [:comment] (.value @comment-state!)))
                                 (swap! edit-item! assoc-in [:description] (.value @description-state!))
                                 (on-save @persona-id!))} "Save"] " "
           [:button {:on-click on-cancel} "Cancel"] " "
           (when deletable?
             [:button.delete-button {:on-click on-delete} "Delete"])
           (error-control error!)]]]))))


(defn delete-feed-item [feed! entity-id]
  (swap! feed! update-in [:results] (fn [results] (remove #(= (:entityId %) entity-id) results))))


(defn delete-entity [persona-id feed! editing?! item on-error]
  (let [entity-id (:entityId item)]
    (model/delete-entity
     (:context @feed!)
     persona-id
     entity-id
     (fn [item]
       (if (empty? item) ;; Item may not be deletable, if you don't own it. Should hide it
         (delete-feed-item feed! entity-id)
         (update-feed-item feed! item))
       (when editing?! (reset! editing?! false)))
     #(on-error %))))


;; TODO: Use keys to get 
(defn edit-feed-item [personas! persona-id! feed! item editing?!]
  (let [edit-item! (atom (edit-item @persona-id! item))
        error! (atom nil)]
    (edit-item-control
     personas!
     persona-id!
     item
     edit-item!
     error!
     (fn []
       (update-edit-entity @persona-id! feed! edit-item! #(reset! editing?! false) #(reset! error! %)))
     #(reset! editing?! false)
     (fn [] ( delete-entity @persona-id! feed! editing?! item #(reset! error! %))))))


(defn feed-item [persona-id personas! feed! item]
  (let [editing?! (atom false)
        persona-id! (atom (or persona-id (:personaId item)))]
    (fn []
      (if @editing?!
        [edit-feed-item personas! persona-id! feed! item editing?!]
        [display-feed-item persona-id! personas! feed! item editing?!]))))


(defn feed-status [feed!]
  [:div.feed-status
   (let [query (:query @feed!)]
     (when (not-empty query)
       (str (count (:results @feed!)) " results for '" query "'")))])

(defn feed-list [persona-id! personas! feed!]
  (when @feed!
    [:div.feed
     [feed-status feed!]
     (doall (for [item  (:results @feed!)]
              ^{:key [item @persona-id!]}
              [feed-item @persona-id! (when (not @persona-id!) personas!) feed! item]))]))

(defn header-actions [creating-post?!]
  [:div.header-actions
   [:img.header-icon {:src  "../img/new-post.png" :on-click #(swap! creating-post?! not)}] 
   image-divider
   [:img.header-icon {:src  "../img/peers.png" :on-click #(location/set-page! :peers)}]
   image-divider
   [help-control]])




(defn prepend-feed-item [feed! view-item]
  (swap! feed! update-in [:results] #(into [view-item] %)))

(defn new-post [persona-id feed! creating-post!? edit-item! on-error]
  (model/new-post
   (:context @feed!)
   persona-id
   @edit-item!
   #(do
      (prepend-feed-item feed! %)
      (reset! creating-post!? false))
   #(on-error %)))

(defn feed-instructions []
  [:div.feed-item 
   [:div
    [:img.nola-img {:src "img/nola.png"}]
    [:div.item-name  "Snap! Your feed is empty!"]
    [:div
     [:ul.instruction-items
      [:li "Add posts by clicking the new post icon (" [:img.header-icon {:src  "../img/new-post.png"}] ") on the top right"] 
      [:li "Add peers by clicking the peers icon (" [:img.header-icon {:src  "../img/peers.png"}] ") on the top right"]
      [:li "Add posts using the browser extension"]]]]])

;; TODO: Make parameter ordering consistent. Some places have persona-id then personas, others
;; are the other way around.
;; TODO: Only pass atoms when necessary. There are some cases where the personas! is passed, when @personas! 
;; would suffice
(defn feed-page [feed! personas! persona-id! on-persona-select query! on-search]
  (let [creating-post?! (atom false)]
    (fn []
      [:div#opencola.feed-page
       [search/search-header
        :feed
        personas!
        persona-id!
        on-persona-select
        query!
        on-search
        (partial header-actions creating-post?!)]
       [error-control (state/error!)]
       (when @creating-post?!
         (let [edit-item! (atom (edit-item))
               error! (atom nil)]
           [:div
            [edit-item-control
             (when (not @persona-id!) personas!)
             (atom (or @persona-id! (-> @personas! :items first :id)))
             nil
             edit-item!
             error!
             (fn [persona-id] (new-post persona-id feed! creating-post?! edit-item! #(reset! error! %)))
             #(reset! creating-post?! false) nil]
       [error-control error!]]))
       (if (or (= @feed! {}) (not-empty (:results @feed!)) (not-empty @query!))
         [feed-list persona-id! personas! feed!]
         [feed-instructions])])))
