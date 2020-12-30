(ns spicybot.env)

(def state (atom {}))

(defn init-state [connection-ch message-ch event-ch]
  (reset! state {:message message-ch
                 :connection connection-ch
                 :event event-ch
                 :movie {:suggestions {}}}))
