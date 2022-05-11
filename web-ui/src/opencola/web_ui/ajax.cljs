(ns ^:figwheel-hooks opencola.web-ui.ajax
  (:require
   [ajax.core :as ajax] ; https://github.com/JulianBirch/cljs-ajax
   [opencola.web-ui.config :as config]))


(defn resolve-service-url [path]
  ;; TODO: Use url construction methods
  (str (config/get-service-url) path))


(defn GET [path success-handler error-handler]
  (ajax/GET 
   (resolve-service-url path)
   {
    :keywords? true
    :response-format :json
    :handler success-handler
    :error-handler error-handler})) 


(defn POST [path body success-handler error-handler] 
  (ajax/POST (resolve-service-url path)
        {:params body
         :keywords? true
         :format :json
         :response-format :json
         :handler success-handler
         :error-handler error-handler}))

(defn DELETE [path success-handler error-handler]
  (ajax/DELETE (resolve-service-url path) 
       {:handler success-handler
        :error-handler error-handler}))