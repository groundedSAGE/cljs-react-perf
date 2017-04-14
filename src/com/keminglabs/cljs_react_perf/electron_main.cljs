(ns com.keminglabs.cljs-react-perf.electron-main
  (:require-macros [com.keminglabs.cljs-react-perf.macros :refer [p pp timeout]]))

(enable-console-print!)

(def ^js/electron electron
  (js/require "electron"))
(def app (.-app electron))
(def BrowserWindow (.-BrowserWindow electron))
(def ipc (.-ipcMain electron))

;;Don't use OS X dock icon, since it can steal focus away from our diligent hero and their terminal.
(def dock (.-dock app))
(when (exists? dock)
  (.hide dock))

;;Don't close electron when all windows are closed.
;;Must subscribe to this event to prevent Electron default behavior, which is to quit the app
(.on app "window-all-closed" (fn []))


;;Some stats from
;;https://github.com/clojure-cookbook/clojure-cookbook/blob/master/01_primitive-data/1-20_simple-statistics.asciidoc

(defn mean
  [coll]
  (let [sum (apply + coll)
        count (count coll)]
    (if (pos? count)
      (/ sum count)
      0)))


(defn standard-deviation
  [coll]
  (let [avg (mean coll)
        squares (for [x coll]
                  (let [x-avg (- x avg)]
                    (* x-avg x-avg)))
        total (count coll)]
    (-> (/ (apply + squares)
           (- total 1))
        (Math/sqrt))))


(defn start-benchmark! [[app-name optimization-mode] cb]
  (let [^js/BrowserWindow w (BrowserWindow. (clj->js {:show false}))]
    (.loadURL w (str "file://" js/__dirname "/../" optimization-mode ".html#" app-name))

    (p "\n-------------------")
    (p (str app-name " (" optimization-mode ")") )

    (.once ipc "measurements"
           (fn [e raw-measurements]

             ;;now that we have measurements back, close the browser window.
             (.close w)

             (let [measurements (js->clj raw-measurements :keywordize-keys true)
                   render-timings (->> measurements
                                       (drop 1) ;;Ignore the initial render time --- only want to measure re-render time
                                       (mapcat :tufte)
                                       (map :duration))]

               ;; TODO: I can't take any of this memory stuff seriously until I can find a way to disable the garbage collector or otherwise get deterministic measurements
               ;; (prn "Memory growth per render (kB): "
               ;;      (->> (map :private-memory measurements)
               ;;           (partition 2 1)
               ;;           (mapv #(apply - (reverse %)))))

               (p (str (Math/round (mean render-timings))
                       " ± "
                       (Math/round (standard-deviation render-timings))))

               ;;(pp render-timings)

               ;;invoke callback to indicate that we're done
               (when cb (cb)))))))


(defn next-benchmark!
  [benchmarks]
  (let [[x & xs] benchmarks]
    (if x
      (start-benchmark! x #(next-benchmark! xs))
      (.quit app))))


(def optimization-modes
  ""

  )

(.on app "ready"
     (fn []

       (p "starting benchmarks")

       (->>
        ["app-1" "app-2" "app-3" "app-4" "app-5" "app-6" "app-7" "app-8" "app-9" "app-10" "app-11" "app-12" "app-13" "app-14" "app-15" "app-16"]
        ;;["app-13" "app-14" "app-15" "app-16" "app-17"]
        (mapcat (fn [app-name]
                  (map vector
                       (repeat app-name)
                       ;;These should correspond to the names of the html files
                       ["simple"
                        "simple2"
                        "advanced"])))
        next-benchmark!)



       ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
       ;;Uncomment this if you need to do some debuggin'

       ;; (do
       ;;   (when (exists? dock)
       ;;     (.show dock))
       ;;   (let [^js/BrowserWindow w (BrowserWindow. (clj->js {:show true}))]
       ;;     (.openDevTools (.-webContents w))
       ;;     (.loadURL w (str "file://" js/__dirname "/../app.html#app-5"))))
       ))
