(ns leiningen.hiccup-watch
  (:import [java.util.concurrent
            ThreadFactory ScheduledThreadPoolExecutor TimeUnit Executors ExecutorService])
  (:require [clojure.string :as string]
            [filevents.core :as filevents]
            [hiccup.page :as hpage]))


(defn gen-html [from-path to-path]

  ;; i. read all files
  ;; ii. from an input directory
  (def idx (slurp from-path))
  (def idx-form (read-string idx))

  (def result-page (hpage/html5 {} idx-form))

  ;; iii. spit out result page(s)
  ;; iv. to a configured location
  (println (str "writing out file: " to-path))
  (spit to-path result-page))


(defn hiccup-watch [project & args]

  (let [input-dir (-> project :hiccup-watch :input-dir)
        output-dir (-> project :hiccup-watch :output-dir)

        inputargs-in-map (apply array-map args)
        input-args-withrealkeywords (into {}
                                          (for [[k v] inputargs-in-map]
                                            (let [intermediate-key (if (= \: (first k))
                                                                     (string/replace-first k #":" "")
                                                                     k)
                                                  final-key (keyword intermediate-key)]
                                              [final-key v])))
        input-override (:input-dir input-args-withrealkeywords)
        output-override (:output-dir input-args-withrealkeywords)

        input-final (if input-override input-override input-dir)
        output-final (if output-override output-override output-dir)]

    (println (str "input-final: " input-final))
    (println (str "output-final: " output-final))



    (if-not (and input-final output-final)
      (println "ERROR: both :input-dir and :output-dir not specified. Exiting")
      (do
        (filevents/watch
         (fn [kind file]

           (println "kind: " kind)
           (println "file: " file)

           (if-not :delete
             (let [output-file-name (str output-final (string/replace-first (. file getName) #"\.edn" ""))]
               (gen-html file output-file-name))))
         "public"))

      #_(do

        (defn generate-id
          "generate system wide unique ID"
          []
          (.. (java.rmi.dgc.VMID.) toString (replaceAll ":" "") (replaceAll "-" "")))

        (defn- random-thread-name [prefix]
          (str prefix "-" (generate-id)))

        (defn thread-factory [thread-name-prefix]
          (proxy [ThreadFactory] []
            (newThread [runfn]
              (Thread. runfn (random-thread-name thread-name-prefix)))))

        (defn scheduled-executor [pool-size thread-name-prefix]
          (->> thread-name-prefix
               thread-factory
               (ScheduledThreadPoolExecutor. pool-size)))

        (defonce SCHEDULED-EXECUTOR (scheduled-executor (.availableProcessors (Runtime/getRuntime))
                                                        "hiccup-watch"))

        (defn- safe-fn [runfn descriptor]
          #(try
             (runfn)
             (catch Exception e
               (println (str "Error periodically executing " descriptor)))))

        (defn run-periodically [descriptor runfn time-period-millis]
          (.scheduleAtFixedRate SCHEDULED-EXECUTOR
                                (safe-fn runfn descriptor)
                                time-period-millis
                                time-period-millis
                                TimeUnit/MILLISECONDS))

        (run-periodically "thing" (fn [] (println "Xxx...")) 500))
      #_(do
        (let [service-fn (fn []
                           (println "Watching... ")
                           (filevents/watch
                            (fn [kind file]

                              (println "kind: " kind)
                              (println "file: " file)

                              (if-not :delete
                                (let [output-file-name (str output-final (string/replace-first (. file getName) #"\.edn" ""))]
                                  (gen-html file output-file-name))))
                            "public"))
              executor-service-pool (java.util.concurrent.Executors/newFixedThreadPool 2)
              _ (.invokeAll executor-service-pool [service-fn])]

          )))))
