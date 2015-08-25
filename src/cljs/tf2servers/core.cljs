(ns tf2servers.core
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [ajax.core :refer [GET]]
            [clojure.string :as str]
            [tf2servers.images :as img]))

(enable-console-print!)

(defn- game-connect [url]
  (set! (.-location js/window) (str "steam://connect/" url)))

(defonce app-state
  (atom {:servers [] :selected-url ""}))

(defn- toggle-sort-order [sort-order] (if (= sort-order :up) :down :up))

(def ^:private server-table-columns
  [{:class "icon-col"
    :header img/lock-img}
   {:class "icon-col"
    :header img/shield-img}
   {:class "title"
    :header "Server Title"}
   {:class "game"
    :header "Game"}
   {:class "bots"
    :header img/bot-img}
   {:class "players"
    :header img/players-img}
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
     [{:class "icon-col" :content (when password? img/lock-img)}
      {:class "icon-col" :content (when vac-enabled? img/shield-img)}
      {:class "name" :content name}
      {:class "game" :content (img/maybe-game->icon game)}
      {:class "bots"
       :content (if (zero? bots) "-" bots)
       :attrs {:title (str bots " bots")}}
      {:class "players" :content players-str :attrs {:title players-str}}
      {:class "map" :content game-map}
      {:class "keys"
       :content
       (dom/ul
         (->> (:keywords info)
              (map img/maybe-tag->icon)
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

(defn- maybe-set-state [owner key data]
  (let [old (om/get-state owner key)]
    (when (not= old data)
      (om/set-state! owner key data))))

(defn update-measurements! [data owner]
  (let [table (om/get-node owner "server-table")
        row (-> (om/get-node owner "table-rows")
                .-children
                (aget 1)
                .-children
                array-seq)
        scroller (.-parentNode table)

        col-widths (doall (for [td row] (.-clientWidth td)))
        row-height (.-clientHeight (first row))
        view-height (.-clientHeight scroller)
        scroll-top (.-scrollTop scroller)]
    (doseq [[k d] [[:col-widths col-widths]
                   [:row-height row-height]
                   [:view-height view-height]
                   [:scroll-top scroll-top]]]
      (maybe-set-state owner k d))))

(defcomponent server-table-row [data owner]
  (display-name [_] "server-table-row")
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

(defn pad
  "Ensure a given seq is always at least n elements long, adding as
  many pad elements to the end as necessary."
  [n pad coll]
  (take n (concat coll (repeat pad))))

(defcomponent server-table [data owner]
  (display-name [_] "server-table")
  (init-state [_]
    {:selected-col nil
     :sort-order :up
     :col-widths (repeat 10 nil)
     :view-height 1
     :row-height 1
     :scroll-top 0})
  (render-state [_ {:keys [selected-url selected-col
                           sort-order col-widths
                           view-height row-height scroll-top]}]
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
        (dom/table {:class "server-table" :ref "server-table"}
          (dom/colgroup
            (map #(dom/col {:class %1})
                 ["icon-col" "icon-col" "title" "game" "icon-col bot-col"
                  "players" "map" "tags"]))
          (dom/tbody {:ref "table-rows"}
            (let [->row #(om/build server-table-row
                                   {:data data :server %}
                                   {:key (:url %)})
                  start-row (int (/ scroll-top row-height))
                  vis-row-cnt (+ 2 (int (/ view-height row-height)))
                  loading-row (dom/tr {:class "loading"}
                                (dom/td {:col-span 8}
                                  "Loading..."))]
              (println scroll-top start-row
                       vis-row-cnt view-height row-height)

              [(dom/tr {:class "top-spacer"
                        :style {:height (* start-row
                                           row-height)}})
               (->> (:servers data)
                    (drop start-row)
                    (take vis-row-cnt)
                    (map ->row)
                    (pad vis-row-cnt loading-row))
               (dom/tr {:class "bottom-spacer"
                        :style {:top (* row-height 100)}}
                 (dom/td "asdfasdf"))]))))))
  (did-mount [_]
    (let [update-sizes! #(update-measurements! data owner)]
      (.addEventListener js/window "resize" update-sizes!)
      (.addEventListener (.-parentNode (om/get-node owner "server-table"))
                         "scroll"
                         update-sizes!)
      (update-sizes!)))
  (did-update [_ _ _]
    (update-measurements! data owner)))

(defcomponent filter-controls [data owner]
  (display-name [_] "filter-controls")
  (render [_]
    (dom/header
      (dom/input {:type "number" :min 1 :max 32 :value 24 :step 1}))))

(defcomponent app [data owner]
  (display-name [_] "app")
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
