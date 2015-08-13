(ns tf2servers.core
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [ajax.core :refer [GET]]
            [clojure.string :as str]))

(enable-console-print!)

(defn- make-icon [src desc]
  (dom/img {:src src
            :alt desc
            :title desc
            :width 12
            :height 12}))

(def lock-img (make-icon "images/lock.png" "Password protected"))
(def shield-img (make-icon "images/shield.png" "VAC enabled"))
(def film-img (make-icon "images/film.png" "Replays enabled"))
(def bot-img (make-icon "images/bot.png" "Bots on server"))
(def players-img (make-icon "images/people.png" "Players on server"))


(let [tag->icon
      {"replays" film-img}]
  (defn maybe-tag->icon
    "Maps common server tags to an appropriate icon, or just returns
  the tag if no mapping exists."
    [tag]
    (if (contains? tag->icon tag)
      (tag->icon tag)
      tag)))

(defn- game-connect [url]
  (set! (.-location js/window) (str "steam://connect/" url)))

(defonce app-state
  (atom {:servers []}))

(defcomponent server-table [data owner]
  (init-state [_] {:selected-url nil})
  (render-state [_ {:keys [selected-url]}]
    (dom/table
     (dom/thead
      (dom/tr (dom/td {:class "icon-col"} lock-img)
              (dom/td {:class "icon-col"} shield-img)
              (dom/td "Server Title")
              (dom/td "Game")
              (dom/td {:class "bots"} bot-img)
              (dom/td {:class "players" :col-span 3} players-img)
              (dom/td "Map")
              (dom/td "Tags")))
     (dom/tbody
      (for [server (:servers data)
            :let [url (str (:ip server) ":" (:port server))
                  selected (= url selected-url)]]
        (dom/tr
         {:data-server url
          :on-double-click #(game-connect url)
          :on-click #(om/set-state! owner :selected-url url)
          :class (when selected "selected")}
         (dom/td (when (= (get-in server [:info :visibility]) 1)
                   lock-img))
         (dom/td (when (= (get-in server [:info :vac-enabled?]) 1)
                   shield-img))
         (dom/td {:class "name"} (get-in server [:info :name]))
         (dom/td {:class "game"} (get-in server [:info :game]))
         (dom/td {:class "bots"}
                 (let [bot-cnt (get-in server [:info :bots])]
                   (if (zero? bot-cnt) "-" [bot-cnt bot-img])))
         (dom/td {:class "player-cnt"} (get-in server [:info :players]))
         (dom/td {:class "player-sep"} "/")
         (dom/td {:class "max-players"} (get-in server [:info :max-players]))
         (dom/td {:class "map"} (get-in server [:info :map]))
         (dom/td {:class "keys"}
                 (dom/ul
                  (->> (get-in server [:info :keywords])
                       (map name)
                       sort
                       (map maybe-tag->icon)
                       (map dom/li)
                       (interpose " "))))))))))

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
