/*
 Licensed to the Apache Software Foundation (ASF) under one
 or more contributor license agreements.  See the NOTICE file
 distributed with this work for additional information
 regarding copyright ownership.  The ASF licenses this file
 to you under the Apache License, Version 2.0 (the
 "License"); you may not use this file except in compliance
 with the License.  You may obtain a copy of the License at
 http://www.apache.org/licenses/LICENSE-2.0
 Unless required by applicable law or agreed to in writing,
 software distributed under the License is distributed on an
 "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 KIND, either express or implied.  See the License for the
 specific language governing permissions and limitations
 under the License.
 */

import Flutter
import UIKit
import WebKit
import Foundation
import AVFoundation
import SafariServices

public class SwiftFlutterPlugin: NSObject, FlutterPlugin, InAppWebViewRegistryUnregisterListener {

    var registrar: FlutterPluginRegistrar?
    var platformUtil: PlatformUtil?
    var inAppWebViewManager: InAppWebViewManager?
    var myCookieManager: Any?
    var myWebStorageManager: Any?
    var credentialDatabase: CredentialDatabase?
    var inAppBrowserManager: InAppBrowserManager?
    var headlessInAppWebViewManager: HeadlessInAppWebViewManager?
    var chromeSafariBrowserManager: ChromeSafariBrowserManager?
    var webAuthenticationSessionManager: WebAuthenticationSessionManager?
    var printJobManager: PrintJobManager?

    // AdFit MethodChannel
    private var adfitChannel: FlutterMethodChannel?
    private static var registeredWebViewIds = Set<AnyHashable>()

    var webViewControllers: [String: InAppBrowserWebViewController?] = [:]
    var safariViewControllers: [String: Any?] = [:]

    // MARK: - InAppWebViewRegistryUnregisterListener

    /// WebView 해제 시 registeredWebViewIds에서 자동으로 제거
    public func onWebViewUnregistered(id: AnyHashable) {
        if SwiftFlutterPlugin.registeredWebViewIds.remove(id) != nil {
            print("[AdFit] 🧹 Auto-cleaned WebView from registeredWebViewIds: \(id)")
        }
    }

    public init(with registrar: FlutterPluginRegistrar) {
        super.init()

        self.registrar = registrar
        registrar.register(FlutterWebViewFactory(plugin: self) as FlutterPlatformViewFactory, withId: FlutterWebViewFactory.VIEW_TYPE_ID)

        platformUtil = PlatformUtil(plugin: self)
        inAppBrowserManager = InAppBrowserManager(plugin: self)
        headlessInAppWebViewManager = HeadlessInAppWebViewManager(plugin: self)
        chromeSafariBrowserManager = ChromeSafariBrowserManager(plugin: self)
        inAppWebViewManager = InAppWebViewManager(plugin: self)
        credentialDatabase = CredentialDatabase(plugin: self)
        if #available(iOS 11.0, *) {
            myCookieManager = MyCookieManager(plugin: self)
        }
        if #available(iOS 9.0, *) {
            myWebStorageManager = MyWebStorageManager(plugin: self)
        }
        webAuthenticationSessionManager = WebAuthenticationSessionManager(plugin: self)
        printJobManager = PrintJobManager(plugin: self)

        // AdFit MethodChannel 초기화
        adfitChannel = FlutterMethodChannel(name: "flutter_inappwebview/adfit", binaryMessenger: registrar.messenger())
        adfitChannel?.setMethodCallHandler(handleAdfitMethodCall)

        // WebView 해제 리스너 등록
        InAppWebViewRegistry.addUnregisterListener(self)
    }

    // MARK: - AdFit MethodChannel Handler

    private func handleAdfitMethodCall(call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "registerAdFit":
            if let args = call.arguments as? [String: Any],
               let webViewId = args["webViewId"] as? AnyHashable {
                let success = registerAdFitForWebView(webViewId: webViewId)
                result(success)
            } else {
                result(FlutterError(code: "INVALID_ARGUMENT", message: "webViewId is required", details: nil))
            }

        case "registerAdFitToAllWebViews":
            let count = registerAdFitToAllWebViews()
            result(count)

        case "unregisterAdFit":
            if let args = call.arguments as? [String: Any],
               let webViewId = args["webViewId"] as? AnyHashable {
                unregisterAdFitForWebView(webViewId: webViewId)
                result(true)
            } else {
                result(false)
            }

        case "getRegisteredCount":
            result(InAppWebViewRegistry.count())

        default:
            result(FlutterMethodNotImplemented)
        }
    }

    private func registerAdFitForWebView(webViewId: AnyHashable) -> Bool {
        if SwiftFlutterPlugin.registeredWebViewIds.contains(webViewId) {
            print("[AdFit] WebView already registered: \(webViewId)")
            return true
        }

        guard let webView = InAppWebViewRegistry.getNativeWebViewById(webViewId) else {
            print("[AdFit] ⚠️ WebView not found in registry: \(webViewId)")
            return false
        }

        // AdFit iOS SDK 등록
        // AdFit.register(webView: webView)
        // TODO: AdFit iOS SDK import 후 활성화

        SwiftFlutterPlugin.registeredWebViewIds.insert(webViewId)
        print("[AdFit] ✅ Registered WebView: \(webViewId)")
        return true
    }

    private func registerAdFitToAllWebViews() -> Int {
        var count = 0
        for id in InAppWebViewRegistry.getAllWebViewIds() {
            if !SwiftFlutterPlugin.registeredWebViewIds.contains(id) {
                if registerAdFitForWebView(webViewId: id) {
                    count += 1
                }
            }
        }
        print("[AdFit] 📊 Registered \(count) new WebViews (total: \(SwiftFlutterPlugin.registeredWebViewIds.count))")
        return count
    }

    private func unregisterAdFitForWebView(webViewId: AnyHashable) {
        if SwiftFlutterPlugin.registeredWebViewIds.remove(webViewId) != nil {
            print("[AdFit] 🗑️ Unregistered WebView: \(webViewId)")
        }
    }
    
    public static func register(with registrar: FlutterPluginRegistrar) {
        let _ = SwiftFlutterPlugin(with: registrar)
    }
    
    public func detachFromEngine(for registrar: FlutterPluginRegistrar) {
        // WebView 해제 리스너 제거
        InAppWebViewRegistry.removeUnregisterListener(self)

        // AdFit MethodChannel 해제
        adfitChannel?.setMethodCallHandler(nil)
        adfitChannel = nil

        platformUtil?.dispose()
        platformUtil = nil
        inAppBrowserManager?.dispose()
        inAppBrowserManager = nil
        headlessInAppWebViewManager?.dispose()
        headlessInAppWebViewManager = nil
        chromeSafariBrowserManager?.dispose()
        chromeSafariBrowserManager = nil
        inAppWebViewManager?.dispose()
        inAppWebViewManager = nil
        credentialDatabase?.dispose()
        credentialDatabase = nil
        if #available(iOS 11.0, *) {
            (myCookieManager as! MyCookieManager?)?.dispose()
            myCookieManager = nil
        }
        if #available(iOS 9.0, *) {
            (myWebStorageManager as! MyWebStorageManager?)?.dispose()
            myWebStorageManager = nil
        }
        webAuthenticationSessionManager?.dispose()
        webAuthenticationSessionManager = nil
        printJobManager?.dispose()
        printJobManager = nil
    }
}
