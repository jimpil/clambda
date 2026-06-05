(ns clambda.core
  (:refer-clojure :exclude [into-array])
  (:require [clambda.redux :refer [abortive-stream vrest mrest rrest]])
  (:import [java.util.stream Stream StreamSupport]
           [clojure.lang IReduceInit]
           (java.io BufferedReader)
           (java.util  Iterator List Map Set Spliterator)
           (clambda.redux SeqSpliterator)
           (java.util.concurrent.atomic AtomicBoolean)))

(defn- accu*
  "A little helper for creating accumulators."
  [f done acc v]
  (let [ret (f acc v)]
    (if (reduced? ret)
      (do (done) @ret)
      ret)))

(defn- throw-combine-not-provided!
  [_ _]
  (throw
    (IllegalStateException.
      "combine-fn NOT provided!")))

(defn rf-some
  "Reducing cousin of `clojure.core/some`."
  ([] nil)
  ([x] x)
  ([_ x]
   (when x
     (ensure-reduced x))))

(defn stream? [x]
  (instance? Stream x))

;;=================================

(defn stream-reducible
  "Turns a Stream into something reducible, and short-circuiting.
   The 3-arg overload controls whether to perform an immutable (the default)
   VS mutable reduction (per `Stream.reduce` VS `Stream.collect` respectively)."
  ([s]
   (stream-reducible s throw-combine-not-provided!))
  ([s combinef]
   (stream-reducible s false combinef))
  ([^Stream s mutable-reduction? combinef]
   (reify IReduceInit
     (reduce [_ f init]
       (let [flag (AtomicBoolean. false)
             done? #(.get flag)
             done! #(.set flag true)
             accumulator (partial accu* f done!)]
         (with-open [estream (abortive-stream s done?)]
           (if mutable-reduction?
             (.collect estream init accumulator combinef)
             (.reduce  estream init accumulator combinef))))))))

(defprotocol MutableContainer
  (mut-accumulate [this e])
  (mut-combine    [this other]))

(extend-protocol MutableContainer
  List
  (mut-accumulate [this e]     (.add this e))
  (mut-combine    [this other] (.addAll this other))
  Set
  (mut-accumulate [this e]     (.add this e))
  (mut-combine    [this other] (.addAll this other))
  Map
  (mut-accumulate [this e]     (.put this (key e) (val e)))
  (mut-combine    [this other] (.putAll this other))
  StringBuilder
  (mut-accumulate [this e]     (.append this e))
  (mut-combine    [this other] (.append this (str other)))
  )

(defn init-from [x]
  (try
    (.clone x)
    (catch Exception _
      (let [empty-array (make-array Class 0)]
        (or
          (some-> (.getDeclaredConstructor (class x) empty-array)
                  (.newInstance empty-array))
          (throw
            (IllegalStateException.
              (str "Unable to clone object " x))))))))

(defn stream-into
  "A 'collecting' transducing context (like `clojure.core/into`), for Java Streams.
   Useful for pouring streams into clojure data-structures
   without involving an intermediate Collection, with the added bonus
   of being able to apply a transducer along the way.
   Parallel streams are supported, but there are two caveats. First of all <to> MUST be
   either empty, or something that can handle duplicates (e.g a set), because it will become
   the <init> for multiple reductions. Be AWARE & CAUTIOUS!
   Secondly, `Stream.reduce()`requires that the reduction does not mutate the values
   received as arguments to combine. Therefore, `conj` has to be the reducing fn per
   parallel reduction, leaving `conj!` (via `into`) for the 'outer' combining.
   If you want a parallel mutable reduction, <to> must satisfy the MutableContainer protocol.
   For serial streams we can go fully mutably (much like `.collect()` does)."
  ([to ^Stream stream]
   (if (.isParallel stream)
     (if (coll? to)
       (reduce conj to (stream-reducible stream into))
       (->> (stream-reducible stream true mut-combine)
            (reduce mut-accumulate (partial init-from to))))
     (into to (stream-reducible stream))))
  ([to xform ^Stream stream]
   (if (.isParallel stream)
     (if (coll? to)
       ;; Cannot use `into` on a parallel stream because it may use transients.
       ;; That goes against the requirement that the reduction
       ;; does not mutate the values received as arguments to combine.
       (transduce xform conj to (stream-reducible stream into))
       ;; we can however have a mutable-reduction,
       ;; if <to> is a MutableContainer (see protocol above)
       (->> (stream-reducible stream true mut-combine)
            (reduce (xform mut-accumulate) (partial init-from to))))
     ;; for a serial stream we're golden - just delegate to `into`
     (into to xform (stream-reducible stream)))))


(defn stream-some
  "A short-circuiting transducing context for Java streams (parallel or not).
   For sequential Streams, rather similar to `.findFirst()` in terms of Streams,
   or `clojure.core/some` in terms of lazy-seqs. For parallel Streams, more
   like `.findAny()`, with the added bonus of aborting the search on the
   'other' threads as soon as an answer is found on 'some' thread."
  ([xform stream]
   (stream-some identity xform stream))
  ([combine-f xform ^Stream stream]
   (let [combine (if (.isParallel stream)
                   (some-fn combine-f)
                   throw-combine-not-provided!)]
     (transduce xform rf-some (stream-reducible stream combine)))))

(defn lines-reducible
  "Similar to `clojure.core/line-seq`, but
   returns something reducible instead of a lazy-seq."
  [^BufferedReader r]
  (reify IReduceInit
    (reduce [_ f init]
      (with-open [rdr r]
        (loop [state init]
          (if (reduced? state)
            @state
            (if-let [line (.readLine rdr)]
              (recur (f state line))
              state)))))))


(defn iterator-reducible
  "Similar to `clojure.core/iterator-seq`,
   but returns something reducible instead of a lazy-seq."
  [^Iterator iter]
  (reify IReduceInit
    (reduce [_ f init]
      (loop [it iter
             state init]
        (if (reduced? state)
          @state
          (if (.hasNext it)
            (recur it (f state (.next it)))
            state))))))

(defn seq-stream
  "Returns a java Stream wrapping a clojure Seq.
   The usefulness of this constructor-fn is minimal (to none) for Clojure users.
   If you're writing Java, it is conceivable that you may want/have to consume a Clojure
   data-structure, and in such cases it is only natural to want to write your code
   in terms of streams.
   WARNING: Only vectors are cheaply splittable, so in general avoid forcefully parallelizing,
   unless you know what you're doing."
  ([s]
   (seq-stream s 1024))
  ([s lazy-split]
    ;; allow parallelism by default only on vectors
    ;; because they can be split cheaply
   (seq-stream s lazy-split (vector? s)))
  (^Stream [s lazy-split parallel?]
   (let [[is-counted? is-sorted? is-map? is-set? is-vector?]
         ((juxt counted? sorted? map? set? vector?) s)
         characteristics (cond->> Spliterator/IMMUTABLE
                                  is-counted? (bit-and Spliterator/SIZED
                                                       Spliterator/SUBSIZED)
                                  is-sorted?  (bit-and Spliterator/SORTED)
                                  (or is-map?
                                      is-set?) (bit-and Spliterator/DISTINCT))
         rest-fn (cond                                      ;; 2-args so we can call it without caring
                   is-vector?  vrest
                   is-set?     disj
                   is-map?     mrest
                   :else       rrest)]
     (StreamSupport/stream
       (SeqSpliterator. s characteristics rest-fn (or lazy-split 1024))
       parallel?))))

(defn stream-seq
  "Returns a Seq wrapping a java Stream (via its Iterator)."
  [^Stream s]
  ;; It would be somewhat unorthodox to use this,
  ;; given that the whole point of this library is
  ;; to help you avoid doing exactly that (going via Seq)
  (-> s .iterator iterator-seq))

(defn stream-array
  "Collects all the elements of Stream <s> into an array of type <t>."
  [^Class t ^Stream s]
  (->> (partial make-array t)
       (.toArray s)))

(defn into-array
  "An drop-in replacement for `clojure.core/into-array`,
   which will work against Java Streams and can take a transducer."
  ([xs]
   (into-array nil xs))
  ([xform xs]
   (into-array nil xform xs))
  ([^Class t xform xs]
   (let [sseq (cond->> xs
                       (stream? xs) stream-seq
                       xform (eduction xform))]
     (if (nil? t)
       (clojure.core/into-array sseq)
       (clojure.core/into-array t sseq)))))

