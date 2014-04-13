(ns leiningen.hiccup-watch
  (:require [taoensso.timbre :as timbre]
            [clojure.string :as string]
            [filevents.core :as filevents]
            [hiccup.core :as hiccup]
            [hiccup.page :as hpage] ))


(defn gen-html [from-path to-path]

  ;; i. read all files
  ;; ii. from an input directory
  (def idx (slurp from-path))
  (def idx-form (read-string idx))

  (def result-page (hpage/html5 {} idx-form))

  ;; iii. spit out result page(s)
  ;; iv. to a configured location
  (timbre/debug (str "writing out file: " to-path))
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


    (if-not (and input-final output-final)
      (timbre/error "Both :input-dir and :output-dir not specified. Exiting")
      (do

        (timbre/debug "hiccup-watch started...")

        ;; Watch the directory
        (filevents/watch
         (fn [kind file]

           (if-not (= :delete kind)
             (if (or (re-find #"\.edn" (.getName file))
                     (re-find #"\.clj" (.getName file)))
               (do

                 (timbre/debug "hiccup-watch[" kind "]: " file)
                 (let [input-file-extension (if (re-find #"\.edn" (.getName file)) #"\.edn" #"\.clj")
                       output-file-name (str output-final
                                             java.io.File/separator
                                             (string/replace-first (. file getName) input-file-extension "")
                                             ".html")]
                   (gen-html file output-file-name))))))
         input-final)


        ;; Kludge to block leiningen from exiting... yes, I know it's bad
        (loop []
          (Thread/sleep 100)
          (recur))))))
