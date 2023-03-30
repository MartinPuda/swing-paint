(ns swing-paint.text
  (:import (javax.swing JFrame JTextArea JSpinner SpinnerNumberModel JComboBox BorderFactory JPanel)
           (java.awt Dimension BorderLayout GraphicsEnvironment Color Component GridLayout Font)
           (javax.swing.event ChangeListener DocumentListener)
           (java.awt.event ActionListener WindowAdapter))
  (:use swing-paint.state)
  (:gen-class))

(defn draw-text [event buf buf1 buf2 x y]
  (doto (.getGraphics buf)
    (.setColor (:foreground-color @paint-state))
    (.setFont (Font. (:font @paint-state)
                     Font/PLAIN
                     (:font-size @paint-state)))
    (.drawString (:text @paint-state) x y)))

(defn new-textframe [buf1 buf2 x y]
  (let [textframe-input (doto (JTextArea. "")
                          (.setBorder (BorderFactory/createLineBorder Color/BLACK))
                          (.setPreferredSize (Dimension. 250 100)))

        textframe-size (doto (JSpinner. (SpinnerNumberModel. ^int (:font-size @paint-state) 1 100 1))
                         (.addChangeListener (proxy [ChangeListener] []
                                               (stateChanged [event]
                                                 (swap! paint-state assoc :font-size (.getValue (.getSource event)))
                                                 (draw-text event buf2 buf1 buf2 x y)))))

        textframe-font (doto (JComboBox. (.getAvailableFontFamilyNames
                                           (GraphicsEnvironment/getLocalGraphicsEnvironment)))
                         (.setSelectedItem (:font @paint-state))
                         (.addActionListener (proxy [ActionListener] []
                                               (actionPerformed [event]
                                                 (swap! paint-state assoc :font (.getSelectedItem (.getSource event)))
                                                 (draw-text event buf2 buf1 buf2 x y)))))

        textframe-bottom-row (doto (JPanel.)
                               (.setBorder (BorderFactory/createEmptyBorder 5 0 0 0))
                               (.setLayout (BorderLayout.))
                               (.setPreferredSize (Dimension. 300 25))
                               (.add textframe-size BorderLayout/LINE_START)
                               (.add textframe-font BorderLayout/LINE_END))

        textframe-wrap (doto (JPanel.)
                         (.setPreferredSize (Dimension. 300 200))
                         (.setLayout (BorderLayout.))
                         (.setBorder (BorderFactory/createEmptyBorder 5 5 5 5))
                         (.add textframe-input BorderLayout/CENTER)
                         (.add textframe-bottom-row BorderLayout/PAGE_END))

        textframe (doto (JFrame. "Fonts")
                    (.add ^Component textframe-wrap)
                    (.setMinimumSize (Dimension. 300 200))
                    (.setPreferredSize (Dimension. 300 200))
                    (.addWindowListener (proxy [WindowAdapter] []
                                          (windowClosing [event]
                                            (draw-text event buf1 buf1 buf2 x y)))))]

    (.addDocumentListener (.getDocument textframe-input)
                          (proxy [DocumentListener] []
                            (changedUpdate [event] (println "ahoj"))
                            (removeUpdate [event])
                            (insertUpdate [event]
                              (let [d (.getDocument event)]
                                (swap! paint-state assoc :text (.getText d 0 (.getLength d)))))))
    ;   (draw-text event buf2 buf1 buf2 x y)))))

    textframe))