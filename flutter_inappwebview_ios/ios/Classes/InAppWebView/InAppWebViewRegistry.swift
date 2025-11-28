//
//  InAppWebViewRegistry.swift
//  flutter_inappwebview_ios
//
//  InAppWebView 인스턴스의 전역 레지스트리
//
//  외부 SDK(예: AdFit 등)에서 WKWebView 인스턴스에
//  접근할 수 있도록 전역 레지스트리를 제공합니다.
//
//  WeakReference를 사용하여 메모리 누수를 방지합니다.
//
//  사용 예시 (앱 프로젝트의 Swift 코드에서):
//  ```swift
//  // WebView ID로 인스턴스 획득
//  if let webView = InAppWebViewRegistry.getWebViewById(webViewId) {
//      AdFit.register(webView: webView)
//  }
//
//  // 또는 모든 WebView에 대해 처리
//  InAppWebViewRegistry.getAllWebViews().forEach { webView in
//      AdFit.register(webView: webView)
//  }
//  ```

import Foundation
import WebKit

/// WeakReference wrapper for InAppWebView (메모리 누수 방지)
private class WeakWebView {
    weak var webView: InAppWebView?
    init(_ webView: InAppWebView) {
        self.webView = webView
    }
}

/// WebView 해제 시 알림을 받기 위한 프로토콜
@objc public protocol InAppWebViewRegistryUnregisterListener: AnyObject {
    func onWebViewUnregistered(id: AnyHashable)
}

@objc public final class InAppWebViewRegistry: NSObject {

    // Thread-safe를 위한 concurrent queue와 barrier 사용
    private static let queue = DispatchQueue(label: "com.pichillilorenzo.InAppWebViewRegistry", attributes: .concurrent)

    // WebView 저장소 (WeakReference 사용)
    private static var webViewMap: [AnyHashable: WeakWebView] = [:]

    // WebView 해제 시 알림을 받을 리스너들 (weak reference로 메모리 누수 방지)
    private static var unregisterListeners = NSHashTable<AnyObject>.weakObjects()

    private override init() {
        super.init()
        // 인스턴스 생성 방지 (유틸리티 클래스)
    }

    /// 해제 리스너를 등록합니다.
    @objc public static func addUnregisterListener(_ listener: InAppWebViewRegistryUnregisterListener) {
        queue.sync(flags: .barrier) {
            unregisterListeners.add(listener)
        }
    }

    /// 해제 리스너를 제거합니다.
    @objc public static func removeUnregisterListener(_ listener: InAppWebViewRegistryUnregisterListener) {
        queue.sync(flags: .barrier) {
            unregisterListeners.remove(listener)
        }
    }

    /// 리스너들에게 WebView 해제 알림을 보냅니다.
    private static func notifyUnregisterListeners(id: AnyHashable) {
        var listenersCopy: [InAppWebViewRegistryUnregisterListener] = []
        queue.sync {
            listenersCopy = unregisterListeners.allObjects.compactMap { $0 as? InAppWebViewRegistryUnregisterListener }
        }
        for listener in listenersCopy {
            listener.onWebViewUnregistered(id: id)
        }
    }

    /// WebView를 레지스트리에 등록합니다.
    /// InAppWebView 생성 시 자동으로 호출됩니다.
    /// 동기 실행으로 등록 직후 조회 시 race condition 방지
    ///
    /// - Parameters:
    ///   - id: WebView의 고유 ID
    ///   - webView: 등록할 InAppWebView 인스턴스
    @objc public static func register(id: AnyHashable, webView: InAppWebView) {
        queue.sync(flags: .barrier) {
            webViewMap[id] = WeakWebView(webView)
        }
    }

    /// WebView를 레지스트리에서 제거합니다.
    /// InAppWebView dispose 시 자동으로 호출됩니다.
    /// 등록된 리스너들에게 알림을 보냅니다.
    ///
    /// - Parameter id: WebView의 고유 ID
    @objc public static func unregister(id: AnyHashable) {
        queue.sync(flags: .barrier) {
            webViewMap.removeValue(forKey: id)
        }
        notifyUnregisterListeners(id: id)
    }

    /// ID로 InAppWebView 인스턴스를 조회합니다.
    /// GC된 경우 자동으로 맵에서 제거됩니다.
    ///
    /// - Parameter id: WebView의 고유 ID
    /// - Returns: InAppWebView 인스턴스, 없으면 nil
    @objc public static func getWebViewById(_ id: AnyHashable) -> InAppWebView? {
        var result: InAppWebView?
        var needsCleanup = false

        queue.sync {
            if let weakRef = webViewMap[id] {
                result = weakRef.webView
                needsCleanup = (result == nil)
            }
        }

        // GC된 경우 별도로 제거 (sync 블록 외부에서 처리)
        if needsCleanup {
            queue.async(flags: .barrier) {
                if webViewMap[id]?.webView == nil {
                    webViewMap.removeValue(forKey: id)
                }
            }
        }

        return result
    }

    /// ID로 네이티브 WKWebView 인스턴스를 조회합니다.
    /// InAppWebView는 WKWebView를 상속하므로 직접 반환됩니다.
    ///
    /// - Parameter id: WebView의 고유 ID
    /// - Returns: WKWebView 인스턴스, 없으면 nil
    @objc public static func getNativeWebViewById(_ id: AnyHashable) -> WKWebView? {
        return getWebViewById(id)
    }

    /// 등록된 모든 InAppWebView 인스턴스를 반환합니다.
    /// GC된 WebView는 자동으로 제외되고 정리됩니다.
    ///
    /// - Returns: 모든 InAppWebView 인스턴스의 배열 (스냅샷)
    @objc public static func getAllWebViews() -> [InAppWebView] {
        var result: [InAppWebView] = []
        var keysToRemove: [AnyHashable] = []

        queue.sync {
            for (key, weakRef) in webViewMap {
                if let webView = weakRef.webView {
                    result.append(webView)
                } else {
                    keysToRemove.append(key)
                }
            }
        }

        // GC된 항목 제거
        if !keysToRemove.isEmpty {
            queue.async(flags: .barrier) {
                for key in keysToRemove {
                    if webViewMap[key]?.webView == nil {
                        webViewMap.removeValue(forKey: key)
                    }
                }
            }
        }

        return result
    }

    /// 등록된 모든 WKWebView 인스턴스를 반환합니다.
    ///
    /// - Returns: 모든 WKWebView 인스턴스의 배열
    @objc public static func getAllNativeWebViews() -> [WKWebView] {
        return getAllWebViews()
    }

    /// 등록된 모든 WebView ID를 반환합니다.
    /// GC된 WebView의 ID는 자동으로 제외됩니다.
    ///
    /// - Returns: 모든 WebView ID의 배열 (스냅샷)
    @objc public static func getAllWebViewIds() -> [AnyHashable] {
        var result: [AnyHashable] = []
        var keysToRemove: [AnyHashable] = []

        queue.sync {
            for (key, weakRef) in webViewMap {
                if weakRef.webView != nil {
                    result.append(key)
                } else {
                    keysToRemove.append(key)
                }
            }
        }

        // GC된 항목 제거
        if !keysToRemove.isEmpty {
            queue.async(flags: .barrier) {
                for key in keysToRemove {
                    if webViewMap[key]?.webView == nil {
                        webViewMap.removeValue(forKey: key)
                    }
                }
            }
        }

        return result
    }

    /// 특정 ID의 WebView가 등록되어 있는지 확인합니다.
    ///
    /// - Parameter id: WebView의 고유 ID
    /// - Returns: 등록되어 있으면 true
    @objc public static func contains(id: AnyHashable) -> Bool {
        var result = false
        queue.sync {
            if let weakRef = webViewMap[id] {
                result = weakRef.webView != nil
            }
        }
        return result
    }

    /// 등록된 WebView 수를 반환합니다.
    /// GC된 항목은 제외하여 카운트합니다.
    ///
    /// - Returns: 등록된 WebView 수
    @objc public static func count() -> Int {
        var result = 0
        queue.sync {
            for (_, weakRef) in webViewMap {
                if weakRef.webView != nil {
                    result += 1
                }
            }
        }
        return result
    }

    /// 레지스트리가 비어있는지 확인합니다.
    /// 최적화: count()를 호출하지 않고 직접 체크
    ///
    /// - Returns: 비어있으면 true
    @objc public static func isEmpty() -> Bool {
        var result = true
        queue.sync {
            for (_, weakRef) in webViewMap {
                if weakRef.webView != nil {
                    result = false
                    break
                }
            }
        }
        return result
    }

    /// 모든 WebView를 레지스트리에서 제거합니다.
    /// 주의: 일반적으로 호출할 필요 없음
    @objc public static func clear() {
        queue.sync(flags: .barrier) {
            webViewMap.removeAll()
        }
    }
}
