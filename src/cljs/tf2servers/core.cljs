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
  (atom {:servers [] :selected-url ""}))

(defn- toggle-sort-order [sort-order] (if (= sort-order :up) :down :up))

(def ^:private server-table-columns
  [{:class "icon-col"
    :header lock-img}
   {:class "icon-col"
    :header shield-img}
   {:class "title"
    :header "Server Title"}
   {:class "game"
    :header "Game"}
   {:class "bots"
    :header bot-img}
   {:class "players"
    :header players-img}
   {:class "map"
    :header "Map"}
   {:class "tags"
    :header "Tags"}])

(defn- server->table-row [server]
  (let [{game-map :map
         :keys [password? vac-enabled? name game bots players max-players]
         :as info} (:info server)
         players-str (str players "/" max-players)]
    {:url (str (:ip server) ":" (:port server))
     :server
     [{:class "icon-col" :content (when password? lock-img)}
      {:class "icon-col" :content (when vac-enabled? shield-img)}
      {:class "name" :content name}
      {:class "game" :content (maybe-game->icon game)}
      {:class "bots"
       :content (if (zero? bots) "-" bots)
       :attrs {:title (str bots " bots")}}
      {:class "players" :content players-str :attrs {:title players-str}}
      {:class "map" :content game-map}
      {:class "keys"
       :content
       (dom/ul
         (->> (:keywords info)
              (map maybe-tag->icon)
              (sort-by #(if (string? %1)
                          (str/lower-case %1)
                          (:alt %1)))
              (map #(dom/li {:class (when-not (string? %1) "icon")}
                            %1))
              (interpose " ")))}]}))

(defn update-sort-order! [data owner col-idx]
  (let [selected-col (om/get-state owner :selected-col)]
    (if (not= col-idx selected-col)
      (do (om/set-state! owner :selected-col col-idx)
          (om/set-state! owner :sort-order :up))
      (om/update-state! owner :sort-order toggle-sort-order)))
  (let [key-fn #(get-in % [:server col-idx :content])
        compare-fn (if (= :up (om/get-state owner :sort-order))
                     #(.localeCompare (str %1) (str %2))
                     #(- (.localeCompare (str %1) (str %2))))]
    (om/transact! data :servers #(sort-by key-fn compare-fn %))))

(defn update-col-sizes! [data owner]
  (let [row (-> (om/get-node owner "table-rows")
                .-children
                (aget 0)
                .-children
                array-seq)
        widths (doall (for [td row] (.-clientWidth td)))]
    (om/set-state! owner :col-widths widths)))

(defcomponent server-table-row [data owner]
  (render [_]
    (let [{data :data
           {:keys [url server]} :server} data]
      (dom/tr
       {:data-server url
        :on-double-click #(game-connect url)
        :on-click #(om/update! data :selected-url url)
        :class (when (= url (:selected-url data)) "selected")}
       (map (fn [{:keys [class content attrs]}]
              (dom/td
               (merge {:class class
                       :title (when (string? content) content)}
                      attrs)
               content))
            server)))))

(defcomponent server-table [data owner]
  (init-state [_]
    {:selected-col nil
     :sort-order :up
     :col-widths (repeat 10 nil)})
  (render-state [_ {:keys [selected-url selected-col
                           sort-order col-widths]}]
    (dom/div {:class "server-table-container"}
      (dom/ul {:class "table-header" :ref "table-header"}
        (map
         (fn [idx {:keys [class header attrs]} width]
           (let [attrs (merge
                        attrs
                        {:class (str class
                                     " header"
                                     (when (= idx selected-col)
                                       " selected"))
                         :on-click #(update-sort-order! data owner idx)}
                        (when width {:style {:width width}}))]
             (dom/li attrs
                     header
                     (dom/div {:class (str "col-sort-arrow "
                                           (name sort-order))}))))
         (range)
         server-table-columns
         col-widths))
      (dom/div {:class "server-table-scroller"}
        (dom/table {:class "server-table"}
          (dom/colgroup
            (map #(dom/col {:class %1})
                 ["icon-col" "icon-col" "title" "game" "icon-col bot-col"
                  "players" "map" "tags"]))
          (dom/tbody {:ref "table-rows"}
            (map (fn [server]
                   (om/build server-table-row
                             {:data data
                              :server server}
                             {:key (:url server)}))
                 (:servers data)))))))
  (did-mount [_]
    (let [update-sizes! #(update-col-sizes! data owner)]
      (.addEventListener js/window "resize" update-sizes!)
      (update-sizes!)))
  (did-update [_ _ _]
    (update-col-sizes! data owner)))

(defcomponent filter-controls [data owner]
  (render [_]
    (dom/header)))

(defcomponent app [data owner]
  (init-state [_]
    (GET "/api/servers"
        {:handler
         (fn [servers]
           (println "Recved " (count servers) " servers.")
           (om/update! data :servers (map server->table-row servers)))
         :error-handler
         (fn [err]
           (println "Fetch error:" err)
           (.log js/console err))})
    {})
  (render [_]
    (dom/div
      (om/build filter-controls data)
      (om/build server-table data))))

(defn main []
  (om/root app
           app-state
           {:target (. js/document (getElementById "app"))}))
