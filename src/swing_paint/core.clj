(ns swing-paint.core
  (:require
    [swing-paint.state :refer :all]
    [swing-paint.tools :refer :all]
    [swing-paint.gui :refer :all])
  (:import (javax.swing UIManager))
  (:gen-class))

(defn -main [& _]
  (UIManager/setLookAndFeel                                 ;(UIManager/getSystemLookAndFeelClassName))
    "javax.swing.plaf.metal.MetalLookAndFeel")
  (reset-state!)
  (doto (new-mainframe)
    (.setResizable true)
    (.setVisible true)))