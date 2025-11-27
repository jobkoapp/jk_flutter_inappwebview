package com.pichillilorenzo.flutter_inappwebview_android.webview;

import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pichillilorenzo.flutter_inappwebview_android.webview.in_app_webview.InAppWebView;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InAppWebView 인스턴스의 전역 레지스트리
 *
 * 외부 SDK(예: AdFit, Firebase Analytics 등)에서 WebView 인스턴스에
 * 접근할 수 있도록 전역 레지스트리를 제공합니다.
 *
 * 사용 예시 (앱 프로젝트의 Kotlin/Java 코드에서):
 * <pre>
 * // WebView ID로 인스턴스 획득
 * val webView = InAppWebViewRegistry.getWebViewById(webViewId)
 * if (webView != null) {
 *     AdFitSdk.register(webView)
 * }
 *
 * // 또는 모든 WebView에 대해 처리
 * InAppWebViewRegistry.getAllWebViews().forEach { webView ->
 *     AdFitSdk.register(webView)
 * }
 * </pre>
 */
public final class InAppWebViewRegistry {
    private static final String LOG_TAG = "InAppWebViewRegistry";

    // Thread-safe한 ConcurrentHashMap 사용
    private static final Map<Object, InAppWebView> webViewMap = new ConcurrentHashMap<>();

    private InAppWebViewRegistry() {
        // 인스턴스 생성 방지 (유틸리티 클래스)
    }

    /**
     * WebView를 레지스트리에 등록합니다.
     * InAppWebView 생성 시 자동으로 호출됩니다.
     *
     * @param id WebView의 고유 ID
     * @param webView 등록할 InAppWebView 인스턴스
     */
    public static void register(@NonNull Object id, @NonNull InAppWebView webView) {
        webViewMap.put(id, webView);
    }

    /**
     * WebView를 레지스트리에서 제거합니다.
     * InAppWebView dispose 시 자동으로 호출됩니다.
     *
     * @param id WebView의 고유 ID
     */
    public static void unregister(@NonNull Object id) {
        webViewMap.remove(id);
    }

    /**
     * ID로 WebView 인스턴스를 조회합니다.
     *
     * @param id WebView의 고유 ID
     * @return InAppWebView 인스턴스, 없으면 null
     */
    @Nullable
    public static InAppWebView getWebViewById(@NonNull Object id) {
        return webViewMap.get(id);
    }

    /**
     * ID로 네이티브 WebView 인스턴스를 조회합니다.
     * InAppWebView는 WebView를 상속하므로 직접 반환됩니다.
     *
     * @param id WebView의 고유 ID
     * @return WebView 인스턴스, 없으면 null
     */
    @Nullable
    public static WebView getNativeWebViewById(@NonNull Object id) {
        return webViewMap.get(id);
    }

    /**
     * 등록된 모든 WebView 인스턴스를 반환합니다.
     *
     * @return 모든 InAppWebView 인스턴스의 컬렉션
     */
    @NonNull
    public static Collection<InAppWebView> getAllWebViews() {
        return webViewMap.values();
    }

    /**
     * 등록된 모든 WebView ID를 반환합니다.
     *
     * @return 모든 WebView ID의 컬렉션
     */
    @NonNull
    public static Collection<Object> getAllWebViewIds() {
        return webViewMap.keySet();
    }

    /**
     * 특정 ID의 WebView가 등록되어 있는지 확인합니다.
     *
     * @param id WebView의 고유 ID
     * @return 등록되어 있으면 true
     */
    public static boolean contains(@NonNull Object id) {
        return webViewMap.containsKey(id);
    }

    /**
     * 등록된 WebView 수를 반환합니다.
     *
     * @return 등록된 WebView 수
     */
    public static int size() {
        return webViewMap.size();
    }

    /**
     * 레지스트리가 비어있는지 확인합니다.
     *
     * @return 비어있으면 true
     */
    public static boolean isEmpty() {
        return webViewMap.isEmpty();
    }

    /**
     * 모든 WebView를 레지스트리에서 제거합니다.
     * 주의: 일반적으로 호출할 필요 없음
     */
    public static void clear() {
        webViewMap.clear();
    }
}
