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
    [com.fulcrologic.fulcro.components :as comp :refer [defsc]]
    [com.fulcrologic.fulcro.dom.html-entities :as ent]
    [com.fulcrologic.fulcro.mutations :as m]
    [com.fulcrologic.fulcro.routing.dynamic-routing :as dr :refer [defrouter]]
    [com.fulcrologic.rad.authorization :as auth]
    [com.fulcrologic.rad.form :as form]
    [com.fulcrologic.rad.ids :refer [new-uuid]]
    [com.fulcrologic.rad.routing :as rroute]
    [taoensso.timbre :as log]))

(defsc AccountsDetailDefault [this props]
  {:query         ['*]
   :ident         (fn [] [:component/id ::AccountsDetailDefault])
   :initial-state {}
   :route-segment ["default"]}
  (dom/div "AccountsDetailDefault: Some details about accounts..."))

(defrouter AccountsDetailsRouter [_ _]
  {:router-targets [AccountsDetailDefault AccountList]})

(def ui-accounts-details-router (comp/factory AccountsDetailsRouter))

(m/defmutation simulated-load [{:keys [delay-ms target]}]
  (action [{:keys [app]}]
          #?(:cljs (js/setTimeout
                     #(comp/transact! app [(dr/target-ready {:target target})])
                     delay-ms))))

(defsc Accounts [this {:acc/keys [router]}]
  {:query         [{:acc/router (comp/get-query AccountsDetailsRouter)}]
   :ident         (fn [] [:component/id ::Accounts])
   :initial-state {:acc/router {}}
   :route-segment ["accounts"]
   :will-enter    (fn [app _]
                    (let [immediate? false
                          ident [:component/id ::Accounts]]
                      (log/info "Accounts routing is " (if immediate? "immediate" "deferred"))
                      (if immediate?
                        (dr/route-immediate ident)
                        (dr/route-deferred ident
                                           #(comp/transact! app [(simulated-load {:target ident :delay-ms 4000})])))))}
  (dom/div
    (dom/h3 "Stuff about accounts")
    (dom/p "Select the desired kind of details: "
           (dom/a {:onClick (fn [] (rroute/route-to! this AccountsDetailDefault {}))} "default")
           " (AccountsDetailDefault)"
           " / "
           (dom/a {:onClick (fn [] (rroute/route-to! this AccountList {}))} "accounts")
           " (AccountList)")
    (div {:style {:border "1px dashed grey" :padding "0.3em"}}
      (ui-accounts-details-router router))))

(defsc LandingPage [this props]
  {:query         ['*]
   :ident         (fn [] [:component/id ::LandingPage])
   :initial-state {}
   :route-segment ["landing-page"]}
  (dom/div "Welcome to the Demo. Please log in."))

;; This will just be a normal router...but there can be many of them.
(defrouter MainRouter [this {:keys [current-state route-factory route-props]}]
  {:always-render-body? true
   :router-targets      [LandingPage Accounts]}
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
                   :keys       [authenticator router ui/ready?]}]
  {:query         [{:authenticator (comp/get-query Authenticator)}
                   {:router (comp/get-query MainRouter)}
                   ::app/active-remotes
                   ::auth/authorization :ui/ready?]
   :initial-state {:router        {}
                   :authenticator {}
                   :ui/ready?     false}}
  (let [logged-in? (= :success (some-> authorization :local ::auth/status))
        busy?      (or (seq active-remotes) (not ready?))
        username   (some-> authorization :local :account/name)]
    (dom/div
      (div :.ui.top.menu
        (div :.ui.item
             (dom/a {:onClick (fn [] (rroute/route-to! this LandingPage {}))} "Demo"))
        (div :.ui.item
             (dom/a {:onClick (fn [] (rroute/route-to! this AccountsDetailDefault {}))} "Accounts"))
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
      (when ready?
        (div :.ui.container.segment
             (ui-authenticator authenticator)
             (ui-main-router router))))))

(def ui-root (comp/factory Root))

