(ns swing-paint.tools
  (:import (java.awt Color Cursor BasicStroke GradientPaint Graphics2D AlphaComposite RadialGradientPaint Polygon TexturePaint Rectangle)
           (java.awt.geom Rectangle2D$Double RoundRectangle2D$Double Ellipse2D$Double Point2D Point2D$Float Path2D$Float GeneralPath))
  (:use swing-paint.state
        swing-paint.text)
  (:gen-class))

;;; tools

(defn optimized-filler
  "Function for filling for bucket tool"
  [event buffer]
  (let [x (.getX event)
        y (.getY event)
        original-color (Color. (.getRGB buffer x y))
        fill-color (:foreground-color @paint-state)
        inside? (fn [x y]
                  (and (<= 0 x (dec (.getWidth buffer)))
                       (<= 0 y (dec (.getHeight buffer)))
                       (= original-color (Color. (.getRGB buffer x y)))))
        stack (volatile! (list (list x y)))
        left-end (volatile! x)
        right-end (volatile! x)
        scan (fn [l r y]
               (let [added (volatile! false)]
                 (doseq [x (range l r)]
                   (if (not (inside? x y))
                     (vreset! added false)
                     (when (not @added)
                       (vswap! stack conj (list x y))
                       (vreset! added true))))))]
    (when (not= original-color fill-color)
      (.setCursor (.getSource event) (Cursor. Cursor/WAIT_CURSOR))
      (.setRGB buffer x y (.getRGB fill-color))
      (while (not-empty @stack)
        (let [[x y] (first @stack)]
          (vswap! stack pop)
          (vreset! left-end x)
          (vreset! right-end x)
          (while (inside? (dec @left-end) y)
            (vswap! left-end dec))
          (while (inside? (inc @right-end) y)
            (vswap! right-end inc))
          (let [grbuf (.getGraphics buffer)]
            (.setColor grbuf fill-color)
            (.drawLine grbuf @left-end y @right-end y))
          (scan @left-end (inc @right-end) (inc y))
          (scan @left-end (inc @right-end) (dec y))))
      (.setCursor (.getSource event) (Cursor. Cursor/HAND_CURSOR)))))

(defn get-stroke [kw]
  (BasicStroke.
    (@paint-state kw)
    BasicStroke/CAP_BUTT BasicStroke/JOIN_MITER 10
    ((:stroke @paint-state) {:solid       nil
                             :dotted      (float-array [5])
                             :dashed      (float-array [10])
                             :dash-dotted (float-array [10 10 5 10])})
    0))

(defn get-paint [x1 y1 x2 y2]
  (case (:paint @paint-state)
    :color (:foreground-color @paint-state)
    :gradient1 (GradientPaint. x1 y1 (:foreground-color @paint-state) x2 y2 (:background-color @paint-state))
    :gradient2 (GradientPaint. x1 y1 (:background-color @paint-state) x2 y2 (:foreground-color @paint-state))
    :gradient3 (RadialGradientPaint. ^Point2D (Point2D$Float. (- x2 (* 0.5 (- x2 x1)))
                                                              (- y2 (* 0.5 (- y2 y1))))
                                     ^float (max 1 (float (* 0.5 (- x2 x1))))
                                     ^"[F" (float-array [0 1])
                                     ^"[Ljava.awt.Color;" (into-array ((juxt :foreground-color :background-color) @paint-state)))
    :texture (if-let [texture (:texture @paint-state)]
               (TexturePaint. texture (Rectangle. x1 y1 50 50)) ;resizes texture to given Dimensions
               (:foreground-color @paint-state))))

(defn draw-polygon
  "Draws polygon to graphics."
  [event buf-image]
  (let [graphics (.getGraphics buf-image)
        x (.getX event)
        y (.getY event)]
    (swap! paint-state update :polygon-points #(conj % [x y]))
    (let [pts (:polygon-points @paint-state)
          polygon (Polygon. (int-array (map first pts))
                            (int-array (map second pts))
                            (count pts))]
      (prn pts)
      (doto graphics
        (.setColor (:foreground-color @paint-state))
        (.fillPolygon polygon)
        (.setStroke (get-stroke :tool-size))
        (.setColor (:background-color @paint-state))
        (.drawPolygon polygon)))))

(defn draw-dragged-shape
  "Draws dragged shape to graphics."
  [event buf-image kw]
  (let [[x1 y1] (:drag-point @paint-state)
        x2 (.getX event)
        y2 (.getY event)
        arcw (/ (- x2 x1) 10)
        arch (/ (- y2 y1) 10)
        obj (kw {:rectangle       (Rectangle2D$Double. x1 y1 (- x2 x1) (- y2 y1))
                 :round-rectangle (RoundRectangle2D$Double. x1 y1 (- x2 x1) (- y2 y1) arcw arch)
                 :circle          (Ellipse2D$Double. x1 y1 (- x2 x1) (- y2 y1))})]
    (doto (.getGraphics buf-image)
      (.setStroke (get-stroke :tool-size))
      (.setPaint (get-paint x1 y1 x2 y2))

      (.fill obj)
      (.setStroke (get-stroke :border-size))
      (.setColor (:background-color @paint-state))
      (.draw obj))))

(defn draw-simple-shape
  "Draws rectangle or circle to graphics."
  [event buf-image kw]
  (let [b (:tool-size @paint-state)
        x (.getX event)
        shift-x (- x (/ b 2))                               ;pocatecni bod shora
        y (.getY event)
        shift-y (- y (/ b 2))                               ;pocatecni bod shora
        obj (kw {:rectangle (Rectangle2D$Double. shift-x shift-y b b)
                 :circle    (Ellipse2D$Double. shift-x shift-y b b)})]
    (doto (.getGraphics buf-image)
      (.setStroke (get-stroke :tool-size))
      (.setPaint (get-paint shift-x shift-y (+ shift-x b) (+ shift-y b)))
      (.fill obj))
    (when (> (:border-size @paint-state) 0)
      (doto (.getGraphics buf-image)
        (.setStroke (get-stroke :border-size))
        (.setColor (:background-color @paint-state))
        (.draw obj)))))

(defn draw-spray
  "Draws spray to graphics."
  [event buf-image]
  (let [graphics (.getGraphics buf-image)
        b (:tool-size @paint-state)
        d (:tool-density @paint-state)
        x (.getX event)
        shift-x (- x (/ b 2))
        y (.getY event)
        shift-y (- y (/ b 2))]
    (.setColor graphics (:foreground-color @paint-state))
    ; (.fillOval graphics shift-x shift-y b b)))
    (doseq [spray-y (range shift-y (inc (+ shift-y b)))]
      (doseq [spray-x (range shift-x (inc (+ shift-x b)))]
        (when (and (= (rand-nth (range 0 (inc (- 10 d)))) 0)
                   (<= 0 spray-x (.getWidth buf-image))
                   (<= 0 spray-y (.getHeight buf-image))
                   (<= (Point2D/distance x y spray-x spray-y) (/ b 2.0)))
          (.setRGB buf-image spray-x spray-y
                   (.getRGB (.getColor graphics))))))))

(defn display-textframe
  "Displays texframe for drawing text."
  [event buf1 buf2]
  (let [x (.getX event)
        y (.getY event)
        textframe (swing-paint.text/new-textframe buf1 buf2 x y)]
    (doto textframe
      (.setVisible true))))

(defn draw-dragged-line
  "Draws dragged line to graphics."
  [event buf-image]
  (let [[x1 y1] (:drag-point @paint-state)
        x2 (.getX event)
        y2 (.getY event)]
    (doto ^Graphics2D (.getGraphics buf-image)
      (.setStroke (get-stroke :tool-size))
      (.setPaint (get-paint x1 y1 x2 y2))
      (.drawLine x1 y1 x2 y2))))

(defn draw-dragged-object
  "Draws shape from :dragged-point [x y] to event [x y]"
  [kw event buf-image]
  (case kw
    (:line :polygon) (draw-dragged-line event buf-image)
    (draw-dragged-shape event buf-image kw)))

(defn add-curve-point [event]
  (swap! paint-state update :curve-points
         #(conj % (.getX event) (.getY event))))

(defn draw-curve [event buf-image]
  (let [[x1 y1 c1x c1y c2x c2y x2 y2] (map double (:curve-points @paint-state))] ;(doto (.getGraphics buf-image)
    (doto (.getGraphics buf-image)
      (.setStroke (get-stroke :tool-size))
      (.setColor (:foreground-color @paint-state))
      (.draw (doto ^Path2D$Float (GeneralPath.)
               (.moveTo ^double x1 ^double y1)
               (.curveTo
                 ^double c1x
                 ^double c1y
                 ^double c2x
                 ^double c2y
                 ^double x2
                 ^double y2))))))

(defn draw-to-buf-image [event buf-image]
  (case (:tool @paint-state)
    (:rubber :pencil) (draw-simple-shape event buf-image :rectangle)
    :brush (draw-simple-shape event buf-image :circle)
    :spray (draw-spray event buf-image)
    :curve (when (= 8 (count (:curve-points @paint-state)))
             (draw-curve event buf-image))
    nil))

(defn clear-buffered-image
  "Clears given buffer."
  [_ buf-image]
  (let [gr ^Graphics2D (.getGraphics buf-image)]
    (.setComposite gr AlphaComposite/Clear)
    (.fillRect gr 0 0 (.getWidth buf-image) (.getHeight buf-image))
    (.setComposite gr AlphaComposite/SrcOver)))