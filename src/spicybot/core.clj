(ns spicybot.core
  (:gen-class)
  (:require ;; Clojure
            [clojure.edn :as edn]
            [clojure.core.async :refer [chan close!]]
            ;; Discljord
            [discljord.messaging :as discord-rest]
            [discljord.connections :as discord-ws]
            [discljord.formatting :refer [mention-user]]
            [discljord.events :refer [message-pump!]]
            [discljord.messaging :as msg]
            [discljord.permissions :as perm]
            ;; Third Party
            [cuerdas.core :as str]
            ;; Project
            [spicybot.embed :as embed]
            [spicybot.env :as env]
            ))

(def bot-state (atom nil))
(def state (atom {:movie {:suggestions {}}}))
(def bot-id (atom nil))
(def config (edn/read-string (slurp "config.edn")))


(defn random-response [user]
  (str (rand-nth (:responses config)) ", " (mention-user user) \!))

;; **  Spicy Helpers
(def ^:private reactions
  {:thumbs-up "👍"
   :x "❌"})

(defn react!
  [channel-id message-id reaction]
  (when (contains? reactions reaction)
    (msg/create-reaction! (:rest @bot-state)
                          channel-id
                          message-id
                          (get reactions reaction))))

(defn add-suggestion
  [author-id username movie]
  (swap! state
         assoc-in
         [:movie :suggestions author-id]
         {:movie movie
          :user username}))

(defn admin? [user-id guild-id]
  (let [user-roles (:roles @(msg/get-guild-member! (:rest @bot-state) guild-id user-id))]
    (->> @(msg/get-guild-roles! (:rest @bot-state) guild-id)
         (filter #(some #{(:id %)} user-roles))
         (some #(perm/has-permission-flag? :administrator (:permissions %)))
         boolean)))

;; ** Spicybot Command Multidef
(defmulti handle-spicy-cmd (fn [command args event-data] command))

(defmethod handle-spicy-cmd "help"
  [_ args {:keys [channel-id] :as event-data}]
  (msg/create-message!
   (:rest @bot-state)
   channel-id
   :embed (embed/help)))

(defn suggest
  "suggest a movie"
  [channel-id message-id author-id username movie]
  (if-not (str/empty-or-nil? movie)
    (do (add-suggestion author-id username movie)
        (react! channel-id message-id :thumbs-up))
    (react! channel-id message-id :x)))


(defmethod handle-spicy-cmd "suggest"
  [_ args {:keys [channel-id id author] :as data}]
  (let [{author-id :id, username :username} author
        movie (str/join " " args)]
    (suggest channel-id id author-id username movie)))

(defmethod handle-spicy-cmd "suggestas"
  [_ args {:keys [channel-id id author mentions guild-id]}]
  (if (admin? (:id author) guild-id)
    (let [[_ & movie] args
          as (first mentions)
          movie (str/join " " movie)]
      (suggest channel-id id (:id as) (:username as) movie))
    (react! channel-id id :x)))

(defmethod handle-spicy-cmd "suggested"
  [_ _ {:keys [channel-id id]}]
  (msg/create-message!
   (:rest @bot-state)
   channel-id
   :embed (embed/movie-list (-> @state :movie :suggestions))))

(defmethod handle-spicy-cmd "pickmovie"
  [_ _ {:keys [channel-id id author guild-id] :as event-data}]
  (if (and (admin? (:id author) guild-id)
           (seq (get-in @state [:movie :suggestions])))
    (let [{:keys [movie user]} (-> @state :movie :suggestions vals rand-nth)]
      (msg/create-message!
       (:rest @bot-state)
       channel-id
       :embed (embed/pick-movie movie user)))
    (react! channel-id id :x)))

(defmethod handle-spicy-cmd "clearmovies"
  [_ _ {:keys [channel-id id author guild-id] :as event-data}]
  (if (admin? (:id author) guild-id)
    (do (swap! state assoc-in [:movie :suggestions] {})
        (react! channel-id id :thumbs-up))
    (react! channel-id id :x)))

(defmethod handle-spicy-cmd :default
  [command args event-data])

;; ** Discljord Event MultiDef
(defmulti handle-event (fn [type _data] type))

;; ** Server Message Creation Event
(defmethod handle-event :message-create
  [_ {:keys [channel-id author mentions content] :as data}]
  (when (and (not (:bot author)) (str/starts-with? content "!"))
    (let [[command & args] (str/split content #"\s+")
          command (str/strip-prefix command "!")]
      (handle-spicy-cmd command args data))))

;; ** Bot Boilerplate
(defmethod handle-event :ready
  [_ _]
  (discord-ws/status-update! (:gateway @bot-state) :activity (discord-ws/create-activity :name (:playing config))))

(defmethod handle-event :default [_ _])

(defn start-bot! [token & intents]
  (let [event-channel (chan 100)
        gateway-connection (discord-ws/connect-bot! token event-channel :intents (set intents))
        rest-connection (discord-rest/start-connection! token)]
    {:events  event-channel
     :gateway gateway-connection
     :rest    rest-connection}))

(defn stop-bot! [{:keys [rest gateway events] :as _state}]
  (discord-rest/stop-connection! rest)
  (discord-ws/disconnect-bot! gateway)
  (close! events))

(defn -main [& args]
  (reset! bot-state (start-bot! (:token config) :guild-messages))
  (reset! bot-id (:id @(discord-rest/get-current-user! (:rest @bot-state))))
  (try
    (message-pump! (:events @bot-state) handle-event)
    (finally (stop-bot! @bot-state))))
