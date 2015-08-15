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
            :width 16
            :height 16}))

(def lock-img (make-icon "images/lock.png" "Password protected"))
(def shield-img (make-icon "images/shield.png" "VAC enabled"))
(def film-img (make-icon "images/film.png" "Replays enabled"))
(def bot-img (make-icon "images/bot.png" "Bots on server"))
(def players-img (make-icon "images/people.png" "Players on server"))
(def hlstatsx-img (make-icon "images/hlstatsx.png" "HLstatsX:CE"))
(def dice-img (make-icon "images/dice.png" "Random Crits"))
(def nodice-img (make-icon "images/nodice.png" "No Crits"))
(def chat-img (make-icon "images/chat.png" "Alltalk"))
(def tf2-img (make-icon "images/tf2.png" "Team Fortress"))
(def tf2true-img (make-icon "images/tf2true.png" "TFTrue"))

(let [tag->icon
      {"replays" film-img
       "HLstatsX:CE" hlstatsx-img
       "nocrits" nodice-img
       "alltalk" chat-img}]
  (defn maybe-tag->icon
    "Maps common server tags to an appropriate icon, or just returns
  the tag if no mapping exists."
    [tag]
    (if (contains? tag->icon tag)
      (tag->icon tag)
      tag)))

(let [game->icon
      {"Team Fortress" tf2-img
       "TFTrue  " tf2true-img}]
  (defn maybe-game->icon
    "Maps common server game modes to an appropriate icon, or just
  returns the raw game mode if no mapping exists."
    [gamemode]
    (if (contains? game->icon gamemode)
      (game->icon gamemode)
      gamemode)))

(defn- game-connect [url]
  (set! (.-location js/window) (str "steam://connect/" url)))

(defonce app-state
  (atom {:servers []}))

(defn- toggle-sort-order [sort-order] (if (= sort-order :up) :down :up))

(defcomponent server-table [data owner]
  (init-state [_]
    {:selected-url nil
     :selected-col nil
     :sort-order :up})
  (render-state [_ {:keys [selected-url selected-col sort-order]}]
    (dom/table
     (dom/thead
      (dom/tr
       (map-indexed
        (fn [idx [class content attrs]]
          (dom/td (merge
                   {:class (str class
                                (when (= idx selected-col)
                                  " selected"))
                    :on-click
                    (fn [e]
                      (let [selected-col (om/get-state owner :selected-col)]
                        (if (not= idx selected-col)
                          (do (om/set-state! owner :selected-col idx)
                              (om/set-state! owner :sort-order :up))
                          (do (om/update-state! owner :sort-order
                                                toggle-sort-order)))))}
                   attrs)
                  content
                  (dom/div {:class (str "col-sort-arrow " (name sort-order))})))
        [["icon-col" lock-img]
         ["icon-col" shield-img]
         ["title" "Server Title"]
         ["game" "Game"]
         ["bots" bot-img]
         ["players" players-img {:col-span 3}]
         ["map" "Map"]
         ["tags" "Tags"]])))
     (dom/tbody
      (for [server (:servers data)
            :let [url (str (:ip server) ":" (:port server))
                  selected (= url selected-url)]]
        (dom/tr
         {:data-server url
          :on-double-click #(game-connect url)
          :on-click #(om/set-state! owner :selected-url url)
          :class (when selected "selected")}
         (dom/td {:class "icon-col"}
                 (when (= (get-in server [:info :visibility]) 1)
                   lock-img))
         (dom/td {:class "icon-col"}
                 (when (= (get-in server [:info :vac-enabled?]) 1)
                   shield-img))
         (dom/td {:class "name"} (get-in server [:info :name]))
         (dom/td {:class "game"}
                 (maybe-game->icon (get-in server [:info :game])))
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
                       (map maybe-tag->icon)
                       (sort-by #(if (string? %1)
                                   (str/lower-case %1)
                                   (:alt %1)))
                       (map #(dom/li {:class (when-not (string? %1) "icon")}
                                     %1))
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
