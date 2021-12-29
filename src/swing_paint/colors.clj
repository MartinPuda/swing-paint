(ns swing-paint.colors
  (:import (java.awt Color))
  (:gen-class))

(defn paint-predefined-colors
  "Returns list of 28 Color objects with given RGB values"
  []
  (let [rgb-values
        [[0 0 0]
         [132 132 132]
         [132 0 0]
         [132 132 0]
         [0 132 0]
         [0 132 132]
         [0 0 132]
         [132 0 132]
         [132 132 66]
         [0 66 66]
         [0 132 255]
         [0 66 132]
         [132 0 255]
         [132 66 0]
         ;second line
         [255 255 255]
         [198 198 198]
         [255 0 0]
         [255 255 0]
         [0 255 0]
         [0 255 255]
         [0 0 255]
         [255 0 255]
         [255 255 132]
         [0 255 132]
         [132 255 255]
         [132 132 255]
         [255 0 132]
         [255 132 66]]]
    (for [[r g b] rgb-values]
      (Color. ^int r
              ^int g
              ^int b))))