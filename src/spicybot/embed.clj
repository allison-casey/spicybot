(ns spicybot.embed
  (:require [cuerdas.core :as cuerdas]))

(defn movie-list [suggestions]
  (let [fields (for [{:keys [user movie]} (vals suggestions)]
                 {:name user
                  :value movie})]
    {:title "Movies"
     :fields fields}))

(defn help []
  (let [suggest {:name "!suggest"
                 :value "Suggest a movie for movie club"}
        suggested {:name "!suggested"
                   :value "Get a list of all suggested movies"}
        help {:name "!help"
              :value "Show this help message"}
        pickmovie {:name "!pickmovie"
                   :value "(Admin Only) Select a suggested movie at random"}
        clearmovies {:name "!clearmovies"
                     :value "(Admin Only) Clear all suggested movies"}]
    {:title "Spicybot Help"
     :fields [help suggest suggested pickmovie clearmovies]}))

(defn pick-movie [movie user]
  (let [description (str "Congratulations to %s! Your movie ***%s*** "
                         "has been chosen as this week's "
                         "*Movie Club* movie!")]
    {:title "This Week's Movie"
     :description (format description user (cuerdas/title movie))}))
