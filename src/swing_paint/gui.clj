(ns swing-paint.gui
  (:require
    [swing-paint.state :refer :all]
    [swing-paint.text :refer :all]
    [swing-paint.tools :refer :all]
    [swing-paint.colors :refer :all])
  (:import (javax.swing JButton BorderFactory JSeparator ImageIcon SwingUtilities JColorChooser JPanel JSpinner SpinnerNumberModel JRadioButton ButtonGroup JLabel JMenuBar JMenu JMenuItem JFileChooser BoxLayout JFrame)
           (java.awt Color Dimension Cursor GridLayout Component FlowLayout BorderLayout Container)
           (java.awt.event ActionListener MouseAdapter ComponentAdapter MouseMotionAdapter)
           (java.awt.image BufferedImage)
           (javax.swing.event ChangeListener)
           (javax.swing.border EmptyBorder)
           (java.io File)
           (javax.imageio ImageIO))
  (:gen-class))

;;; generic objects

(defn generic-button []
  (doto (JButton.)
    (.setPreferredSize (Dimension. 25 25))
    (.setBorder (BorderFactory/createLineBorder Color/BLACK))
    (.setOpaque true)))

(defn generic-separator []
  (doto (JSeparator.)
    (.setMaximumSize (Dimension. Integer/MAX_VALUE 1))))

(defn paint-black-border []
  (BorderFactory/createLineBorder Color/BLACK))

(def paint-frame-color (Color. 247 239 222))

(def max-x 1820)

(def max-y 894)

;;; mainframe

(defn new-mainframe []
  (let [buf1 (BufferedImage. 1820 894 BufferedImage/TYPE_INT_ARGB) ;two buffered images
        buf2 (BufferedImage. 1820 894 BufferedImage/TYPE_INT_ARGB)
        ;resizing adapter for canvas
        ca (proxy [ComponentAdapter] []
             (componentResized [event]
               (.drawImage (.getGraphics (.getSource event)) buf1 0 0 nil)))

        fgcolor
        (doto (generic-button)
          (.setBackground (:foreground-color @paint-state)))

        bgcolor
        (doto (generic-button)
          (.setBackground (:background-color @paint-state)))

        tool-buttons
        (map (fn [[x y] tool-number tool-keyword]
               (doto (JButton.)
                 (.setIcon (ImageIcon. (str "resources/button" tool-number ".png")))
                 (.setBounds x y 25 25)
                 (.setBorder (swing-paint.gui/paint-black-border))
                 (.addActionListener (proxy [ActionListener] []
                                       (actionPerformed [_]
                                         (swap! paint-state assoc :tool tool-keyword)
                                         (when (= tool-keyword :rubber)
                                           (let [color Color/WHITE]
                                             (swap! paint-state assoc :background-color color)
                                             (swap! paint-state assoc :foreground-color color)
                                             (.setBackground bgcolor color)
                                             (.setBackground fgcolor color)))
                                         (swap! paint-state assoc :drag-point false)
                                         (swap! paint-state assoc :polygon-points [])
                                         (swap! paint-state assoc :curve-points [])
                                         (println (:tool @paint-state)))))))
             (map #(vector %1 %2)
                  (interleave (repeat 8 24) (repeat 8 50))
                  (mapcat #(vector %1 %1)
                          (range 25 225 25)))
             (range 1 17)
             (list :a :a :rubber :bucket
                   :color-picker :a :pencil :brush
                   :spray :text :line :curve
                   :rectangle :polygon :circle :round-rectangle))

        pick-color
        (fn [event buf-image]
          (let [kw (if (SwingUtilities/isLeftMouseButton event) :foreground-color :background-color)
                color (Color. (.getRGB buf-image (.getX event) (.getY event)))]
            (swap! paint-state assoc kw color)
            (.setBackground (case kw :background-color bgcolor
                                     :foreground-color fgcolor) color)))

        color-button-adapter
        (fn []
          (proxy [MouseAdapter] []
            (mouseClicked [event]
              (cond (and (= (.getClickCount event) 2)
                         (SwingUtilities/isLeftMouseButton event))
                    (let [newcolor (JColorChooser/showDialog nil nil nil)]
                      (when newcolor
                        (.setBackground
                          (.getSource event) newcolor)))
                    (SwingUtilities/isLeftMouseButton event)
                    (let [new-fgcolor (.getBackground (.getSource event))]
                      (swap! paint-state assoc :foreground-color new-fgcolor)
                      (.setBackground fgcolor new-fgcolor))
                    (SwingUtilities/isRightMouseButton event)
                    (let [new-bgcolor (.getBackground (.getSource event))]
                      (swap! paint-state assoc :background-color new-bgcolor)
                      (.setBackground bgcolor new-bgcolor))))))

        color-buttons
        (map
          (fn [[x y] color]
            (doto (JButton.)
              (.setBorder (swing-paint.gui/paint-black-border))
              (.setBounds x y 25 25)
              (.setBackground color)
              (.setPreferredSize (Dimension. 25 25))
              (.addMouseListener (color-button-adapter))))
          (concat
            (map #(vector % 424)
                 (range 100 450 26))
            (map #(vector % 450)
                 (range 100 450 26)))
          (paint-predefined-colors))

        canvas-mouse-motion-listener (proxy [MouseMotionAdapter] []
                                       (mouseMoved [event]
                                         (let [src (.getSource event)
                                               grcn (.getGraphics src)]
                                           ;;     (.setColor grcn (Color. 255 255 255))
                                           ;     (.fillRect grcn 0 0 400 400)) ;gr se pokryje bílou
                                           (.drawImage grcn buf1
                                                       0 0
                                                       nil)
                                           ;vykreslí se full  image z buf1
                                           (clear-buffered-image event buf2)
                                           ;buf2 se pokryje průhledností
                                           (draw-to-buf-image event buf2)
                                           ;obrázek se vykresleslí do buf2 a ten se vykreslí do gr
                                           (.drawImage grcn buf2 0 0 nil)))
                                       (mouseDragged [event]
                                         (let [src (.getSource event)
                                               tool (:tool @paint-state)]
                                           (if (#{:line :rectangle :polygon :circle :round-rectangle} tool)
                                             (do (.drawImage (.getGraphics src) buf1 0 0 nil)
                                                 (clear-buffered-image event buf2)
                                                 (draw-dragged-object tool event buf2)
                                                 (.drawImage (.getGraphics src) buf2 0 0 nil))

                                             (do (draw-to-buf-image event buf1)
                                                 (.drawImage (.getGraphics (.getSource event)) buf1 0 0 nil))))))

        canvas-mouse-listener (proxy [MouseAdapter] []
                                (mouseClicked [_])
                                (mousePressed [event]
                                  (let [x (.getX event)
                                        y (.getY event)
                                        tool (:tool @paint-state)]
                                    (when (#{:line :rectangle :circle :round-rectangle} tool)
                                      (swap! paint-state assoc :drag-point [x y]))
                                    (when (= tool :polygon)
                                      (if (empty? (:polygon-points @paint-state))
                                        (do (swap! paint-state assoc :polygon-points [[x y]])
                                            (swap! paint-state assoc :drag-point [x y]))
                                        (swap! paint-state assoc :drag-point (last (:polygon-points @paint-state)))))))
                                (mouseReleased [event]
                                  (case
                                    (:tool @paint-state)
                                    :bucket
                                    (do (optimized-filler event buf1)
                                        (.drawImage (.getGraphics (.getSource event)) buf1 0 0 nil))
                                    (:spray :rubber) (do (draw-to-buf-image event buf1)
                                                         (.drawImage (.getGraphics (.getSource event)) buf1 0 0 nil))
                                    (:brush :pencil) (draw-to-buf-image event buf1)
                                    :color-picker (pick-color event buf1)
                                    :text (display-textframe event buf1 buf2)
                                    :line (do (draw-dragged-line event buf1)
                                              (swap! paint-state assoc :drag-point false))
                                    :curve (do (swing-paint.tools/add-curve-point event)
                                               (when (= (count (:curve-points @paint-state)) 8)
                                                 (draw-to-buf-image event buf1)
                                                 (swap! paint-state assoc :curve-points [])
                                                 (.drawImage (.getGraphics (.getSource event)) buf1 0 0 nil)))
                                    :rectangle (do (draw-dragged-shape event buf1 :rectangle)
                                                   (swap! paint-state assoc :drag-point false))
                                    :polygon (do (swap! paint-state assoc :drag-point false)
                                                 (draw-polygon event buf1)
                                                 (.drawImage (.getGraphics (.getSource event)) buf1 0 0 nil))
                                    :circle (do (draw-dragged-shape event buf1 :circle)
                                                (swap! paint-state assoc :drag-point false))
                                    :round-rectangle (do (draw-dragged-shape event buf1 :round-rectangle)
                                                         (swap! paint-state assoc :drag-point false))
                                    nil))
                                (mouseEntered [_])
                                (mouseExited [_])
                                (mouseWheelMoved [_])
                                (mouseMoved [_]))
        ;; canvas
        canvas (doto (JPanel.)
                 (.setCursor (Cursor/getPredefinedCursor Cursor/HAND_CURSOR))
                 (.addComponentListener ca)
                 (.setPreferredSize (Dimension. 400 400))
                 (.addMouseMotionListener canvas-mouse-motion-listener)
                 (.addMouseListener canvas-mouse-listener)
                 (.setBackground Color/WHITE))
        ;spinner for tool size
        ; spinner-alpha
        spinner-size (doto (JSpinner. (SpinnerNumberModel. ^int (:tool-size @paint-state) 2 100 1))
                       (.setPreferredSize (Dimension. 50 25))
                       (.addChangeListener (proxy [ChangeListener] []
                                             (stateChanged [event]
                                               (swap! paint-state assoc :tool-size (.getValue (.getSource event)))))))

        spinner-density (doto (JSpinner. (SpinnerNumberModel. ^int (:tool-density @paint-state) 1 10 1))
                          (.setPreferredSize (Dimension. 50 25))
                          (.addChangeListener (proxy [ChangeListener] []
                                                (stateChanged [event]
                                                  (swap! paint-state assoc :tool-density (.getValue (.getSource event)))))))

        spinner-border-size (doto (JSpinner. (SpinnerNumberModel. ^int (:border-size @paint-state) 0 100 1))
                              (.setMaximumSize (Dimension. 50 25))
                              (.addChangeListener (proxy [ChangeListener] []
                                                    (stateChanged [event]
                                                      (swap! paint-state assoc :border-size (.getValue (.getSource event)))))))

        radio-button-listener
        (fn [data]
          (proxy [ActionListener] []
            (actionPerformed [_]
              (swap! paint-state assoc :stroke data))))

        radio-button-listener2
        (fn [data]
          (proxy [ActionListener] []
            (actionPerformed [_]
              (let [chooser (JFileChooser.)]
                (when (and (= data :texture)
                           (zero? (.showOpenDialog chooser nil)))
                  (swap! paint-state assoc :texture
                         (-> (.getSelectedFile chooser)
                             (.getAbsolutePath)
                             (File.)
                             (ImageIO/read))))
                (swap! paint-state assoc :paint data)))))

        ;; toto je panel pod 8x2
        radio1 (doto (JRadioButton. "⎯⎯⎯⎯⎯" true)
                 (.setBackground paint-frame-color)
                 (.addActionListener (radio-button-listener :solid)))
        radio2 (doto (JRadioButton. "⋅ ⋅ ⋅ ⋅ ⋅ ⋅")
                 (.setBackground paint-frame-color)
                 (.addActionListener (radio-button-listener :dotted)))
        radio3 (doto (JRadioButton. "⎯ ⎯ ⎯ ⎯ ")
                 (.setBackground paint-frame-color)
                 (.addActionListener (radio-button-listener :dashed)))
        radio4 (doto (JRadioButton. "⎯ ⋅ ⎯ ⋅ ⎯")
                 (.setBackground paint-frame-color)
                 (.addActionListener (radio-button-listener :dash-dotted)))
        radio-group (doto (ButtonGroup.)
                      (.add radio1)
                      (.add radio2)
                      (.add radio3)
                      (.add radio4))

        g2-radio1 (doto (JRadioButton. "Color" true)
                    (.setBackground paint-frame-color)
                    (.addActionListener (radio-button-listener2 :color)))
        g2-radio2 (doto (JRadioButton. "Gradient 1")
                    (.setBackground paint-frame-color)
                    (.addActionListener (radio-button-listener2 :gradient1)))
        g2-radio3 (doto (JRadioButton. "Gradient 2")
                    (.setBackground paint-frame-color)
                    (.addActionListener (radio-button-listener2 :gradient2)))
        g2-radio4 (doto (JRadioButton. "Gradient 3")
                    (.setBackground paint-frame-color)
                    (.addActionListener (radio-button-listener2 :gradient3)))
        g2-radio5 (doto (JRadioButton. "Texture")
                    (.setBackground paint-frame-color)
                    (.addActionListener (radio-button-listener2 :texture)))
        radio-group2 (doto (ButtonGroup.)
                       (.add g2-radio1)
                       (.add g2-radio2)
                       (.add g2-radio3)
                       (.add g2-radio4)
                       (.add g2-radio5))

        ;; tohle je 8x2 tlacitkovy panel
        pane-tools-buttons (doto (JPanel. (GridLayout. 8 2))
                             (.setBorder (EmptyBorder. 10 25 10 25))
                             (.setBackground paint-frame-color)
                             (.setPreferredSize (Dimension. 100 100)))

        pane-size+density (doto (JPanel.)
                            (.setBackground paint-frame-color)
                            (.add (doto (JLabel. "Size")
                                    (.setPreferredSize (Dimension. 50 15))))
                            (.add spinner-size)
                            (.add (doto (JLabel. "Density")
                                    (.setPreferredSize (Dimension. 50 15))))
                            (.add spinner-density))

        pane-border-size (doto (JPanel.)
                           (.setBackground paint-frame-color)
                           (.setPreferredSize (Dimension. 100 50))
                           (.setMaximumSize (Dimension. 100 50))
                           (.add (doto (JLabel. "Border")
                                   (.setMinimumSize (Dimension. 100 50))))
                           (.add spinner-border-size))

        pane-radios1 (doto (JPanel.)
                       (.setBackground paint-frame-color)
                       (.setPreferredSize (Dimension. 100 125))
                       (.setAlignmentX Component/CENTER_ALIGNMENT)
                       (.setBorder (EmptyBorder. 5 5 5 5))
                       (.add (doto (JLabel. "Line Type")
                               (.setPreferredSize (Dimension. 50 15))))
                       (.add radio1)
                       (.add radio2)
                       (.add radio3)
                       (.add radio4))

        pane-radios2 (doto (JPanel.)
                       (.setBackground paint-frame-color)
                       (.setPreferredSize (Dimension. 100 100))
                       (.setAlignmentX Component/CENTER_ALIGNMENT)
                       (.setBorder (EmptyBorder. 5 5 5 5))
                       (.add (doto (JLabel. "Fill Style")
                               (.setPreferredSize (Dimension. 50 15))))
                       (.add g2-radio1)
                       (.add g2-radio2)
                       (.add g2-radio3)
                       (.add g2-radio4)
                       (.add g2-radio5))

        pane-left-tools (doto (JPanel.)
                          (.setBackground paint-frame-color)
                          (.setPreferredSize (Dimension. 100 400))
                          (.add pane-tools-buttons BorderLayout/PAGE_START)
                          (.add ^Component (generic-separator))
                          (.add pane-size+density BorderLayout/PAGE_START))

        pane-right-tools (doto (JPanel.)
                           (.setAlignmentX Component/LEFT_ALIGNMENT)
                           (.setBackground paint-frame-color)
                           (.setPreferredSize (Dimension. 100 400))
                           (.add pane-border-size)
                           (.add ^Component (generic-separator))
                           (.add pane-radios1)
                           (.add ^Component (generic-separator))
                           (.add pane-radios2)
                           (.add ^Component (generic-separator)))

        pane-left-sidebar (doto (JPanel.)
                            (.setBackground paint-frame-color)
                            (.setPreferredSize (Dimension. 100 400))
                            (.add pane-left-tools BorderLayout/PAGE_START))

        pane-right-sidebar (doto (JPanel.)
                             (.setBackground paint-frame-color)
                             (.setPreferredSize (Dimension. 100 400))
                             (.add pane-right-tools BorderLayout/PAGE_START))

        colors1 (doto (JPanel. (FlowLayout. FlowLayout/CENTER 1 0))
                  (.setBackground paint-frame-color)
                  (.setPreferredSize (Dimension. 100 100))
                  (.setBorder (EmptyBorder. 25 10 25 10))
                  (.add ^Component fgcolor)
                  (.add ^Component bgcolor))
        colors2 (doto (JPanel. (GridLayout. 2 8 1 1))
                  (.setBorder (EmptyBorder. 25 10 25 10))   ;top left bottom right
                  (.setBackground paint-frame-color)
                  (.setPreferredSize (Dimension. 350 100)))
        pane-colors (doto (JPanel. (FlowLayout. FlowLayout/LEFT 0 0))
                      (.setBackground paint-frame-color)
                      (.setPreferredSize (Dimension. 550 100))
                      (.add colors1)
                      (.add colors2))

        mainframe (doto (JFrame. "Clojure Swing Paint")
                    (.setSize (Dimension. 600 500))
                    (.setLayout (BorderLayout.))
                    (.setMinimumSize (Dimension. 600 500))
                    (.setFocusable true)
                    (.add canvas BorderLayout/CENTER)
                    (.add pane-colors BorderLayout/PAGE_END)
                    (.setJMenuBar (doto (JMenuBar.)
                                    (.add (doto (JMenu. "Menu")
                                            (.add (doto (JMenuItem. "New") ;clears buf1
                                                    (.addActionListener (proxy [ActionListener] []
                                                                          (actionPerformed [_]
                                                                            (let [src canvas
                                                                                  grcn (.getGraphics src)
                                                                                  grbuf (.getGraphics buf1)]
                                                                              (.setColor grbuf Color/white)
                                                                              (.fillRect grbuf 0 0 (.getWidth buf1) (.getHeight buf1))
                                                                              (.drawImage grcn buf1 0 0 nil)))))))
                                            (.add (doto (JMenuItem. "Open") ;opens image into canvas
                                                    (.addActionListener (proxy [ActionListener] []
                                                                          (actionPerformed [_]
                                                                            (let [src canvas
                                                                                  chooser (new JFileChooser)
                                                                                  returnVal (.showOpenDialog chooser nil)
                                                                                  grcn (.getGraphics src)
                                                                                  grbuf (.getGraphics buf1)]
                                                                              (when (= returnVal 0)
                                                                                (let [selected-file (.getSelectedFile chooser)
                                                                                      abspath (.getAbsolutePath selected-file)
                                                                                      image (ImageIO/read (File. abspath))]
                                                                                  (.setColor grbuf Color/white)
                                                                                  (.fillRect grbuf 0 0 (.getWidth buf1) (.getHeight buf1))
                                                                                  (.drawImage grbuf image 0 0 nil) ;grafika mista, jaky obrazek, prvne do bufferu
                                                                                  (.drawImage grcn buf1 0 0 nil))))))))) ;a pak prekresli canvas
                                            (.add (doto (JMenuItem. "Save")
                                                    (.addActionListener (proxy [ActionListener] []
                                                                          (actionPerformed [_]
                                                                            (try (let [chooser (new JFileChooser)
                                                                                       returnVal (.showSaveDialog chooser nil)]
                                                                                   (when (= returnVal JFileChooser/APPROVE_OPTION)
                                                                                     (let [selected-file (.getSelectedFile chooser)
                                                                                           abspath (.getAbsolutePath selected-file)]
                                                                                       (println selected-file)
                                                                                       (println abspath)
                                                                                       (ImageIO/write buf1 "png" (File.
                                                                                                                   (if (clojure.string/ends-with? abspath ".png")
                                                                                                                     abspath
                                                                                                                     (str abspath ".png"))))
                                                                                       ; jpg didn't work
                                                                                       (println "Image saved..."))))
                                                                                 (catch Exception e (println e))))))))
                                            (.add (JSeparator.)))))))]
    (.setLayout pane-radios1 (BoxLayout. pane-radios1 BoxLayout/Y_AXIS))
    (.setLayout pane-radios2 (BoxLayout. pane-radios2 BoxLayout/Y_AXIS))
    (.setLayout pane-right-tools (BoxLayout. pane-right-tools BoxLayout/Y_AXIS))
    (.setLayout pane-left-tools (BoxLayout. pane-left-tools BoxLayout/Y_AXIS))

    (doto mainframe
      (.add pane-left-sidebar BorderLayout/LINE_START)
      (.add canvas BorderLayout/CENTER)
      (.add pane-right-sidebar BorderLayout/LINE_END)
      (.add pane-colors BorderLayout/PAGE_END))

    (doseq [but tool-buttons]
      (.add ^Container pane-tools-buttons ^Component but))

    (doseq [but color-buttons]
      (.add ^Container colors2 ^Component but))

    (let [gr (.getGraphics buf1)]
      (.setColor gr Color/white)
      (.fillRect gr 0 0 max-x max-y))

    (let [gr (.getGraphics buf2)]
      (.setColor gr Color/white)
      (.fillRect gr 0 0 max-x max-y))
    mainframe))