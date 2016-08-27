;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2015-2016 Andrey Antukh <niwi@niwi.nz>
;; Copyright (c) 2015-2016 Juan de la Cruz <delacruzgarciajuan@gmail.com>

(ns uxbox.main.ui
  (:require [sablono.core :as html :refer-macros [html]]
            [promesa.core :as p]
            [beicon.core :as rx]
            [goog.dom :as gdom]
            [rum.core :as rum]
            [lentes.core :as l]
            [uxbox.main.state :as st]
            [uxbox.util.router :as rt]
            [uxbox.util.rstore :as rs]
            [uxbox.util.i18n :refer (tr)]
            [uxbox.main.data.projects :as dp]
            [uxbox.main.data.users :as udu]
            [uxbox.main.data.auth :as dauth]
            [uxbox.main.data.messages :as dmsg]
            [uxbox.main.ui.loader :refer (loader)]
            [uxbox.main.ui.lightbox :refer (lightbox)]
            [uxbox.main.ui.icons :as i]
            [uxbox.main.ui.auth :as auth]
            [uxbox.main.ui.dashboard :as dashboard]
            [uxbox.main.ui.settings :as settings]
            [uxbox.main.ui.workspace :refer (workspace)]
            [uxbox.util.mixins :as mx]
            [uxbox.main.ui.shapes]))

;; --- Constants

(def +unrestricted+
  #{:auth/login
    :auth/register
    :auth/recovery-request
    :auth/recovery})

(def restricted?
  (complement +unrestricted+))

(def route-ref
  (-> (l/key :route)
      (l/derive st/state)))

;; --- Error Handling

(defn- on-error
  "A default error handler."
  [error]
  (cond
    ;; Unauthorized or Auth timeout
    (and (:status error)
         (:payload error)
         (or (= (:status error) 403)
             (= (:status error) 419)))
    (rs/emit! (dauth/logout))

    ;; Network error
    (= (:status error) 0)
    (do
      (dmsg/error! (tr "errors.network"))
      (js/console.error "Stack:" (.-stack error)))

    ;; Something else
    :else
    (do
      (dmsg/error! (tr "errors.generic"))
      (js/console.error "Stack:" (.-stack error)))))

(rs/add-error-watcher :ui on-error)

;; --- Main App (Component)

(defn app-render
  [own]
  (let [route (mx/react route-ref)
        auth (mx/react st/auth-ref)
        location (:id route)
        params (:params route)]
    (if (and (restricted? location) (not auth))
      (do (p/schedule 0 #(rt/go :auth/login)) nil)
      (case location
        :auth/login (auth/login-page)
        :auth/register (auth/register-page)
        :auth/recovery-request (auth/recovery-request-page)
        :auth/recovery (auth/recovery-page (:token params))
        :dashboard/projects (dashboard/projects-page)
        :dashboard/elements (dashboard/elements-page)
        :dashboard/icons (dashboard/icons-page)
        :dashboard/images (dashboard/images-page)
        :dashboard/colors (dashboard/colors-page)
        :settings/profile (settings/profile-page)
        :settings/password (settings/password-page)
        :settings/notifications (settings/notifications-page)
        :workspace/page (let [projectid (uuid (:project params))
                              pageid (uuid (:page params))]
                          (workspace projectid pageid))
        nil
        ))))

(defn app-will-mount
  [own]
  (when @st/auth-ref
    (rs/emit! (udu/fetch-profile)))
  own)

(def app
  (mx/component
   {:render app-render
    :will-mount app-will-mount
    :mixins [mx/reactive]
    :name "app"}))

;; --- Routes

(def routes
  [["/auth/login" :auth/login]
   ["/auth/recovery/token/:token" :auth/recovery]
   ["/settings/profile" :settings/profile]
   ["/settings/password" :settings/password]
   ["/settings/notifications" :settings/notifications]
   ["/dashboard/projects" :dashboard/projects]
   ["/dashboard/elements" :dashboard/elements]
   ["/dashboard/icons" :dashboard/icons]
   ["/dashboard/images" :dashboard/images]
   ["/dashboard/colors" :dashboard/colors]
   ["/workspace/:project/:page" :workspace/page]])

;; --- Main Entry Point

(defn init-routes
  []
  (rt/init routes))

(defn init
  []
  (let [app-dom (gdom/getElement "app")
        lightbox-dom (gdom/getElement "lightbox")
        loader-dom (gdom/getElement "loader")]
    (rum/mount (app) app-dom)
    (rum/mount (lightbox) lightbox-dom)
    (rum/mount (loader) loader-dom)))
