(ns com.example.ui
  (:require
    #?@(:cljs [[com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown :refer [ui-dropdown]]
               [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown-menu :refer [ui-dropdown-menu]]
               [com.fulcrologic.semantic-ui.modules.dropdown.ui-dropdown-item :refer [ui-dropdown-item]]])
    #?(:clj  [com.fulcrologic.fulcro.dom-server :as dom :refer [div label input]]
       :cljs [com.fulcrologic.fulcro.dom :as dom :refer [div label input]])
    [com.example.ui.account-forms :refer [AccountForm AccountList]]
    [com.example.ui.invoice-forms :refer [InvoiceForm InvoiceList AccountInvoices]]
    [com.example.ui.item-forms :refer [ItemForm InventoryReport]]
    [com.example.ui.line-item-forms :refer [LineItemForm]]
    [com.example.ui.login-dialog :refer [LoginForm]]
    [com.example.ui.sales-report :as sales-report]
    [com.example.ui.dashboard :as dashboard]
    [com.fulcrologic.fulcro.application :as app]
    [com.fulcrologic.fulcro.algorithms.merge :as merge]
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom.html-entities :as ent]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [com.fulcrologic.rad.authorization :as auth]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.routing :as rroute]
    [taoensso.timbre :as log]))

(declare JhOrgDashboard)

(defsc LandingPage [this props]
  {:query         ['*]
   :ident         (fn [] [:component/id ::LandingPage])
   :initial-state {}
   :route-segment ["landing-page"]}
  (dom/div
    "Welcome to the Demo. Please log in."
    (dom/div (dom/a {:onClick (fn [] (rroute/route-to! this JhOrgDashboard {:fake/org-nr "123"}))} "-> JhOrgDashboard"))))

(defsc NoOrgDetails [this _]
  {:query         ['*]
   :ident         (fn [] [:component/id ::Dummy])
   :initial-state {}
   :route-segment ["no-details"]}
  (dom/p "dummy component "
         (dom/a {:onClick (fn [] (rroute/route-to! this InventoryReport {:fake/org-nr 123}))} "Show the report")))

(defrouter JhOrgDetailsRouter [_ _]
  {:router-targets [NoOrgDetails InventoryReport]})

(def ui-jh-org-details-router (comp/factory JhOrgDetailsRouter))

(defsc JhOrgDashboard [this {:fake/keys [details-router org-nr]}]
  {:query         [:fake/org-nr {:fake/details-router (comp/get-query JhOrgDetailsRouter)}]
   :ident         :fake/org-nr
   :initial-state {:fake/details-router {}}
   :will-enter    (fn [app {orgnr :fake/org-nr}]
                    (let [ident [:fake/org-nr orgnr]]
                      (dr/route-deferred ident
                                         #(do (merge/merge-component! app JhOrgDashboard {:fake/org-nr orgnr}) ; fake a load
                                              (comp/transact! app [(dr/target-ready {:target ident})])))))
   :route-segment ["jh-org-dashboard" :fake/org-nr]}
  (dom/div
    (dom/p (str "JhOrgDashboard for the fake org nr. " org-nr))
    (ui-jh-org-details-router details-router)))

;; This will just be a normal router...but there can be many of them.
(defrouter MainRouter [this {:keys [current-state route-factory route-props]}]
  {:always-render-body? true
   :router-targets      [LandingPage ItemForm InvoiceForm InvoiceList AccountList AccountForm AccountInvoices
                         sales-report/SalesReport #_InventoryReport
                         sales-report/RealSalesReport
                         #_dashboard/Dashboard
                         JhOrgDashboard]}
  ;; Normal Fulcro code to show a loader on slow route change (assuming Semantic UI here, should
  ;; be generalized for RAD so UI-specific code isn't necessary)
  (dom/div
    (dom/div :.ui.loader {:classes [(when-not (= :routed current-state) "active")]})
    (when route-factory
      (route-factory route-props))))

(def ui-main-router (comp/factory MainRouter))

(auth/defauthenticator Authenticator {:local LoginForm})

(def ui-authenticator (comp/factory Authenticator))

(defsc Root [this {::auth/keys [authorization]
                   ::app/keys  [active-remotes]
                   :keys       [authenticator router]}]
  {:query         [{:authenticator (comp/get-query Authenticator)}
                   {:router (comp/get-query MainRouter)}
                   ::app/active-remotes
                   ::auth/authorization]
   :initial-state {:router        {}
                   :authenticator {}}}
  (let [logged-in? (= :success (some-> authorization :local ::auth/status))
        busy?      (seq active-remotes)
        username   (some-> authorization :local :account/name)]
    (dom/div
      (div :.ui.top.menu
        (div :.ui.item "Demo")
        (when logged-in?
          #?(:cljs
             (comp/fragment
               (ui-dropdown {:className "item" :text "Account"}
                 (ui-dropdown-menu {}
                   (ui-dropdown-item {:onClick (fn [] (rroute/route-to! this AccountList {}))} "View All")
                   (ui-dropdown-item {:onClick (fn [] (form/create! this AccountForm))} "New")))
               (ui-dropdown {:className "item" :text "Inventory"}
                 (ui-dropdown-menu {}
                   (ui-dropdown-item {:onClick (fn [] (rroute/route-to! this InventoryReport {}))} "View All")
                   (ui-dropdown-item {:onClick (fn [] (form/create! this ItemForm))} "New")))
               (ui-dropdown {:className "item" :text "Invoices"}
                 (ui-dropdown-menu {}
                   (ui-dropdown-item {:onClick (fn [] (rroute/route-to! this InvoiceList {}))} "View All")
                   (ui-dropdown-item {:onClick (fn [] (form/create! this InvoiceForm))} "New")
                   (ui-dropdown-item {:onClick (fn [] (rroute/route-to! this AccountInvoices {:account/id (new-uuid 101)}))} "Invoices for Account 101")))
               (ui-dropdown {:className "item" :text "Reports"}
                 (ui-dropdown-menu {}
                   (ui-dropdown-item {:onClick (fn [] (rroute/route-to! this dashboard/Dashboard {}))} "Dashboard")
                   (ui-dropdown-item {:onClick (fn [] (rroute/route-to! this sales-report/RealSalesReport {}))} "Sales Report"))))))
        (div :.right.menu
          (div :.item
            (div :.ui.tiny.loader {:classes [(when busy? "active")]})
            ent/nbsp ent/nbsp ent/nbsp ent/nbsp)
          (if logged-in?
            (comp/fragment
              (div :.ui.item
                (str "Logged in as " username))
              (div :.ui.item
                (dom/button :.ui.button {:onClick (fn []
                                                    ;; TODO: check if we can change routes...
                                                    (rroute/route-to! this LandingPage {})
                                                    (auth/logout! this :local))}
                  "Logout")))
            (div :.ui.item
              (dom/button :.ui.primary.button {:onClick #(auth/authenticate! this :local nil)}
                "Login")))))
      (div :.ui.container.segment
        (ui-authenticator authenticator)
        (ui-main-router router)))))

(def ui-root (comp/factory Root))

