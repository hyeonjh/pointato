package com.pointato;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    private WebView webView;
    private LinearLayout resultContainer;
    private TextView summaryText;
    private Button btnNaverReview;
    private Button btnToggleWebView;
    private Button btnCopyResult;

    private final Handler handler = new Handler(Looper.getMainLooper());

    //private static final String NAVER_ORDER_URL = "https://shopping.naver.com/my/order";
    private static final String API_BASE_URL = "https://pointato.hyeonjihun.com";
    private static final String DEFAULT_NAVER_ORDER_URL = "https://shopping.naver.com/my/order";

    private SiteConfig naverConfig = SiteConfig.defaultNaver();


    private final List<OrderItem> reviewItems = new ArrayList<>();
    private final List<OrderItem> collectedReviewItems = new ArrayList<>();

    private String lastResultText = "";

    private int collectedTotalCount = 0;
    private int scannedPageCount = 0;
    private boolean waitingFirstPageLoad = false;

    static class OrderItem {
        int cardIndex;
        int pageNo;
        int rewardAmount;

        String status;
        String delivery;
        String date;
        String name;
        String price;
        String productUrl;
        String detailUrl;
        boolean reviewAvailable;
        String reviewType;
        String reviewReward;
    }

    static class SiteConfig {
        String site;
        String displayName;
        String orderPageUrl;
        int maxPage;

        static SiteConfig defaultNaver() {
            SiteConfig config = new SiteConfig();
            config.site = "naver";
            config.displayName = "네이버";
            config.orderPageUrl = DEFAULT_NAVER_ORDER_URL;
            config.maxPage = 2;
            return config;
        }

        static SiteConfig fromJson(JSONObject obj) {
            SiteConfig config = new SiteConfig();
            config.site = obj.optString("site", "naver");
            config.displayName = obj.optString("displayName", "네이버");
            config.orderPageUrl = obj.optString("orderPageUrl", DEFAULT_NAVER_ORDER_URL);
            config.maxPage = obj.optInt("maxPage", 2);
            return config;
        }
    }

    interface PageMoveCallback {
        void onDone(String result);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupLayout();
        setupWebView();
        setupEvents();
        fetchNaverSiteConfig();
    }

    private void setupLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 32, 28, 24);
        root.setBackgroundColor(0xFFF8F8F8);

        TextView title = new TextView(this);
        title.setText("Pointato");
        title.setTextSize(28);
        title.setTextColor(0xFF222222);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, 0, 0, 8);

        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView subtitle = new TextView(this);
        subtitle.setText("놓친 리뷰 적립금을 한 번에 확인해보세요.");
        subtitle.setTextSize(14);
        subtitle.setTextColor(0xFF666666);
        subtitle.setPadding(0, 0, 0, 24);
        root.addView(subtitle);

        btnNaverReview = new Button(this);
        btnNaverReview.setText("네이버 리뷰 가능 상품 조회");
        root.addView(btnNaverReview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        summaryText = new TextView(this);
        summaryText.setText("조회 버튼을 누르면 네이버 주문목록에서 리뷰 가능 상품을 찾아요.");
        summaryText.setTextSize(15);
        summaryText.setTextColor(0xFF333333);
        summaryText.setPadding(0, 24, 0, 16);
        root.addView(summaryText);

        LinearLayout smallButtonRow = new LinearLayout(this);
        smallButtonRow.setOrientation(LinearLayout.HORIZONTAL);

        btnCopyResult = new Button(this);
        btnCopyResult.setText("결과 복사");

        btnToggleWebView = new Button(this);
        btnToggleWebView.setText("WebView 보기");

        smallButtonRow.addView(btnCopyResult, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        smallButtonRow.addView(btnToggleWebView, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        root.addView(smallButtonRow);

        ScrollView scrollView = new ScrollView(this);

        resultContainer = new LinearLayout(this);
        resultContainer.setOrientation(LinearLayout.VERTICAL);
        resultContainer.setPadding(0, 16, 0, 16);

        scrollView.addView(resultContainer);

        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1
        ));

        webView = new WebView(this);
        webView.setVisibility(View.GONE);

        root.addView(webView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                520
        ));

        setContentView(root);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (url.contains("nid.naver.com")) {
                    waitingFirstPageLoad = false;

                    webView.setVisibility(View.VISIBLE);
                    btnToggleWebView.setText("WebView 숨기기");

                    summaryText.setText("네이버 로그인이 필요합니다. WebView에서 로그인 후 다시 조회해주세요.");
                    return;
                }

                if (waitingFirstPageLoad && url.contains("shopping.naver.com/my/order")) {
                    waitingFirstPageLoad = false;

                    summaryText.setText("네이버 주문목록 1페이지를 분석 중...");

                    handler.postDelayed(() -> collectNaverOrdersPage(1), 2500);
                }
            }
        });
    }

    private void setupEvents() {
        btnNaverReview.setOnClickListener(v -> {
            resultContainer.removeAllViews();

            reviewItems.clear();
            collectedReviewItems.clear();

            collectedTotalCount = 0;
            scannedPageCount = 0;
            lastResultText = "";

            summaryText.setText("네이버 주문목록으로 이동 중...");

            webView.setVisibility(View.GONE);
            btnToggleWebView.setText("WebView 보기");

            waitingFirstPageLoad = true;
            webView.loadUrl(getNaverOrderUrl());
        });

        btnToggleWebView.setOnClickListener(v -> {
            if (webView.getVisibility() == View.VISIBLE) {
                webView.setVisibility(View.GONE);
                btnToggleWebView.setText("WebView 보기");
            } else {
                webView.setVisibility(View.VISIBLE);
                btnToggleWebView.setText("WebView 숨기기");
            }
        });

        btnCopyResult.setOnClickListener(v -> {
            if (lastResultText == null || lastResultText.trim().isEmpty()) {
                Toast.makeText(this, "복사할 결과가 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            ClipboardManager clipboard =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

            ClipData clip = ClipData.newPlainText("Pointato Result", lastResultText);
            clipboard.setPrimaryClip(clip);

            Toast.makeText(this, "결과를 복사했습니다.", Toast.LENGTH_SHORT).show();
        });
    }

    private void fetchNaverSiteConfig() {
        new Thread(() -> {
            HttpURLConnection conn = null;

            try {
                URL url = new URL(API_BASE_URL + "/api/sites/naver/config");
                conn = (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();

                if (responseCode != 200) {
                    throw new Exception("API 응답 오류: " + responseCode);
                }

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream())
                );

                StringBuilder responseBuilder = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    responseBuilder.append(line);
                }

                reader.close();

                JSONObject obj = new JSONObject(responseBuilder.toString());
                SiteConfig config = SiteConfig.fromJson(obj);

                runOnUiThread(() -> {
                    naverConfig = config;

                    Toast.makeText(
                            this,
                            "서버 설정 로드 완료: " + naverConfig.displayName,
                            Toast.LENGTH_SHORT
                    ).show();
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    naverConfig = SiteConfig.defaultNaver();

                    Toast.makeText(
                            this,
                            "서버 설정 로드 실패. 기본 설정 사용",
                            Toast.LENGTH_SHORT
                    ).show();
                });
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }).start();
    }

    private String getNaverOrderUrl() {
        if (naverConfig == null || naverConfig.orderPageUrl == null || naverConfig.orderPageUrl.trim().isEmpty()) {
            return DEFAULT_NAVER_ORDER_URL;
        }

        return naverConfig.orderPageUrl;
    }

    private int getNaverMaxPage() {
        if (naverConfig == null || naverConfig.maxPage <= 0) {
            return 2;
        }

        return naverConfig.maxPage;
    }

    private void collectNaverOrdersPage(int pageNo) {
        summaryText.setText("네이버 주문목록 " + pageNo + "페이지 분석 중...");

        String js =
                "(function() {" +
                        "const cards = Array.from(document.querySelectorAll('[class*=OrderProductBundle_order_card]'));" +
                        "const orders = cards.map((card, index) => {" +
                        "  const status = card.querySelector('strong[class*=OrderProduct_status]')?.innerText.trim() || '';" +
                        "  const delivery = Array.from(card.querySelectorAll('.blind'))" +
                        "    .map(el => el.innerText.trim())" +
                        "    .find(text => text.includes('배송')) || '';" +
                        "  const date = card.querySelector('[class*=OrderProductItem_date]')?.innerText.trim() || '';" +
                        "  const name = card.querySelector('[class*=OrderProductItem_name]')?.innerText.trim() || '';" +
                        "  const price = card.querySelector('strong[class*=OrderProductItem_price]')?.innerText.trim() || '';" +
                        "  const productUrl = card.querySelector('a[class*=OrderProductItem_thumb_area]')?.href || '';" +
                        "  const detailUrl = card.querySelector('a[class*=OrderProductItem_btn_detail]')?.href || '';" +
                        "  const reviewButton =" +
                        "    card.querySelector('[data-shp-contents-id=\"리뷰쓰기\"]') ||" +
                        "    card.querySelector('[data-shp-contents-id=\"한달사용리뷰\"]');" +
                        "  const reviewAvailable = !!reviewButton;" +
                        "  const reviewType = reviewButton?.getAttribute('data-shp-contents-id') || '';" +
                        "  const reviewReward = reviewButton?.querySelector('[class*=UserActionButtons_text]')?.innerText.trim() || '';" +
                        "  return {" +
                        "    cardIndex: index," +
                        "    status," +
                        "    delivery," +
                        "    date," +
                        "    name," +
                        "    price," +
                        "    productUrl," +
                        "    detailUrl," +
                        "    reviewAvailable," +
                        "    reviewType," +
                        "    reviewReward" +
                        "  };" +
                        "});" +
                        "return JSON.stringify(orders);" +
                        "})();";

        webView.evaluateJavascript(js, value -> {
            try {
                String json = cleanEvaluateResult(value);
                JSONArray array = new JSONArray(json);

                scannedPageCount = Math.max(scannedPageCount, pageNo);
                collectedTotalCount += array.length();

                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);

                    boolean reviewAvailable = obj.optBoolean("reviewAvailable", false);

                    if (!reviewAvailable) {
                        continue;
                    }

                    OrderItem item = new OrderItem();
                    item.cardIndex = obj.optInt("cardIndex", i);
                    item.pageNo = pageNo;
                    item.status = obj.optString("status", "");
                    item.delivery = obj.optString("delivery", "");
                    item.date = obj.optString("date", "");
                    item.name = obj.optString("name", "");
                    item.price = obj.optString("price", "");
                    item.productUrl = obj.optString("productUrl", "");
                    item.detailUrl = obj.optString("detailUrl", "");
                    item.reviewAvailable = true;
                    item.reviewType = obj.optString("reviewType", "");
                    item.reviewReward = obj.optString("reviewReward", "");
                    item.rewardAmount = parseRewardNumber(item.reviewReward);

                    collectedReviewItems.add(item);
                }

                if (pageNo < getNaverMaxPage()) {
                    int nextPage = pageNo + 1;

                    summaryText.setText(pageNo + "페이지 분석 완료. " + nextPage + "페이지로 이동 중...");

                    moveToNaverPageNumber(nextPage, result -> {
                        if ("CLICKED".equals(result)) {
                            handler.postDelayed(() -> collectNaverOrdersPage(nextPage), 3500);
                        } else {
                            renderCollectedResult();
                        }
                    });
                } else {
                    renderCollectedResult();
                }

            } catch (Exception e) {
                summaryText.setText("파싱 중 오류가 발생했습니다.\n" + e.getMessage());
                Toast.makeText(this, "파싱 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void moveToNaverPageNumber(int pageNo, PageMoveCallback callback) {
        if (pageNo <= 1) {
            callback.onDone("PAGE_1");
            return;
        }

        String js =
                "(function() {" +
                        "const target = '" + pageNo + "';" +
                        "const elements = Array.from(document.querySelectorAll('a, button'));" +
                        "const pageButton = elements.find(el => {" +
                        "  const text = (el.innerText || el.textContent || '').trim();" +
                        "  const rect = el.getBoundingClientRect();" +
                        "  return text === target && rect.width > 0 && rect.height > 0;" +
                        "});" +
                        "if (!pageButton) return 'PAGE_' + target + '_NOT_FOUND';" +
                        "pageButton.scrollIntoView({behavior: 'smooth', block: 'center'});" +
                        "pageButton.click();" +
                        "return 'CLICKED';" +
                        "})();";

        webView.evaluateJavascript(js, value -> {
            String result = cleanEvaluateResult(value);
            callback.onDone(result);
        });
    }

    private void renderCollectedResult() {
        resultContainer.removeAllViews();

        List<OrderItem> uniqueItems = getUniqueSortedReviewItems();

        reviewItems.clear();
        reviewItems.addAll(uniqueItems);

        int totalReward = 0;

        int normalReviewCount = 0;
        int normalReviewReward = 0;

        int monthReviewCount = 0;
        int monthReviewReward = 0;

        for (OrderItem item : uniqueItems) {
            totalReward += item.rewardAmount;

            if (item.reviewType != null && item.reviewType.contains("한달")) {
                monthReviewCount++;
                monthReviewReward += item.rewardAmount;
            } else {
                normalReviewCount++;
                normalReviewReward += item.rewardAmount;
            }
        }

        String summary =
                "네이버 리뷰 적립금 조회 결과\n" +
                        "수집 페이지: " + scannedPageCount + "페이지\n" +
                        "전체 상품: " + collectedTotalCount + "개\n" +
                        "리뷰 가능: " + uniqueItems.size() + "개\n" +
                        "예상 적립 합계: 최대 " + totalReward + "원\n\n" +
                        "일반 리뷰: " + normalReviewCount + "개 / 최대 " + normalReviewReward + "원\n" +
                        "한달사용리뷰: " + monthReviewCount + "개 / 최대 " + monthReviewReward + "원\n" +
                        "정렬: 예상 적립금 높은 순";

        summaryText.setText(summary);

        StringBuilder copyBuilder = new StringBuilder();
        copyBuilder.append(summary).append("\n\n");

        if (uniqueItems.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("현재 리뷰 가능한 상품이 없습니다.");
            empty.setTextSize(16);
            empty.setTextColor(0xFF555555);
            empty.setPadding(0, 24, 0, 24);

            resultContainer.addView(empty);

            lastResultText = summary;
            return;
        }

        for (int i = 0; i < uniqueItems.size(); i++) {
            OrderItem item = uniqueItems.get(i);

            addOrderCard(i + 1, item);

            copyBuilder
                    .append(i + 1).append(". ")
                    .append(item.name).append("\n")
                    .append("- 페이지: ").append(item.pageNo).append("페이지\n")
                    .append("- 상태: ").append(item.status).append("\n")
                    .append("- 배송: ").append(item.delivery).append("\n")
                    .append("- 주문일: ").append(item.date).append("\n")
                    .append("- 가격: ").append(item.price).append("\n")
                    .append("- 리뷰 유형: ").append(item.reviewType).append("\n")
                    .append("- 예상 적립금: ").append(item.reviewReward).append("\n")
                    .append("- 정렬 기준 금액: ").append(item.rewardAmount).append("원\n")
                    .append("- 상세 URL: ").append(item.detailUrl).append("\n\n");
        }

        lastResultText = copyBuilder.toString();
    }

    private List<OrderItem> getUniqueSortedReviewItems() {
        List<OrderItem> result = new ArrayList<>();
        Set<String> keys = new HashSet<>();

        for (OrderItem item : collectedReviewItems) {
            String key = makeUniqueKey(item);

            if (keys.contains(key)) {
                continue;
            }

            keys.add(key);
            result.add(item);
        }

        Collections.sort(result, new Comparator<OrderItem>() {
            @Override
            public int compare(OrderItem a, OrderItem b) {
                return b.rewardAmount - a.rewardAmount;
            }
        });

        return result;
    }

    private String makeUniqueKey(OrderItem item) {
        if (item.detailUrl != null && !item.detailUrl.trim().isEmpty()) {
            return item.detailUrl.trim();
        }

        return safeText(item.name) + "|" +
                safeText(item.date) + "|" +
                safeText(item.price) + "|" +
                safeText(item.reviewType);
    }

    private void addOrderCard(int displayIndex, OrderItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(28, 24, 28, 24);
        card.setBackgroundColor(0xFFFFFFFF);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );

        cardParams.setMargins(0, 0, 0, 22);

        TextView name = new TextView(this);
        name.setText(displayIndex + ". " + item.name);
        name.setTextSize(17);
        name.setTextColor(0xFF222222);
        name.setPadding(0, 0, 0, 14);
        card.addView(name);

        TextView info = new TextView(this);
        info.setText(
                "페이지: " + item.pageNo + "페이지\n" +
                        "상태: " + safeText(item.status) + "\n" +
                        "배송: " + safeText(item.delivery) + "\n" +
                        "주문일: " + safeText(item.date) + "\n" +
                        "가격: " + safeText(item.price) + "\n" +
                        "리뷰 유형: " + safeText(item.reviewType) + "\n" +
                        "예상 적립금: " + safeText(item.reviewReward) + "\n" +
                        "정렬 기준 금액: " + item.rewardAmount + "원"
        );
        info.setTextSize(14);
        info.setTextColor(0xFF555555);
        info.setPadding(0, 0, 0, 18);
        card.addView(info);

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);

        Button detailButton = new Button(this);
        detailButton.setText("상세보기");

        Button reviewButton = new Button(this);
        reviewButton.setText("리뷰쓰기 이동");

        buttonRow.addView(detailButton, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        buttonRow.addView(reviewButton, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1
        ));

        card.addView(buttonRow);

        detailButton.setOnClickListener(v -> {
            if (item.detailUrl == null || item.detailUrl.trim().isEmpty()) {
                Toast.makeText(this, "상세 URL이 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            webView.setVisibility(View.VISIBLE);
            btnToggleWebView.setText("WebView 숨기기");

            webView.loadUrl(item.detailUrl);
        });

        reviewButton.setOnClickListener(v -> {
            webView.setVisibility(View.VISIBLE);
            btnToggleWebView.setText("WebView 숨기기");

            summaryText.setText("리뷰쓰기 버튼을 찾기 위해 주문목록으로 이동 중...");

            moveToOrderPageAndClickReview(item);
        });

        resultContainer.addView(card, cardParams);
    }

    private void moveToOrderPageAndClickReview(OrderItem item) {
        webView.loadUrl(getNaverOrderUrl());

        handler.postDelayed(() -> {
            if (item.pageNo <= 1) {
                clickReviewButtonByItem(item);
                return;
            }

            summaryText.setText(item.pageNo + "페이지로 이동해서 리뷰쓰기 버튼을 찾는 중...");

            moveToNaverPageNumber(item.pageNo, result -> {
                if ("CLICKED".equals(result)) {
                    handler.postDelayed(() -> clickReviewButtonByItem(item), 3000);
                } else {
                    summaryText.setText("리뷰쓰기 이동 실패: " + item.pageNo + "페이지 이동 실패");
                    Toast.makeText(this, "페이지 이동 실패: " + result, Toast.LENGTH_SHORT).show();
                }
            });
        }, 3000);
    }

    private void clickReviewButtonByItem(OrderItem item) {
        String safeName = escapeJsString(item.name);
        String safeDetailUrl = escapeJsString(item.detailUrl);

        String js =
                "(function() {" +
                        "const targetName = '" + safeName + "';" +
                        "const targetDetailUrl = '" + safeDetailUrl + "';" +

                        "const cards = Array.from(document.querySelectorAll('[class*=OrderProductBundle_order_card]'));" +
                        "if (!cards.length) return 'NO_ORDER_CARDS';" +

                        "let targetCard = cards.find(card => {" +
                        "  const name = card.querySelector('[class*=OrderProductItem_name]')?.innerText.trim() || '';" +
                        "  const detailUrl = card.querySelector('a[class*=OrderProductItem_btn_detail]')?.href || '';" +
                        "  return detailUrl === targetDetailUrl || name === targetName || name.includes(targetName) || targetName.includes(name);" +
                        "});" +

                        "if (!targetCard) return 'TARGET_CARD_NOT_FOUND';" +

                        "const btn =" +
                        "  targetCard.querySelector('[data-shp-contents-id=\"리뷰쓰기\"]') ||" +
                        "  targetCard.querySelector('[data-shp-contents-id=\"한달사용리뷰\"]');" +

                        "if (!btn) return 'REVIEW_BUTTON_NOT_FOUND';" +

                        "btn.scrollIntoView({behavior: 'smooth', block: 'center'});" +
                        "btn.click();" +

                        "return 'CLICKED';" +
                        "})();";

        webView.evaluateJavascript(js, value -> {
            String result = cleanEvaluateResult(value);

            if ("CLICKED".equals(result)) {
                summaryText.setText("리뷰쓰기 화면으로 이동 중입니다.");
                Toast.makeText(this, "리뷰쓰기 버튼을 눌렀습니다.", Toast.LENGTH_SHORT).show();
            } else {
                summaryText.setText("리뷰쓰기 이동 실패: " + result);
                Toast.makeText(this, "리뷰쓰기 이동 실패: " + result, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String cleanEvaluateResult(String value) {
        if (value == null) {
            return "";
        }

        String result = value;

        if (result.startsWith("\"") && result.endsWith("\"")) {
            result = result.substring(1, result.length() - 1);
        }

        result = result
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\t", "\t");

        return result;
    }

    private String escapeJsString(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private int parseRewardNumber(String rewardText) {
        if (rewardText == null) {
            return 0;
        }

        try {
            String onlyNumber = rewardText.replaceAll("[^0-9]", "");

            if (onlyNumber.isEmpty()) {
                return 0;
            }

            return Integer.parseInt(onlyNumber);
        } catch (Exception e) {
            return 0;
        }
    }

    private String safeText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "-";
        }

        return text;
    }
}