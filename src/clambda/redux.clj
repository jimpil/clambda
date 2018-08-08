(ns clambda.redux
  (:import (java.util.stream Stream StreamSupport)
           (java.util Spliterator)))


(defn- abortive-spliterator ;; short-circuiting Spliterator
  "Wraps a Spliterator such that it can (optionally) terminate early
  (i.e. when <done?>, a fn of no args, returns true)."
  [^Spliterator internal done?]
  (reify Spliterator
    (characteristics [_]
      (-> internal
          .characteristics
          (bit-and-not Spliterator/SIZED Spliterator/SORTED)))
    (estimateSize [_]
      (if (done?)
        0
        (.estimateSize internal)))
    (tryAdvance [_ action]
      (and (not (done?))
           (.tryAdvance internal action)))
    (trySplit [_]
      (when-let [new-split (.trySplit internal)]
        (abortive-spliterator new-split done?)))))

(defn abortive-stream ;; ;; short-circuiting Stream
  "Wraps a Stream such that it can (optionally) terminate early
  (i.e. when <done?>, a fn of no args, returns true)."
  ^Stream [^Stream stream done?]
  (-> stream
      .spliterator
      (abortive-spliterator done?)
      (StreamSupport/stream (.isParallel stream))))


(deftype SeqSpliterator
  [^:unsynchronized-mutable s characteristics rest-fn lazy-seq-split]

  Spliterator
  (characteristics [_]
    characteristics)
  (estimateSize [_]
    (if (counted? s)
      (count s)
      Long/MAX_VALUE))
  (tryAdvance [_ action]
    (boolean
      (when-let [x (first s)]
        (.accept action x)
        (set! s (rest-fn s x)))))
  (trySplit [_]
    (when-let [current (not-empty s)]
      (cond
        (vector? current)
        (let [n (count current)
              middle (/ n 2)
              left  (subvec current 0 middle)
              right (subvec current middle n)] ;; fast split
          (when (and (seq left)
                     (seq right))
            (set! s right)
            (SeqSpliterator. left characteristics rest-fn nil)))

        (map? current)
        (let [n (count current)
              middle (/ n 2)
              ks (keys current)
              left-ks (take middle ks)
              right (apply dissoc current left-ks)]
          (when (and (seq left-ks)
                     (seq right))
            (set! s right)
            (SeqSpliterator. (select-keys current left-ks) characteristics rest-fn nil)))

        (set? current) ;; this is the slowest path :(
        (let [n (count current)
              middle (/ n 2)
              left-ks (take middle current)
              right (apply disj current left-ks)]
          (when (and (seq left-ks)
                     (seq right))
            (set! s right)
            (SeqSpliterator. (set left-ks) characteristics rest-fn nil)))

        :else
        (let [[left right] (split-at lazy-seq-split current)] ;; slow split
          (when (and (seq left)
                     (seq right))
            (set! s right)
            (SeqSpliterator. left characteristics rest-fn lazy-seq-split)))
        )
      )
    )
  )

(defn vrest
  "rest-fn for vectors"
  [v _]
  (subvec v 1))

(defn mrest
  "rest-fn for maps"
  [m me]
  (dissoc m (key me)))

(defn rrest
  "rest-fn for seqs"
  [s _]
  (rest s))
