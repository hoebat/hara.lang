(ns std.lang.base.emit-source
  (:require [std.string :as str]
            [std.lib :as h]
            [std.json :as json]))

;;
;; Source Node
;;

(defrecord SourceNode [source line column name children])

(defn source-node?
  "checks if object is a source node"
  {:added "4.0"}
  [x]
  (instance? SourceNode x))

(defn node
  "creates a source node
   (node 10 5 \"file.clj\" \"var x = 1;\")
   (node 10 5 \"file.clj\" [\"var \" \"x\" \" = \" \"1;\"])"
  {:added "4.0"}
  ([line column source chunk]
   (node line column source chunk nil))
  ([line column source chunk name]
   (SourceNode. source line column name (if (sequential? chunk) chunk [chunk]))))

(defn fragment
  "creates a source fragment (list of nodes)"
  {:added "4.0"}
  [children]
  (SourceNode. nil nil nil nil children))

(defn to-string
  "renders the source node to a string"
  {:added "4.0"}
  [node]
  (cond (nil? node) ""
        (string? node) node
        (source-node? node) (apply str (map to-string (:children node)))
        (sequential? node) (apply str (map to-string node))
        :else (str node)))

;;
;; Emit Helpers (Polymorphic)
;;

(defn emit-str
  "polymorphic str for strings and source-nodes"
  {:added "4.0"}
  [& args]
  (if (some source-node? args)
    (fragment args)
    (apply str args)))

(defn emit-join
  "polymorphic join for strings and source-nodes"
  {:added "4.0"}
  [sep args]
  (if (some source-node? args)
    (fragment (interpose sep args))
    (str/join sep args)))

;;
;; VLQ / Map Gen
;;

(def +vlq-base-shift+ 5)
(def +vlq-base+ (bit-shift-left 1 +vlq-base-shift+))
(def +vlq-base-mask+ (dec +vlq-base+))
(def +vlq-continuation-bit+ +vlq-base+)

(defn to-vlq-signed
  [v]
  (if (neg? v)
    (inc (bit-shift-left (- v) 1))
    (bit-shift-left v 1)))

(def +base64-chars+ "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/")

(defn base64-encode
  [v]
  (nth +base64-chars+ v))

(defn encode-vlq
  [v]
  (let [vlq (to-vlq-signed v)]
    (loop [digit (bit-and vlq +vlq-base-mask+)
           rest  (bit-shift-right vlq +vlq-base-shift+)
           acc   []]
      (if (pos? rest)
        (recur (bit-and rest +vlq-base-mask+)
               (bit-shift-right rest +vlq-base-shift+)
               (conj acc (base64-encode (bit-or digit +vlq-continuation-bit+))))
        (apply str (conj acc (base64-encode digit)))))))

(defn generate-source-map
  "generates a v3 source map"
  {:added "4.0"}
  [root-node {:keys [file source-root]}]
  (let [code (to-string root-node)

        state (atom {:generated-line 1
                     :generated-column 0
                     :previous-generated-column 0
                     :previous-original-line 0
                     :previous-original-column 0
                     :previous-source-index 0
                     :previous-name-index 0
                     :sources []
                     :names []

                     ;; Output buffers
                     :current-line-segments [] ;; Vector of encoded strings
                     :all-lines []             ;; Vector of line strings (joined by ;)
                     })

        get-source-index (fn [s]
                           (let [{:keys [sources]} @state
                                 idx (.indexOf sources s)]
                             (if (neg? idx)
                               (do (swap! state update :sources conj s)
                                   (count sources))
                               idx)))

        get-name-index (fn [n]
                         (let [{:keys [names]} @state
                               idx (.indexOf names n)]
                           (if (neg? idx)
                             (do (swap! state update :names conj n)
                                 (count names))
                             idx)))

        flush-line (fn []
                     (let [{:keys [current-line-segments]} @state]
                       (swap! state update :all-lines conj (str/join "," current-line-segments))
                       (swap! state assoc :current-line-segments [])
                       (swap! state assoc :generated-column 0)
                       (swap! state assoc :previous-generated-column 0)
                       (swap! state update :generated-line inc)))

        process-node (fn process [node]
                       (cond (string? node)
                             (let [parts (str/split node #"\n" -1)]
                               (doseq [[i part] (map-indexed vector parts)]
                                 (let [len (count part)]
                                   (swap! state update :generated-column + len)
                                   (if (< i (dec (count parts)))
                                     (flush-line)))))

                             (source-node? node)
                             (do (when (and (:source node) (:line node) (:column node))
                                   (let [{:keys [generated-column
                                                 previous-generated-column
                                                 previous-original-line
                                                 previous-original-column
                                                 previous-source-index
                                                 previous-name-index]} @state

                                         source-idx (get-source-index (:source node))
                                         name-idx   (if (:name node) (get-name-index (:name node)))

                                         col-diff   (- generated-column previous-generated-column)
                                         source-diff (- source-idx previous-source-index)
                                         line-diff  (dec (- (:line node) previous-original-line)) ;; Source maps are 0-based, :line is usually 1-based? CLJ is 1-based.
                                         ;; Actually, VLQ is delta. If we assume :line is 1-based, and we want 0-based output...
                                         ;; 1st mapping: line 10 -> previous 0 = delta 9? (if 0-based index)
                                         ;; Let's assume input :line is 1-based. V3 map uses 0-based lines.
                                         ;; Initial previous-original-line is 0.
                                         ;; If first node is line 1, 1-based. 0-based is 0. Delta is 0 - 0 = 0.
                                         ;; So if input is 1-based, we map to 0-based before diffing.
                                         ;; But here we store previous-original-line.
                                         ;; Let's consistently store 0-based in previous-original-line.

                                         node-line-0 (dec (:line node))
                                         node-col-0  (dec (:column node)) ;; Assuming col is 1-based too? editors usually 1-based.

                                         line-diff   (- node-line-0 previous-original-line)
                                         col-diff-orig (- node-col-0 previous-original-column)

                                         segment    (cond-> [(encode-vlq col-diff)
                                                             (encode-vlq source-diff)
                                                             (encode-vlq line-diff)
                                                             (encode-vlq col-diff-orig)]
                                                      name-idx (conj (encode-vlq (- name-idx previous-name-index))))]

                                     (swap! state update :current-line-segments conj (apply str segment))

                                     (swap! state assoc :previous-generated-column generated-column)
                                     (swap! state assoc :previous-original-line node-line-0)
                                     (swap! state assoc :previous-original-column node-col-0)
                                     (swap! state assoc :previous-source-index source-idx)
                                     (if name-idx
                                       (swap! state assoc :previous-name-index name-idx))))

                                 (doseq [child (:children node)]
                                   (process child)))

                             (sequential? node)
                             (doseq [child node]
                               (process child))

                             :else
                             (process (str node))))]

    (process-node root-node)

    ;; Flush the last line segments if any?
    ;; No, if string ended without newline, we still have segments in current-line.
    ;; But we only flush on newline.
    ;; We should add the final line segments.
    (swap! state update :all-lines conj (str/join "," (:current-line-segments @state)))

    {:code code
     :map (json/write-str
           {:version 3
            :file file
            :sourceRoot source-root
            :sources (:sources @state)
            :names (:names @state)
            :mappings (str/join ";" (:all-lines @state))})}))
