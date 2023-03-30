(ns swing-paint.state
  (:import (java.awt Color))
  (:gen-class))

(def paint-state
  (atom {}))

(defn reset-state! []
  (reset! paint-state
          {:foreground-color Color/BLACK
           :background-color Color/BLACK
           :paint            :color
           :stroke           :solid
           :texture          nil
           :tool             :pencil
           :tool-size        10
           :border-size      1
           :tool-density     10
           :drag-point       false
           :font-size        5
           :font             "Times New Roman"
           :text             ""
           :polygon-points   []
           :curve-points     []}))
