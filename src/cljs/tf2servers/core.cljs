(ns tf2servers.core
  (:require [om.core :as om :include-macros true]
            [om-tools.dom :as dom :include-macros true]
            [om-tools.core :refer-macros [defcomponent]]
            [ajax.core :refer [GET]]
            [clojure.string :as str]
            [tf2servers.images :as img]))

(enable-console-print!)

(defn pad
  "Ensure a given seq is always at least n elements long, adding as
  many pad elements to the end as necessary."
  [n pad coll]
  (take n (concat coll (repeat pad))))

(defn- game-connect [url]
  (set! (.-location js/window) (str "steam://connect/" url)))

(defonce app-state
  (atom {:servers [] :selected-url ""}))

(defn- toggle-sort-order [sort-order] (if (= sort-order :up) :down :up))

(defn locale-compare [s1 s2]
  (cond
    (string? s1) (.localeCompare s1 s2)
    (string? s2) (.localeCompare s2 s1)
    :else 0))

(defn make-list-comparator [comp-fn]
  (fn [coll1 coll2]
    (let [size (max (count coll1) (count coll2))
          coll1 (pad size nil coll1)
          coll2 (pad size nil coll2)])
    (or (first (remove zero? (map comp-fn coll1 coll2)))
        0)))

(let [bool-comp identity
      str-comp locale-compare
      str-list-comp (make-list-comparator str-comp)
      int-comp compare
      int-pair-comp (make-list-comparator int-comp)]
  (def ^:private server-table-columns
    [{:class "icon-col"
      :header img/lock-img
      :comp bool-comp}
     {:class "icon-col"
      :header img/shield-img
      :comp bool-comp}
     {:class "title"
      :header "Server Title"
      :comp str-comp}
     {:class "game"
      :header "Game"
      :comp str-comp}
     {:class "bots"
      :header img/bot-img
      :comp int-comp}
     {:class "players"
      :header img/players-img
      :comp int-pair-comp}
     {:class "map"
      :header "Map"
      :comp str-comp}
     {:class "tags"
      :header "Tags"
      :comp str-list-comp}]))

(defn- server->table-row [server]
  (let [{game-map :map
         :keys [password? vac-enabled? name game bots players max-players
                keywords]
         :as info} (:info server)
         players-str (str players "/" max-players)]
    {:url (str (:ip server) ":" (:port server))
     :server
     [{:class "icon-col" :content (when password? img/lock-img)
       :sort-val password?}
      {:class "icon-col" :content (when vac-enabled? img/shield-img)
       :sort-val vac-enabled?}
      {:class "name" :content name
       :sort-val name}
      {:class "game" :content (img/maybe-game->icon game)
       :sort-val game}
      {:class "bots"
       :content (if (zero? bots) "-" bots)
       :attrs {:title (str bots " bots")}
       :sort-val bots}
      {:class "players" :content players-str :attrs {:title players-str}
       :sort-val [players max-players]}
      {:class "map" :content game-map
       :sort-val game-map}
      (let [keywords
            (->> keywords
                 (map str/lower-case)
                 (group-by img/tag-has-icon?)
                 ((juxt #(get % true) #(get % false)))
                 (map sort)
                 flatten)]
        {:class "keys"
         :sort-val keywords
         :content
         (dom/ul {:title (str/join ", " keywords)}
           (->> keywords
                (map img/maybe-tag->icon)
                (map #(dom/li {:class (when-not (string? %1) "icon")}
                              %1))
                (interpose " ")))})]}))

(defn update-sort-order! [data owner col-idx]
  (let [selected-col (om/get-state owner :selected-col)]
    (if (not= col-idx selected-col)
      (do (om/set-state! owner :selected-col col-idx)
          (om/set-state! owner :sort-order :up))
      (om/update-state! owner :sort-order toggle-sort-order)))
  (let [key-fn #(get-in % [:server col-idx :sort-val])
        compare-fn' (:comp (server-table-columns col-idx))
        compare-fn (if (= :up (om/get-state owner :sort-order))
                     compare-fn' #(- (compare-fn' %1 %2)))]
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

        ;; Careful only to recalculate the header widths for actual
        ;; data: if loading placeholder rows are displayed they cannot
        ;; be used.
        col-widths (when (= 8 (count row))
                       (doall (for [td row] (.-clientWidth td))))

        row-height (.-clientHeight (first row))
        view-height (.-clientHeight scroller)
        scroll-top (.-scrollTop scroller)]
    (doseq [[k d] [[:col-widths col-widths]
                   [:row-height row-height]
                   [:view-height view-height]
                   [:scroll-top scroll-top]]]
      (when d (maybe-set-state owner k d)))))

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
