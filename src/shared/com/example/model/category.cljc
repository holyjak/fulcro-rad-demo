(ns com.example.model.category
  (:require
    [clojure.string :as str]
    [com.fulcrologic.rad.attributes :refer [defattr]]
    [com.fulcrologic.rad.attributes-options :as ao]
    [com.fulcrologic.fulcro.components :refer [defsc]]
    #?(:clj [com.example.components.database-queries :as queries])
    [com.fulcrologic.rad.report-options :as ro]))

(defsc Category [_ _]
  {:query [:category/id :category/label]
   :ident :category/id})

(defattr id :category/id :uuid
  {ao/identity? true
   ao/schema    :production})

(defattr label :category/label :string
  {ao/required?                                          true
   ao/identities                                         #{:category/id}
   ro/column-heading                                     "Category"
   :com.fulcrologic.rad.database-adapters.sql/max-length 120
   ao/schema                                             :production})

(defattr label-uppercase :category/label-uppercase :string
  {ro/column-heading                                     "Category*"
   ao/pc-input   #{:category/label}
   ao/pc-output  [:category/label-uppercase]
   ao/pc-resolve (fn [_ {label :category/label :as params}]
                   {:category/label-uppercase (str/upper-case label)})})

(defattr all-categories :category/all-categories :ref
  {ao/target     :category/id
   ao/pc-output  [{:category/all-categories [:category/id]}]
   ao/pc-resolve (fn [{:keys [query-params] :as env} _]
                   #?(:clj
                      {:category/all-categories (queries/get-all-categories env query-params)}))})

(def attributes [id label label-uppercase all-categories])

