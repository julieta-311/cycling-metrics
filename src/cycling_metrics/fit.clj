(ns cycling-metrics.fit
  (:import [com.garmin.fit Decode MesgBroadcaster RecordMesgListener RecordMesg]
           [java.io InputStream]))

(defn parse-fit [file]
  (let [power-data (atom [])
        decode (Decode.)
        broadcaster (MesgBroadcaster. decode)
        listener (reify RecordMesgListener
                   (onMesg [_ mesg]
                     (when (and mesg (.getPower mesg))
                       (swap! power-data conj (Short/toUnsignedInt (.getPower mesg))))))]
    (.addListener broadcaster listener)
    (with-open [in (clojure.java.io/input-stream file)]
       ;; The read method returns boolean (success)
       (.read decode in broadcaster))
    {:power @power-data}))
