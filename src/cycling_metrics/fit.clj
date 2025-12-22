(ns cycling-metrics.fit
  (:import [com.garmin.fit Decode MesgBroadcaster RecordMesgListener RecordMesg]
           [java.io InputStream]))

(defn parse-fit [file]
  (let [records (atom [])
        decode (Decode.)
        broadcaster (MesgBroadcaster. decode)
        listener (reify RecordMesgListener
                   (onMesg [_ mesg]
                     (let [power (when (and mesg (.getPower mesg)) (Short/toUnsignedInt (.getPower mesg)))
                           hr    (when (and mesg (.getHeartRate mesg)) (Short/toUnsignedInt (.getHeartRate mesg)))]
                       (when (or power hr)
                         (swap! records conj {:power (or power 0) :heart-rate (or hr 0)})))))]
    (.addListener broadcaster listener)
    (with-open [in (clojure.java.io/input-stream file)]
       ;; The read method returns boolean (success)
       (.read decode in broadcaster))
    {:records @records}))
