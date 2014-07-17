(ns tixi.position
  (:require-macros [dommy.macros :refer (node sel1)]
                   [tixi.utils :refer (b)])
  (:require [dommy.core :as dommy]
            [tixi.geometry :as g :refer [Size Rect Point]]
            [tixi.data :as d]
            [tixi.items :as i]
            [goog.style :as style]
            [tixi.utils :refer [p]]))

(defn- calculate-letter-size []
  (let [number-of-x 100]
    (dommy/append! (sel1 :body)
                   [:.calculate-letter-size (apply str (repeat number-of-x "X"))])
    (let [calculator (sel1 :.calculate-letter-size)
          width (.-offsetWidth calculator)
          height (.-offsetHeight calculator)
          result (Size. (.round js/Math (/ width number-of-x)) height)]
      (dommy/remove! calculator)
      result)))

;; TODO: Sometimes it forgets it?
(defn- letter-size []
  ((memoize calculate-letter-size)))

(defn- hit-item? [item point]
  (let [rect (g/build-rect (i/origin item) (i/dimensions item))]
    (g/inside? rect point)))

(defn- item-has-point? [id item point]
  (let [index (:index (d/item-cache id))
        moved-point (g/sub point (i/origin item))
        [x y] (g/values moved-point)
        k (str x "_" y)]
    (boolean (aget index k))))

(defn width->position [width]
  (.floor js/Math (* width (:width (letter-size)))))

(defn position->width [pos]
  (.floor js/Math (/ pos (:width (letter-size)))))

(defn height->position [height]
  (.floor js/Math (* height (:height (letter-size)))))

(defn position->height [pos]
  (.floor js/Math (/ pos (:height (letter-size)))))

(defn canvas-size []
  (Size. (position->width (.-innerWidth js/window))
         (position->height (.-innerHeight js/window))))

(defprotocol IConvert
  (position->coords [this])
  (coords->position [this]))

(extend-type Rect
  IConvert
  (position->coords [this]
    (let [{:keys [width height]} (letter-size)
          [x1 y1 x2 y2] (g/values this)]
      (Rect. (Point. (position->width x1) (position->height y1))
             (Point. (position->width x2) (position->height y2)))))
  (coords->position [this]
    (let [{:keys [width height]} (letter-size)
          [x1 y1 x2 y2] (g/values this)]
      (Rect. (Point. (width->position x1) (height->position y1))
             (Point. (width->position x2) (height->position y2))))))

(extend-type Point
  IConvert
  (position->coords [this]
    (let [{:keys [width height]} (letter-size)
          [x y] (g/values this)]
      (Point. (position->width x) (position->height y))))
  (coords->position [this]
    (let [{:keys [width height]} (letter-size)
         [x y] (g/values this)]
      (Point. (width->position x) (height->position y)))))

(extend-type Size
  IConvert
  (position->coords [this]
    (let [{:keys [width height]} (letter-size)
          [x y] (g/values this)]
      (Size. (position->width x) (position->height y))))
  (coords->position [this]
    (let [{:keys [width height]} (letter-size)
         [x y] (g/values this)]
      (Size. (width->position x) (height->position y)))))

(defn event->coords [event]
  (let [root (sel1 :.project)
        offset (if root (style/getPageOffset root) (js-obj "x" 0 "y" 0))
        x (- (.-clientX event) (.-x offset))
        y (- (.-clientY event) (.-y offset))]
    (position->coords (Point. x y))))

(defn items-inside-rect [rect]
  (filter (fn [[_ item]]
            (let [input-rect (g/normalize (:input item))
                  sel-rect (g/normalize rect)]
              (g/inside? sel-rect input-rect)))
          (d/completed)))

(defn- item-at-client-point [client-point]
  (when client-point
    (let [[x y] (g/values client-point)
          element-at-client-point (.elementFromPoint js/document x y)
          [_, id-str] (re-find #"^text-content-(\d+)" (.-id (.-parentNode element-at-client-point)))]
      (when id-str
        (let [id (js/parseInt id-str 10)]
          [id (d/completed-item id)])))))

(defn items-at-point
  ([point] (items-at-point point nil))
  ([point client-point]
    (if-let [result (item-at-client-point client-point)]
      [result]
      (->> (d/completed)
        (filter (fn [[_ item]] (hit-item? item point)))
        (filter (fn [[id item]] (item-has-point? id item point)))))))

(defn items-with-outlet-at-point [connector-id point]
  (->> (items-at-point point)
       (keep (fn [[id item]]
               (when (not= id connector-id)
                 (when-let [used-outlet (first (filter (fn [rel-outlet]
                                                         (= point (g/absolute rel-outlet (:input item))))
                                                       (i/outlets item)))]
                   [id item used-outlet]))))))

(defn item-id-at-point
  ([point] (item-id-at-point point nil))
  ([point client-point] (first (first (items-at-point point client-point)))))

(defn items-wrapping-rect [ids]
  (when (and ids (not-empty ids))
    (if (and (= (count ids) 1)
             (contains? #{:line :rect-line} (:type (d/completed-item (first ids)))))
      (:input (d/completed-item (first ids)))
      (g/wrapping-rect (map #(:input (d/completed-item %)) ids)))))
