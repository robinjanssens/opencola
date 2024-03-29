; Copyright 2024 OpenCola
; 
; Licensed under the Apache License, Version 2.0 (the "License");
; you may not use this file except in compliance with the License.
; You may obtain a copy of the License at
; 
;    http://www.apache.org/licenses/LICENSE-2.0
;
; Unless required by applicable law or agreed to in writing, software
; distributed under the License is distributed on an "AS IS" BASIS,
; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
; See the License for the specific language governing permissions and
; limitations under the License.

(ns opencola.web-ui.view.persona
  (:require
   [reagent.core :as r]
   [opencola.web-ui.app-state :as state]
   [opencola.web-ui.model.persona :as model]
   [opencola.web-ui.view.common :refer [input-checkbox error-control edit-control-buttons button-component text-input-component swap-atom-data! profile-img select-menu]]
   [opencola.web-ui.view.search :as search]
   [opencola.web-ui.location :as location]))

(defn init-personas [personas! on-success on-error]
  (model/get-personas
   (fn [personas] 
     (reset! personas! personas) 
     (on-success))
   #(on-error %)))

(defn create-persona [personas! persona! on-success on-error]
  (model/create-persona
   (dissoc @persona! :error)                          ; Switch to non-atom
   #(do
      (reset! personas!  %)
      (on-success))
   #(on-error %)))

(defn get-personas [personas! on-error]
  (model/get-personas
   #(reset! personas! %)
   #(on-error %)))

(defn update-persona [personas! persona! on-error]
  (model/update-persona
   @persona!
   #(reset! personas! %)
   #(on-error %)))

(defn delete-persona [personas! persona! on-error]
  (model/delete-persona
   @persona!
   #(reset! personas! %)
   #(on-error %)))

(defn persona-select [personas! persona-id!]
  (let [persona-list  (:items @personas!)]
    [select-menu {:class "persona-select-menu"} persona-list persona-id! :id :name #(reset! persona-id! %)]))

(defn persona-item [personas! persona]
  (let [editing?! (r/atom false)
        show-advanced?! (r/atom false)
        persona! (r/atom persona)
        error! (r/atom nil)
        on-error #(reset! error! %)]
    (fn []
      (let [image-uri (:imageUri @persona!)]
        [:div.list-item
          [profile-img image-uri (:name @persona!) (:id @persona!)]
         [:div.peer-info
          [text-input-component 
           {:value (:name @persona!) :disabled (not @editing?!) :icon-class "icon-persona" :name "persona-name" :icon-tool-tip-text "Name"} 
           #(swap-atom-data! % persona! :name)]
          
          (when (or @show-advanced?! @editing?!)
            [:div.peer-info
             [text-input-component
              {:value (:id @persona!) :disabled true :icon-class "icon-id" :name "persona-id" :icon-tool-tip-text "Id"}
              #(swap-atom-data! % persona! :id)]])
          
          [text-input-component 
           {:value (:address @persona!) :disabled (not @editing?!) :icon-class "icon-link" :name "persona-link" :icon-tool-tip-text "Link"} 
           #(swap-atom-data! % persona! :address)]
          
          [text-input-component 
           {:value (:imageUri @persona!) :disabled (not @editing?!) :icon-class "icon-photo" :name "persona-img" :icon-tool-tip-text "Profile Image"} 
           #(swap-atom-data! % persona! :imageUri)] 
          
          [input-checkbox 
           {:checked (:isActive @persona!) :disabled (not @editing?!) :icon-class "icon-refresh" :name "persona-active" :tool-tip-text "Sync"} 
           #(swap! persona! assoc-in [:isActive] (-> % .-target .-checked))]]
         
         #_(when (not @editing?!) 
          [button-component {:class "show-advanced-button" :text (str (if @show-advanced?! "Hide" "Show all"))} #(swap! show-advanced?! not)])
         
         (if @editing?!
           [edit-control-buttons {:on-save (fn [] (update-persona personas! persona! on-error))
                                  :save-disabled (= @persona! persona)
                                  :on-cancel #(do
                                                (reset! persona! persona)
                                                (reset! editing?! false))
                                  :on-delete #(delete-persona personas! persona! on-error)} 
             error!] 
           [button-component {:text "Edit" :class " edit-control-button edit-button" :name "edit-button"} #(reset! editing?! true)])]))))

(def empty-persona
  {:id ""  
   :name "" 
   :publicKey "" 
   :address "ocr://relay.opencola.net" 
   :imageUri "" 
   :isActive true})

(defn add-persona-item [personas! adding-persona?!]
  (let [persona! (r/atom empty-persona)
        error! (r/atom nil)]
    (fn []
      (let [image-uri (:imageUri @persona!)]
        [:div.list-item
         [profile-img image-uri (:name @persona!) nil]
         [:div.peer-info
          [text-input-component {:value (:name @persona!) :icon-class "icon-persona" :name "persona-name"} #(swap-atom-data! % persona! :name)]
          [text-input-component {:value (:address @persona!) :icon-class "icon-link" :name "persona-link"} #(swap-atom-data! % persona! :address)]
          [text-input-component {:value (:imageUri @persona!) :icon-class "icon-photo" :name "persona-img"} #(swap-atom-data! % persona! :imageUri)]
          [input-checkbox {:checked (:isActive @persona!) :icon-class "icon-refresh" :name "persona-active"} #(swap! persona! assoc-in [:isActive] (-> % .-target .-checked))]]
         [edit-control-buttons {:on-save (fn [] 
                                           (create-persona personas! persona! 
                                                           #(reset! adding-persona?! false) 
                                                           #(reset! error! %)))
                                :on-cancel #(reset! adding-persona?! false)} error!]]))))

(defn persona-list [personas! adding-persona?!]
  (when @personas!
    [:div.content-list.persona-list 
     (when @adding-persona?! [add-persona-item personas! adding-persona?!])
     (doall (for [persona (:items @personas!)]
              ^{:key persona} [persona-item personas! persona]
              ))]))


(defn personas-page [personas! persona! on-persona-select query! on-search!]
  (let [adding-persona?! (r/atom false)]
    (fn []
      [:div.settings-page
       [search/search-header 
        :personas
        personas! 
        persona! 
        on-persona-select 
        query! 
        on-search! 
        adding-persona?!]
       [error-control (state/error!)] 
       [:h2.text-center "Personas"]
       [persona-list  personas! adding-persona?!]])))
