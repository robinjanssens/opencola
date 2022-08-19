(defproject opencola/web-ui "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :min-lein-version "2.7.1"

  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/clojurescript "1.10.773"]
                 [reagent "0.10.0" ]
                 [cljs-ajax "0.8.4"]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [lambdaisland/uri "1.13.95"]
                 [clj-commons/secretary "1.2.4"]
                 [mvxcvi/alphabase "2.1.1"]
                 [cljsjs/simplemde "1.11.2-0"] ; https://cljsjs.github.io/ & https://github.com/sparksuite/simplemde-markdown-editor
                 [markdown-to-hiccup "0.6.2"]]

  :source-paths ["src"]

  :aliases {"fig"       ["trampoline" "run" "-m" "figwheel.main"]
            "fig:build" ["trampoline" "run" "-m" "figwheel.main" "-b" "dev" "-r"]
            "fig:min"   ["run" "-m" "figwheel.main" "-O" "advanced" "-bo" "dev"]
            "fig:test"  ["run" "-m" "figwheel.main" "-co" "test.cljs.edn" "-m" "opencola.test-runner"]}

  :profiles {:dev {:dependencies [[com.bhauman/figwheel-main "0.2.15"]
                                  [com.bhauman/rebel-readline-cljs "0.1.4"]]
                   
                   :resource-paths ["target"]
                   ;; need to add the compiled assets to the :clean-targets
                   :clean-targets ^{:protect false} ["target"]}})

