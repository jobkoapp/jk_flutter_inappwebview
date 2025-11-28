package com.pichillilorenzo.flutter_inappwebview_android.webview;

import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pichillilorenzo.flutter_inappwebview_android.webview.in_app_webview.InAppWebView;

import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * InAppWebView 인스턴스의 전역 레지스트리
 *
 * 외부 SDK(예: AdFit, Firebase Analytics 등)에서 WebView 인스턴스에
 * 접근할 수 있도록 전역 레지스트리를 제공합니다.
 *
 * WeakReference를 사용하여 메모리 누수를 방지합니다.
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
    // Thread-safe한 ConcurrentHashMap + WeakReference 사용 (메모리 누수 방지)
    private static final Map<Object, WeakReference<InAppWebView>> webViewMap = new ConcurrentHashMap<>();

    // WebView 해제 시 알림을 받을 리스너들
    private static final List<UnregisterListener> unregisterListeners = new ArrayList<>();

    /**
     * WebView 해제 시 알림을 받기 위한 리스너 인터페이스
     */
    public interface UnregisterListener {
        void onWebViewUnregistered(Object id);
    }

    /**
     * 해제 리스너를 등록합니다.
     */
    public static void addUnregisterListener(UnregisterListener listener) {
        synchronized (unregisterListeners) {
            if (!unregisterListeners.contains(listener)) {
                unregisterListeners.add(listener);
            }
        }
    }

    /**
     * 해제 리스너를 제거합니다.
     */
    public static void removeUnregisterListener(UnregisterListener listener) {
        synchronized (unregisterListeners) {
            unregisterListeners.remove(listener);
        }
    }

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
        webViewMap.put(id, new WeakReference<>(webView));
    }

    /**
     * WebView를 레지스트리에서 제거합니다.
     * InAppWebView dispose 시 자동으로 호출됩니다.
     * 등록된 리스너들에게 알림을 보냅니다.
     *
     * @param id WebView의 고유 ID
     */
    public static void unregister(@NonNull Object id) {
        webViewMap.remove(id);
        notifyUnregisterListeners(id);
    }

    /**
     * 리스너들에게 WebView 해제 알림을 보냅니다.
     */
    private static void notifyUnregisterListeners(Object id) {
        List<UnregisterListener> listenersCopy;
        synchronized (unregisterListeners) {
            listenersCopy = new ArrayList<>(unregisterListeners);
        }
        for (UnregisterListener listener : listenersCopy) {
            try {
                listener.onWebViewUnregistered(id);
            } catch (Exception ignored) {
                // 리스너 예외가 다른 리스너에 영향주지 않도록
            }
        }
    }

    /**
     * ID로 WebView 인스턴스를 조회합니다.
     *
     * @param id WebView의 고유 ID
     * @return InAppWebView 인스턴스, 없으면 null
     */
    @Nullable
    public static InAppWebView getWebViewById(@NonNull Object id) {
        WeakReference<InAppWebView> ref = webViewMap.get(id);
        if (ref == null) return null;
        InAppWebView webView = ref.get();
        // GC된 경우 맵에서 제거
        if (webView == null) {
            webViewMap.remove(id);
        }
        return webView;
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
        return getWebViewById(id);
    }

    /**
     * 등록된 모든 WebView 인스턴스를 반환합니다.
     * GC된 WebView는 자동으로 제외됩니다.
     *
     * @return 모든 InAppWebView 인스턴스의 리스트 (스냅샷)
     */
    @NonNull
    public static List<InAppWebView> getAllWebViews() {
        List<InAppWebView> result = new ArrayList<>();
        List<Object> keysToRemove = new ArrayList<>();

        for (Map.Entry<Object, WeakReference<InAppWebView>> entry : webViewMap.entrySet()) {
            InAppWebView webView = entry.getValue().get();
            if (webView != null) {
                result.add(webView);
            } else {
                keysToRemove.add(entry.getKey());
            }
        }

        // GC된 항목 별도로 제거
        for (Object key : keysToRemove) {
            webViewMap.remove(key);
        }

        return result;
    }

    /**
     * 등록된 모든 WebView ID를 반환합니다.
     * GC된 WebView의 ID는 자동으로 제외됩니다.
     *
     * @return 모든 WebView ID의 리스트 (스냅샷)
     */
    @NonNull
    public static List<Object> getAllWebViewIds() {
        List<Object> result = new ArrayList<>();
        List<Object> keysToRemove = new ArrayList<>();

        for (Map.Entry<Object, WeakReference<InAppWebView>> entry : webViewMap.entrySet()) {
            if (entry.getValue().get() != null) {
                result.add(entry.getKey());
            } else {
                keysToRemove.add(entry.getKey());
            }
        }

        // GC된 항목 별도로 제거
        for (Object key : keysToRemove) {
            webViewMap.remove(key);
        }

        return result;
    }

    /**
     * 등록된 모든 WebView Entry를 반환합니다.
     * 이중 조회를 방지하기 위한 최적화 메서드입니다.
     * GC된 WebView는 자동으로 제외됩니다.
     *
     * @return ID와 WebView 쌍의 리스트 (스냅샷)
     */
    @NonNull
    public static List<Map.Entry<Object, WebView>> getAllEntries() {
        List<Map.Entry<Object, WebView>> result = new ArrayList<>();
        List<Object> keysToRemove = new ArrayList<>();

        for (Map.Entry<Object, WeakReference<InAppWebView>> entry : webViewMap.entrySet()) {
            InAppWebView webView = entry.getValue().get();
            if (webView != null) {
                result.add(new AbstractMap.SimpleEntry<>(entry.getKey(), webView));
            } else {
                keysToRemove.add(entry.getKey());
            }
        }

        // GC된 항목 별도로 제거
        for (Object key : keysToRemove) {
            webViewMap.remove(key);
        }

        return result;
    }

    /**
     * 특정 ID의 WebView가 등록되어 있는지 확인합니다.
     *
     * @param id WebView의 고유 ID
     * @return 등록되어 있으면 true
     */
    public static boolean contains(@NonNull Object id) {
        WeakReference<InAppWebView> ref = webViewMap.get(id);
        if (ref == null) return false;
        InAppWebView webView = ref.get();
        if (webView == null) {
            webViewMap.remove(id);
            return false;
        }
        return true;
    }

    /**
     * 등록된 WebView 수를 반환합니다.
     * 주의: GC된 항목을 정리하면서 카운트하므로 약간의 오버헤드가 있습니다.
     *
     * @return 등록된 WebView 수
     */
    public static int size() {
        int count = 0;
        List<Object> keysToRemove = new ArrayList<>();

        for (Map.Entry<Object, WeakReference<InAppWebView>> entry : webViewMap.entrySet()) {
            if (entry.getValue().get() != null) {
                count++;
            } else {
                keysToRemove.add(entry.getKey());
            }
        }

        // GC된 항목 별도로 제거
        for (Object key : keysToRemove) {
            webViewMap.remove(key);
        }

        return count;
    }

    /**
     * 레지스트리가 비어있는지 확인합니다.
     *
     * @return 비어있으면 true
     */
    public static boolean isEmpty() {
        return size() == 0;
    }

    /**
     * 모든 WebView를 레지스트리에서 제거합니다.
     * 주의: 일반적으로 호출할 필요 없음
     */
    public static void clear() {
        webViewMap.clear();
    }
}
