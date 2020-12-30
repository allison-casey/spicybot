(ns spicybot.reaction
  (:require [spicybot.env :as env]
            [discljord.messaging :as msg]))

(def ^:private reactions {:thumbs-up "👍"
                          :x "❌"})

(defn react
  [channel-id message-id reaction]
  (when (contains? reactions reaction)
    (msg/create-reaction! (:message @env/state)
                          channel-id
                          message-id
                          (get reactions reaction))))
