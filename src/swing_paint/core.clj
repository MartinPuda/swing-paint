(ns swing-paint.core
  (:use swing-paint.state
        swing-paint.tools
        swing-paint.gui)
  (:import (javax.swing UIManager))
  (:gen-class))

(defn -main [& _]
  (UIManager/setLookAndFeel                                 ;(UIManager/getSystemLookAndFeelClassName))
    "javax.swing.plaf.metal.MetalLookAndFeel")
  (reset-state!)
  (doto (new-mainframe)
    (.setResizable true)
    (.setVisible true)))