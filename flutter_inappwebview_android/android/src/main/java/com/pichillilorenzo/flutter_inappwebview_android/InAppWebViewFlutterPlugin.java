package com.pichillilorenzo.flutter_inappwebview_android;

import android.app.Activity;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.pichillilorenzo.flutter_inappwebview_android.chrome_custom_tabs.ChromeSafariBrowserManager;
import com.pichillilorenzo.flutter_inappwebview_android.chrome_custom_tabs.NoHistoryCustomTabsActivityCallbacks;
import com.pichillilorenzo.flutter_inappwebview_android.credential_database.CredentialDatabaseHandler;
import com.pichillilorenzo.flutter_inappwebview_android.headless_in_app_webview.HeadlessInAppWebViewManager;
import com.pichillilorenzo.flutter_inappwebview_android.in_app_browser.InAppBrowserManager;
import com.pichillilorenzo.flutter_inappwebview_android.print_job.PrintJobManager;
import com.pichillilorenzo.flutter_inappwebview_android.process_global_config.ProcessGlobalConfigManager;
import com.pichillilorenzo.flutter_inappwebview_android.proxy.ProxyManager;
import com.pichillilorenzo.flutter_inappwebview_android.service_worker.ServiceWorkerManager;
import com.pichillilorenzo.flutter_inappwebview_android.tracing.TracingControllerManager;
import com.pichillilorenzo.flutter_inappwebview_android.webview.FlutterWebViewFactory;
import com.pichillilorenzo.flutter_inappwebview_android.webview.InAppWebViewManager;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.platform.PlatformViewRegistry;
import io.flutter.embedding.android.FlutterView;

import com.pichillilorenzo.flutter_inappwebview_android.webview.InAppWebViewRegistry;

import android.util.Log;
import android.webkit.WebView;

public class InAppWebViewFlutterPlugin implements FlutterPlugin, ActivityAware {

  protected static final String LOG_TAG = "InAppWebViewFlutterPL";

  // AdFit MethodChannel
  private static final String ADFIT_CHANNEL = "flutter_inappwebview/adfit";
  @Nullable
  private MethodChannel adfitChannel;

  @Nullable
  public PlatformUtil platformUtil;
  @Nullable
  public InAppBrowserManager inAppBrowserManager;
  @Nullable
  public HeadlessInAppWebViewManager headlessInAppWebViewManager;
  @Nullable
  public ChromeSafariBrowserManager chromeSafariBrowserManager;
  @Nullable
  public NoHistoryCustomTabsActivityCallbacks noHistoryCustomTabsActivityCallbacks;
  @Nullable
  public InAppWebViewManager inAppWebViewManager;
  @Nullable
  public MyCookieManager myCookieManager;
  @Nullable
  public CredentialDatabaseHandler credentialDatabaseHandler;
  @Nullable
  public MyWebStorage myWebStorage;
  @Nullable
  public ServiceWorkerManager serviceWorkerManager;
  @Nullable
  public WebViewFeatureManager webViewFeatureManager;
  @Nullable
  public ProxyManager proxyManager;
  @Nullable
  public PrintJobManager printJobManager;
  @Nullable
  public TracingControllerManager tracingControllerManager;
  @Nullable
  public ProcessGlobalConfigManager processGlobalConfigManager;
  public FlutterWebViewFactory flutterWebViewFactory;
  public Context applicationContext;
  public BinaryMessenger messenger;
  public FlutterPlugin.FlutterAssets flutterAssets;
  @Nullable
  public ActivityPluginBinding activityPluginBinding;
  @Nullable
  public Activity activity;
  public FlutterView flutterView;

  public InAppWebViewFlutterPlugin() {}

  @Override
  public void onAttachedToEngine(FlutterPluginBinding binding) {
    this.flutterAssets = binding.getFlutterAssets();

    // Shared.activity could be null or not.
    // It depends on who is called first between onAttachedToEngine event and onAttachedToActivity event.
    //
    // See https://github.com/pichillilorenzo/flutter_inappwebview/issues/390#issuecomment-647039084
    onAttachedToEngine(
            binding.getApplicationContext(), binding.getBinaryMessenger(), this.activity, binding.getPlatformViewRegistry(), null);
  }

  private void onAttachedToEngine(Context applicationContext, BinaryMessenger messenger, Activity activity, PlatformViewRegistry platformViewRegistry, FlutterView flutterView) {
    this.applicationContext = applicationContext;
    this.activity = activity;
    this.messenger = messenger;
    this.flutterView = flutterView;

    inAppBrowserManager = new InAppBrowserManager(this);
    headlessInAppWebViewManager = new HeadlessInAppWebViewManager(this);
    chromeSafariBrowserManager = new ChromeSafariBrowserManager(this);
    noHistoryCustomTabsActivityCallbacks = new NoHistoryCustomTabsActivityCallbacks(this);
    flutterWebViewFactory = new FlutterWebViewFactory(this);
    platformViewRegistry.registerViewFactory(
            FlutterWebViewFactory.VIEW_TYPE_ID, flutterWebViewFactory);

    platformUtil = new PlatformUtil(this);
    inAppWebViewManager = new InAppWebViewManager(this);
    myCookieManager = new MyCookieManager(this);
    myWebStorage = new MyWebStorage(this);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      serviceWorkerManager = new ServiceWorkerManager(this);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      credentialDatabaseHandler = new CredentialDatabaseHandler(this);
    }
    webViewFeatureManager = new WebViewFeatureManager(this);
    proxyManager = new ProxyManager(this);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      printJobManager = new PrintJobManager(this);
    }
    tracingControllerManager = new TracingControllerManager(this);
    processGlobalConfigManager = new ProcessGlobalConfigManager(this);

    // AdFit MethodChannel 초기화
    adfitChannel = new MethodChannel(messenger, ADFIT_CHANNEL);
    adfitChannel.setMethodCallHandler(this::handleAdfitMethodCall);

    // WebView 해제 리스너 등록 (한 번만)
    if (!listenerRegistered) {
      InAppWebViewRegistry.addUnregisterListener(unregisterListener);
      listenerRegistered = true;
    }
  }

  /**
   * AdFit MethodChannel 핸들러
   *
   * app_platform에서 호출:
   * - registerAdFit(webViewId) → 특정 WebView에 AdFit 등록
   * - registerAdFitToAllWebViews → 모든 WebView에 AdFit 등록
   * - unregisterAdFit(webViewId) → 등록 해제
   */
  private void handleAdfitMethodCall(MethodCall call, MethodChannel.Result result) {
    Log.d(LOG_TAG, "[AdFit] 📥 MethodChannel called: " + call.method + ", args: " + call.arguments);

    switch (call.method) {
      case "registerAdFit":
        Object webViewId = call.argument("webViewId");
        Log.d(LOG_TAG, "[AdFit] registerAdFit called with webViewId: " + webViewId + " (type: " + (webViewId != null ? webViewId.getClass().getSimpleName() : "null") + ")");
        if (webViewId != null) {
          boolean success = registerAdFitForWebView(webViewId);
          Log.d(LOG_TAG, "[AdFit] registerAdFit result: " + success);
          result.success(success);
        } else {
          Log.e(LOG_TAG, "[AdFit] ❌ registerAdFit failed: webViewId is null");
          result.error("INVALID_ARGUMENT", "webViewId is required", null);
        }
        break;

      case "registerAdFitToAllWebViews":
        int count = registerAdFitToAllWebViews();
        result.success(count);
        break;

      case "unregisterAdFit":
        Object unregisterWebViewId = call.argument("webViewId");
        if (unregisterWebViewId != null) {
          unregisterAdFitForWebView(unregisterWebViewId);
          result.success(true);
        } else {
          result.success(false);
        }
        break;

      case "getRegisteredCount":
        result.success(InAppWebViewRegistry.size());
        break;

      default:
        result.notImplemented();
        break;
    }
  }

  // 이미 등록된 WebView ID 추적 (중복 등록 방지)
  private static final java.util.Set<Object> registeredWebViewIds = java.util.concurrent.ConcurrentHashMap.newKeySet();

  // WebView 해제 시 registeredWebViewIds 자동 정리를 위한 리스너
  private static final InAppWebViewRegistry.UnregisterListener unregisterListener = new InAppWebViewRegistry.UnregisterListener() {
    @Override
    public void onWebViewUnregistered(Object id) {
      if (registeredWebViewIds.remove(id)) {
        Log.d(LOG_TAG, "[AdFit] 🧹 Auto-cleaned WebView from registeredWebViewIds: " + id);
      }
    }
  };

  // 리스너 등록 여부 추적
  private static boolean listenerRegistered = false;

  // AdFit 리플렉션 캐싱 (성능 최적화)
  private static volatile Class<?> cachedAdfitClass;
  private static volatile java.lang.reflect.Method cachedRegisterMethod;
  private static volatile boolean adfitInitialized = false;
  private static volatile boolean adfitAvailable = false;

  /**
   * AdFit 리플렉션 초기화 (한 번만 실행)
   */
  private static synchronized void initAdfitReflection() {
    if (adfitInitialized) return;
    adfitInitialized = true;
    try {
      cachedAdfitClass = Class.forName("com.kakao.adfit.AdFitSdk");
      cachedRegisterMethod = cachedAdfitClass.getMethod("register", WebView.class);
      adfitAvailable = true;
      Log.d(LOG_TAG, "[AdFit] SDK reflection initialized successfully");
    } catch (ClassNotFoundException e) {
      Log.w(LOG_TAG, "[AdFit] AdFitSdk class not found - SDK not integrated");
      adfitAvailable = false;
    } catch (NoSuchMethodException e) {
      Log.e(LOG_TAG, "[AdFit] AdFitSdk.register method not found", e);
      adfitAvailable = false;
    }
  }

  /**
   * WebView에 AdFit SDK 자동 등록 (InAppWebView.prepare()에서 호출)
   *
   * 이 메서드는 WebView가 생성되자마자 URL 로드 전에 호출됩니다.
   * JavaScript Interface가 URL 로드 전에 주입되어야 하이브리드 광고가 동작합니다.
   *
   * @param webViewId WebView의 고유 ID
   * @param webView WebView 인스턴스
   * @return 성공 여부
   */
  public static boolean registerAdFitForWebViewAuto(Object webViewId, WebView webView) {
    Log.d(LOG_TAG, "[AdFit] 🚀 AUTO-REGISTER called from prepare() - webViewId: " + webViewId);

    if (webView == null) {
      Log.w(LOG_TAG, "[AdFit] ⚠️ AUTO-REGISTER: WebView is null");
      return false;
    }

    // 이미 등록된 경우 스킵
    if (registeredWebViewIds.contains(webViewId)) {
      Log.d(LOG_TAG, "[AdFit] AUTO-REGISTER: Already registered, skipping: " + webViewId);
      return true;
    }

    // 리플렉션 초기화
    initAdfitReflection();

    if (!adfitAvailable) {
      Log.w(LOG_TAG, "[AdFit] AUTO-REGISTER: AdFit SDK not available");
      return false;
    }

    try {
      Log.d(LOG_TAG, "[AdFit] 🚀 AUTO-REGISTER: Calling AdFitSdk.register() BEFORE any URL load...");
      Log.d(LOG_TAG, "[AdFit] 🚀 AUTO-REGISTER: WebView URL (should be null): " + webView.getUrl());
      cachedRegisterMethod.invoke(null, webView);
      registeredWebViewIds.add(webViewId);
      Log.d(LOG_TAG, "[AdFit] ✅ AUTO-REGISTER SUCCESS: " + webViewId);
      return true;
    } catch (Exception e) {
      Log.e(LOG_TAG, "[AdFit] ❌ AUTO-REGISTER FAILED: " + webViewId, e);
      return false;
    }
  }

  /**
   * 특정 WebView에 AdFit SDK 등록
   */
  private boolean registerAdFitForWebView(Object webViewId) {
    Log.d(LOG_TAG, "[AdFit] 🔍 registerAdFitForWebView START - webViewId: " + webViewId);
    Log.d(LOG_TAG, "[AdFit] 🔍 Registry size: " + InAppWebViewRegistry.size());
    Log.d(LOG_TAG, "[AdFit] 🔍 Registry contains this ID: " + InAppWebViewRegistry.contains(webViewId));
    Log.d(LOG_TAG, "[AdFit] 🔍 All registered IDs: " + InAppWebViewRegistry.getAllWebViewIds());

    if (registeredWebViewIds.contains(webViewId)) {
      Log.d(LOG_TAG, "[AdFit] WebView already registered: " + webViewId);
      return true;
    }

    WebView webView = InAppWebViewRegistry.getNativeWebViewById(webViewId);
    Log.d(LOG_TAG, "[AdFit] 🔍 WebView lookup result: " + (webView != null ? "FOUND (" + webView.hashCode() + ")" : "NOT FOUND"));
    return registerAdFitForWebViewInternal(webViewId, webView);
  }

  /**
   * WebView에 AdFit SDK 등록 (내부 메서드 - 캐싱된 리플렉션 사용)
   */
  private boolean registerAdFitForWebViewInternal(Object webViewId, WebView webView) {
    Log.d(LOG_TAG, "[AdFit] 🔧 registerAdFitForWebViewInternal START - webViewId: " + webViewId);

    if (webView == null) {
      Log.w(LOG_TAG, "[AdFit] ⚠️ WebView not found in registry: " + webViewId);
      return false;
    }

    Log.d(LOG_TAG, "[AdFit] 🔧 WebView instance: " + webView.getClass().getName() + "@" + Integer.toHexString(webView.hashCode()));
    Log.d(LOG_TAG, "[AdFit] 🔧 WebView URL: " + webView.getUrl());
    Log.d(LOG_TAG, "[AdFit] 🔧 WebView isAttachedToWindow: " + webView.isAttachedToWindow());

    // 리플렉션 초기화 (한 번만 실행)
    initAdfitReflection();
    Log.d(LOG_TAG, "[AdFit] 🔧 AdFit SDK available: " + adfitAvailable);

    if (!adfitAvailable) {
      Log.w(LOG_TAG, "[AdFit] ⚠️ AdFit SDK not available, cannot register");
      return false;
    }

    try {
      Log.d(LOG_TAG, "[AdFit] 🔧 Calling AdFitSdk.register() via reflection...");
      // 캐싱된 리플렉션 사용
      cachedRegisterMethod.invoke(null, webView);
      registeredWebViewIds.add(webViewId);
      Log.d(LOG_TAG, "[AdFit] ✅ Successfully registered WebView: " + webViewId);
      Log.d(LOG_TAG, "[AdFit] ✅ Total registered count: " + registeredWebViewIds.size());
      return true;
    } catch (Exception e) {
      Log.e(LOG_TAG, "[AdFit] ❌ Failed to register WebView: " + webViewId, e);
      Log.e(LOG_TAG, "[AdFit] ❌ Exception type: " + e.getClass().getName());
      Log.e(LOG_TAG, "[AdFit] ❌ Exception message: " + e.getMessage());
      if (e.getCause() != null) {
        Log.e(LOG_TAG, "[AdFit] ❌ Cause: " + e.getCause().getMessage());
      }
      return false;
    }
  }

  /**
   * 모든 WebView에 AdFit SDK 등록
   * 최적화: 스냅샷 생성 및 이중 조회 제거
   */
  private int registerAdFitToAllWebViews() {
    // 리플렉션 초기화 (한 번만 실행)
    initAdfitReflection();
    if (!adfitAvailable) {
      Log.w(LOG_TAG, "[AdFit] SDK not available, skipping registration");
      return 0;
    }

    int count = 0;
    // 스냅샷을 사용하여 순회 중 수정 문제 방지 및 이중 조회 제거
    for (java.util.Map.Entry<Object, WebView> entry : InAppWebViewRegistry.getAllEntries()) {
      Object id = entry.getKey();
      if (!registeredWebViewIds.contains(id)) {
        // WebView를 직접 사용 (이중 조회 제거)
        if (registerAdFitForWebViewInternal(id, entry.getValue())) {
          count++;
        }
      }
    }
    Log.d(LOG_TAG, "[AdFit] 📊 Registered " + count + " new WebViews (total: " + registeredWebViewIds.size() + ")");
    return count;
  }

  /**
   * WebView 등록 해제
   */
  private void unregisterAdFitForWebView(Object webViewId) {
    if (registeredWebViewIds.remove(webViewId)) {
      Log.d(LOG_TAG, "[AdFit] 🗑️ Unregistered WebView: " + webViewId);
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    // WebView 해제 리스너 제거
    if (listenerRegistered) {
      InAppWebViewRegistry.removeUnregisterListener(unregisterListener);
      listenerRegistered = false;
    }

    // AdFit MethodChannel 해제
    if (adfitChannel != null) {
      adfitChannel.setMethodCallHandler(null);
      adfitChannel = null;
    }

    if (platformUtil != null) {
      platformUtil.dispose();
      platformUtil = null;
    }
    if (inAppBrowserManager != null) {
      inAppBrowserManager.dispose();
      inAppBrowserManager = null;
    }
    if (headlessInAppWebViewManager != null) {
      headlessInAppWebViewManager.dispose();
      headlessInAppWebViewManager = null;
    }
    if (chromeSafariBrowserManager != null) {
      chromeSafariBrowserManager.dispose();
      chromeSafariBrowserManager = null;
    }
    if (noHistoryCustomTabsActivityCallbacks != null) {
      noHistoryCustomTabsActivityCallbacks.dispose();
      noHistoryCustomTabsActivityCallbacks = null;
    }
    if (myCookieManager != null) {
      myCookieManager.dispose();
      myCookieManager = null;
    }
    if (myWebStorage != null) {
      myWebStorage.dispose();
      myWebStorage = null;
    }
    if (credentialDatabaseHandler != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      credentialDatabaseHandler.dispose();
      credentialDatabaseHandler = null;
    }
    if (inAppWebViewManager != null) {
      inAppWebViewManager.dispose();
      inAppWebViewManager = null;
    }
    if (serviceWorkerManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      serviceWorkerManager.dispose();
      serviceWorkerManager = null;
    }
    if (webViewFeatureManager != null) {
      webViewFeatureManager.dispose();
      webViewFeatureManager = null;
    }
    if (proxyManager != null) {
      proxyManager.dispose();
      proxyManager = null;
    }
    if (printJobManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      printJobManager.dispose();
      printJobManager = null;
    }
    if (tracingControllerManager != null) {
      tracingControllerManager.dispose();
      tracingControllerManager = null;
    }
    if (processGlobalConfigManager != null) {
      processGlobalConfigManager.dispose();
      processGlobalConfigManager = null;
    }
  }

  @Override
  public void onAttachedToActivity(ActivityPluginBinding activityPluginBinding) {
    this.activityPluginBinding = activityPluginBinding;
    this.activity = activityPluginBinding.getActivity();

    if (noHistoryCustomTabsActivityCallbacks != null) {
      this.activity.getApplication().registerActivityLifecycleCallbacks(noHistoryCustomTabsActivityCallbacks.activityLifecycleCallbacks);
    }
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    if (activity != null && noHistoryCustomTabsActivityCallbacks != null) {
      this.activity.getApplication().unregisterActivityLifecycleCallbacks(noHistoryCustomTabsActivityCallbacks.activityLifecycleCallbacks);
    }

    activityPluginBinding = null;
    activity = null;
  }

  @Override
  public void onReattachedToActivityForConfigChanges(ActivityPluginBinding activityPluginBinding) {
    this.activityPluginBinding = activityPluginBinding;
    this.activity = activityPluginBinding.getActivity();

    if (noHistoryCustomTabsActivityCallbacks != null) {
      this.activity.getApplication().registerActivityLifecycleCallbacks(noHistoryCustomTabsActivityCallbacks.activityLifecycleCallbacks);
    }
  }

  @Override
  public void onDetachedFromActivity() {
    if (activity != null && noHistoryCustomTabsActivityCallbacks != null) {
      this.activity.getApplication().unregisterActivityLifecycleCallbacks(noHistoryCustomTabsActivityCallbacks.activityLifecycleCallbacks);
    }

    activityPluginBinding = null;
    activity = null;
  }
}
