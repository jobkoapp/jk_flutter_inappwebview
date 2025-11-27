//
//  InAppWebViewRegistry.swift
//  flutter_inappwebview_ios
//
//  InAppWebView 인스턴스의 전역 레지스트리
//
//  외부 SDK(예: AdFit 등)에서 WKWebView 인스턴스에
//  접근할 수 있도록 전역 레지스트리를 제공합니다.
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

@objc public final class InAppWebViewRegistry: NSObject {

    // Thread-safe를 위한 concurrent queue와 barrier 사용
    private static let queue = DispatchQueue(label: "com.pichillilorenzo.InAppWebViewRegistry", attributes: .concurrent)

    // WebView 저장소
    private static var webViewMap: [AnyHashable: InAppWebView] = [:]

    private override init() {
        super.init()
        // 인스턴스 생성 방지 (유틸리티 클래스)
    }

    /// WebView를 레지스트리에 등록합니다.
    /// InAppWebView 생성 시 자동으로 호출됩니다.
    ///
    /// - Parameters:
    ///   - id: WebView의 고유 ID
    ///   - webView: 등록할 InAppWebView 인스턴스
    @objc public static func register(id: AnyHashable, webView: InAppWebView) {
        queue.async(flags: .barrier) {
            webViewMap[id] = webView
        }
    }

    /// WebView를 레지스트리에서 제거합니다.
    /// InAppWebView dispose 시 자동으로 호출됩니다.
    ///
    /// - Parameter id: WebView의 고유 ID
    @objc public static func unregister(id: AnyHashable) {
        queue.async(flags: .barrier) {
            webViewMap.removeValue(forKey: id)
        }
    }

    /// ID로 InAppWebView 인스턴스를 조회합니다.
    ///
    /// - Parameter id: WebView의 고유 ID
    /// - Returns: InAppWebView 인스턴스, 없으면 nil
    @objc public static func getWebViewById(_ id: AnyHashable) -> InAppWebView? {
        var result: InAppWebView?
        queue.sync {
            result = webViewMap[id]
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
    ///
    /// - Returns: 모든 InAppWebView 인스턴스의 배열
    @objc public static func getAllWebViews() -> [InAppWebView] {
        var result: [InAppWebView] = []
        queue.sync {
            result = Array(webViewMap.values)
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
    ///
    /// - Returns: 모든 WebView ID의 배열
    @objc public static func getAllWebViewIds() -> [AnyHashable] {
        var result: [AnyHashable] = []
        queue.sync {
            result = Array(webViewMap.keys)
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
            result = webViewMap[id] != nil
        }
        return result
    }

    /// 등록된 WebView 수를 반환합니다.
    ///
    /// - Returns: 등록된 WebView 수
    @objc public static func count() -> Int {
        var result = 0
        queue.sync {
            result = webViewMap.count
        }
        return result
    }

    /// 레지스트리가 비어있는지 확인합니다.
    ///
    /// - Returns: 비어있으면 true
    @objc public static func isEmpty() -> Bool {
        return count() == 0
    }

    /// 모든 WebView를 레지스트리에서 제거합니다.
    /// 주의: 일반적으로 호출할 필요 없음
    @objc public static func clear() {
        queue.async(flags: .barrier) {
            webViewMap.removeAll()
        }
    }
}
