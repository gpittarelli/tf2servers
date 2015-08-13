(ns tf2servers.core
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [ajax.core :refer [GET]]
            [clojure.string :as str]))

(enable-console-print!)

(defn- game-connect [url]
  (set! (.-location js/window) (str "steam://connect/" url)))

(defonce app-state
  (atom {:servers []}))

(defcomponent server-table [data owner]
  (init-state [_] {:selected-url nil})
  (render-state [_ {:keys [selected-url]}]
    (dom/table
     (for [server (:servers data)
           :let [url (str (:ip server) ":" (:port server))
                 selected (= url selected-url)]]
       (dom/tr
        {:data-server url
         :on-double-click #(game-connect url)
         :on-click #(om/set-state! owner :selected-url url)
         :class (when selected "selected")}
        (dom/td
         (when (= (get-in server [:info :visibility]) 1)
           (dom/img {:src "images/lock.png"
                     :alt "Password protected"
                     :width 16
                     :height 16})))
        (dom/td
         (when (= (get-in server [:info :vac-enabled?]) 1)
           (dom/img {:src "images/shield.png"
                     :alt "VAC enabled"
                     :width 16
                     :height 16})))
        (dom/td
         (when (get-in server [:info :keywords :replays])
           (dom/img {:src "images/film.png"
                     :alt "Replays enabled"
                     :width 16
                     :height 16})))
        (dom/td (get-in server [:info :name]))
        (dom/td (get-in server [:info :game]))
        (dom/td (get-in server [:info :bots]))
        (dom/td (get-in server [:info :players]))
        (dom/td (get-in server [:info :max-players]))
        (dom/td (get-in server [:info :map]))
        (dom/td
         (->> (get-in server [:info :keywords])
              (map name)
              sort
              (str/join ","))))))))

(defcomponent app [data owner]
  (init-state [_]
    (GET "/api/servers"
        {:handler #(om/update! data :servers %)
         :error-handler
         (fn [err]
           (println "Fetch error:")
           (.log js/console err))})
    {})
  (render [_]
          (om/build server-table data)))

(defn main []
  (om/root app app-state
    {:target (. js/document (getElementById "app"))}))
