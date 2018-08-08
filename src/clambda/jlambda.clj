(ns clambda.jlambda
  (:import (java.util.function Predicate Function BiFunction
                               Supplier LongSupplier IntSupplier DoubleSupplier
                               Consumer BiConsumer LongConsumer IntConsumer UnaryOperator DoubleConsumer
                               IntUnaryOperator LongUnaryOperator DoubleUnaryOperator BinaryOperator)))


;;helper macros to remove boilerplate
(defmacro ^:private generate-uni-variant
  [target-iface method]
  `(reify ~target-iface
     (~method [~'_ ~'x]
       (~'f ~'x))))

(defmacro ^:private generate-bi-variant
  [target-iface method]
  `(reify ~target-iface
     (~method [~'_ ~'x ~'y]
       (~'f ~'x ~'y))))


(defmulti jlambda (fn [target-interface f] target-interface))
;;=========================================================
;;#########################################################

;============<PREDICATES>=========

(defmethod jlambda :predicate [_ f]
  (reify Predicate
    (test [_ x]
      (boolean (f x)))))
;=================================
;=========<FUNCTIONS>=============

(defmethod jlambda :function [_ f]
  (generate-uni-variant Function apply))

(defmethod jlambda :bi-function [_ f]
  (generate-bi-variant BiFunction apply))
;===================================
;===========<CONSUMERS>=============

(defmethod jlambda :consumer [_ f]
  (generate-uni-variant Consumer accept))

(defmethod jlambda :long-consumer [_ f]
  (generate-uni-variant LongConsumer accept))

(defmethod jlambda :int-consumer [_ f]
  (generate-uni-variant IntConsumer accept))

(defmethod jlambda :double-consumer [_ f]
  (generate-uni-variant DoubleConsumer accept))

(defmethod jlambda :bi-consumer [_ f]
  (generate-bi-variant BiConsumer accept))
;====================================
;==========<SUPPLIERS>===============

(defmethod jlambda :supplier [_ f]
  (reify Supplier
    (get [_]
      (f))))

(defmethod jlambda :long-supplier [_ f]
  (reify LongSupplier
    (getAsLong [_]
      (long (f)))))

(defmethod jlambda :int-supplier [_ f]
  (reify IntSupplier
    (getAsInt [_]
      (int (f)))))

(defmethod jlambda :double-supplier [_ f]
  (reify DoubleSupplier
    (getAsDouble [_]
      (double (f)))))

(defmethod jlambda :unary [_ f]
  (reify UnaryOperator
    (apply [_ x]
      (f x))))

(defmethod jlambda :binary [_ f]
  (reify BinaryOperator
    (apply [_ x y]
      (f x y))))

(defmethod jlambda :int-unary [_ f]
  (reify IntUnaryOperator
    (applyAsInt [_ x]
      (int (f x)))))

(defmethod jlambda :long-unary [_ f]
  (reify LongUnaryOperator
    (applyAsLong [_ x]
      (long (f x)))))

(defmethod jlambda :double-unary [_ f]
  (reify DoubleUnaryOperator
    (applyAsDouble [_ x]
      (double (f x)))))

;;------------------------------------

(comment
  ;; gives => [2 3]
  (-> (doto (java.util.ArrayList.)
        (.add 1)
        (.add 2))
      .stream
      (.map (jlambda :function inc))
      (.collect (java.util.stream.Collectors/toList)))

  (-> [1 2 3 4 5]
      .stream
      (.filter (jlambda :predicate odd?))
      (.collect (java.util.stream.Collectors/toList)))


  )
