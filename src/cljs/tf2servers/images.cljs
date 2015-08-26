(ns tf2servers.images
  (:require [om-tools.dom :as dom :include-macros true]))

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
      tag))

  (defn tag-has-icon? [tag] (contains? tag->icon tag)))

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
