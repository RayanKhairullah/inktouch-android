package com.example.inktouchapp_android.supabase;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.inktouchapp_android.supabase.api.SupabaseService;
import com.example.inktouchapp_android.product.IceCreamProduct;
import com.example.inktouchapp_android.product.Transaction;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SupabaseHelper {
    private static final String TAG = "SupabaseHelper";
    private static final String API_URL = "https://xopzqcshmfvfaxtgpxnf.supabase.co";
    private static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhvcHpxY3NobWZ2ZmF4dGdweG5mIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTkxOTEyNjEsImV4cCI6MjA3NDc2NzI2MX0.b9fJ5s1Qe0L8uTQJ88Qz857Sr1bNyyL8P8nn1JuzKKU";
    private static final String STORAGE_BUCKET = "product-images";

    private Retrofit retrofit;
    private SupabaseService service;
    private String sessionToken;
    private String userId;
    private String userEmail;
    private String refreshToken;
    private long expiresAtMillis = 0L;
    private Context appContext; // for SharedPreferences persistence

    private static final String PREF_NAME = "supabase_session";
    private static final String KEY_ACCESS = "access_token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_EXPIRES_AT = "expires_at";

    private static SupabaseHelper instance;

    private SupabaseHelper() {
        // Setup OkHttpClient dengan interceptor
        OkHttpClient.Builder httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);

        // Tambahkan logging interceptor
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BODY);
        httpClient.addInterceptor(logging);

        // Setup Retrofit
        retrofit = new Retrofit.Builder()
                .baseUrl(API_URL + "/")  // PERBAIKAN: Pastikan ada trailing slash
                .addConverterFactory(GsonConverterFactory.create())
                .client(httpClient.build())
                .build();

        service = retrofit.create(SupabaseService.class);
    }

    public static synchronized SupabaseHelper getInstance() {
        if (instance == null) {
            instance = new SupabaseHelper();
        }
        return instance;
    }

    // Initialize with application context for persistence
    public void init(Context context) {
        this.appContext = context != null ? context.getApplicationContext() : null;
    }

    // Autentikasi
    public void signIn(String email, String password, AuthCallback callback) {
        SupabaseService.AuthRequest authRequest = new SupabaseService.AuthRequest(email, password);

        Call<SupabaseService.AuthResponse> call = service.signIn(ANON_KEY, authRequest);
        call.enqueue(new Callback<SupabaseService.AuthResponse>() {
            @Override
            public void onResponse(Call<SupabaseService.AuthResponse> call, Response<SupabaseService.AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    SupabaseService.AuthResponse body = response.body();
                    sessionToken = "Bearer " + body.getAccessToken();
                    userId = body.getUser().getId();
                    userEmail = body.getUser().getEmail();
                    refreshToken = body.getRefreshToken();
                    // add small buffer of 60s
                    expiresAtMillis = System.currentTimeMillis() + (body.getExpiresIn() * 1000L) - 60_000L;
                    // persist
                    persistSessionInternal(userEmail, body.getAccessToken(), refreshToken, userId, expiresAtMillis);
                    callback.onSuccess();
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        Log.e("SupabaseHelper", "Login error response: " + errorBody);
                        callback.onError("Login failed: " + errorBody);
                    } catch (IOException e) {
                        callback.onError("Login failed: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onFailure(Call<SupabaseService.AuthResponse> call, Throwable t) {
                Log.e("SupabaseHelper", "Network error", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    // Upload image bytes to Supabase Storage and return the public URL (for Public bucket)
    public void uploadImageToStorage(byte[] data, String fileName, SimpleCallback callback) {
        if (!isUserSignedIn()) {
            callback.onError("User not signed in");
            return;
        }
        if (data == null || data.length == 0) {
            callback.onError("No data");
            return;
        }
        try {
            String safeFile = fileName != null && !fileName.isEmpty() ? fileName : (System.currentTimeMillis()+".jpg");
            String objectPath = userId + "/" + safeFile; // per-user folder
            RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"), data);
            retrofit2.Call<Void> call = service.uploadObject(
                    ANON_KEY,
                    sessionToken,
                    "image/jpeg",
                    STORAGE_BUCKET,
                    objectPath,
                    body
            );
            call.enqueue(new retrofit2.Callback<Void>() {
                @Override
                public void onResponse(retrofit2.Call<Void> call, retrofit2.Response<Void> response) {
                    if (response.isSuccessful()) {
                        String publicUrl = API_URL + "/storage/v1/object/public/" + STORAGE_BUCKET + "/" + objectPath;
                        callback.onSuccess(publicUrl);
                    } else {
                        try {
                            String err = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                            callback.onError("Upload failed: " + err);
                        } catch (IOException e) {
                            callback.onError("Upload failed: " + e.getMessage());
                        }
                    }
                }

                @Override
                public void onFailure(retrofit2.Call<Void> call, Throwable t) {
                    callback.onError("Network error: " + t.getMessage());
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    // Ambil item transaksi untuk detail dialog
    public void loadTransactionItems(String transactionId, ItemsCallback callback) {
        if (!isUserSignedIn()) {
            callback.onError("User not signed in");
            return;
        }

        // Select dengan embed product: alias "product:products(*)" agar dapat nama
        String select = "id,quantity,price,product:products(id,name,price)";
        String order = "id.asc";

        retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call = service.getTransactionItems(
                ANON_KEY,
                sessionToken,
                "eq." + transactionId,
                select,
                order
        );

        call.enqueue(new retrofit2.Callback<java.util.List<java.util.Map<String, Object>>>() {
            @Override
            public void onResponse(retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call, retrofit2.Response<java.util.List<java.util.Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<String> display = new ArrayList<>();
                    for (Map<String, Object> row : response.body()) {
                        // product object
                        Object productObj = row.get("product");
                        String name = "(unknown)";
                        if (productObj instanceof Map) {
                            Object nameObj = ((Map<?, ?>) productObj).get("name");
                            if (nameObj != null) name = String.valueOf(nameObj);
                        }
                        int qty = 1;
                        Object qObj = row.get("quantity");
                        if (qObj instanceof Number) qty = ((Number) qObj).intValue();
                        double price = 0.0;
                        Object pObj = row.get("price");
                        if (pObj instanceof Number) price = ((Number) pObj).doubleValue();
                        display.add(String.format(Locale.getDefault(), "%s x%d - Rp %.0f", name, qty, price));
                    }
                    callback.onSuccess(display);
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        callback.onError("Failed to load transaction items: " + errorBody);
                    } catch (IOException e) {
                        callback.onError("Failed to load transaction items: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onFailure(retrofit2.Call<java.util.List<java.util.Map<String, Object>>> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    public void signOut(AuthCallback callback) {
        // Clear memory state
        sessionToken = null;
        userId = null;
        refreshToken = null;
        expiresAtMillis = 0L;
        // Clear persisted state using appContext if available
        try {
            Context ctx = appContext;
            if (ctx != null) {
                SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                sp.edit().clear().apply();
            }
        } catch (Exception ignored) {}
        callback.onSuccess();
    }

    // Public helper to clear persisted session
    public void clearSession(Context ctx) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            sp.edit().clear().apply();
        } catch (Exception ignored) {}
        sessionToken = null;
        userId = null;
        refreshToken = null;
        expiresAtMillis = 0L;
    }

    public boolean isUserSignedIn() {
        return sessionToken != null && userId != null;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getCurrentUserId() {
        return userId;
    }

    // Initialize or restore session. Attempts persisted session, refresh if expired.
    public void initializeSession(Context ctx, SessionInitCallback callback) {
        if (isUserSignedIn()) {
            callback.onReady();
            return;
        }
        boolean loaded = loadPersistedSession(ctx);
        if (!loaded) {
            callback.onRequireLogin();
            return;
        }
        // If token still valid, ready
        if (System.currentTimeMillis() < expiresAtMillis && sessionToken != null) {
            callback.onReady();
            return;
        }
        // Try refresh
        if (refreshToken == null || refreshToken.isEmpty()) {
            callback.onRequireLogin();
            return;
        }
        SupabaseService.RefreshRequest req = new SupabaseService.RefreshRequest(refreshToken);
        Call<SupabaseService.AuthResponse> call = service.refreshToken(ANON_KEY, req);
        call.enqueue(new Callback<SupabaseService.AuthResponse>() {
            @Override
            public void onResponse(Call<SupabaseService.AuthResponse> call, Response<SupabaseService.AuthResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    SupabaseService.AuthResponse body = response.body();
                    sessionToken = "Bearer " + body.getAccessToken();
                    // userId should remain same; some responses may include user
                    if (body.getUser() != null && body.getUser().getId() != null) {
                        userId = body.getUser().getId();
                    }
                    if (body.getUser() != null && body.getUser().getEmail() != null) {
                        userEmail = body.getUser().getEmail();
                    }
                    refreshToken = body.getRefreshToken() != null ? body.getRefreshToken() : refreshToken;
                    expiresAtMillis = System.currentTimeMillis() + (body.getExpiresIn() * 1000L) - 60_000L;
                    persistSessionInternal(userEmail, body.getAccessToken(), refreshToken, userId, expiresAtMillis);
                    callback.onReady();
                } else {
                    clearSession(ctx);
                    callback.onRequireLogin();
                }
            }

            @Override
            public void onFailure(Call<SupabaseService.AuthResponse> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    private void persistSessionInternal(String email, String access, String refresh, String uid, long expMillis) {
        try {
            Context ctx = appContext;
            if (ctx != null) {
                SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                sp.edit()
                        .putString(KEY_ACCESS, access)
                        .putString(KEY_REFRESH, refresh)
                        .putString(KEY_USER_ID, uid)
                        .putString(KEY_USER_EMAIL, email)
                        .putLong(KEY_EXPIRES_AT, expMillis)
                        .apply();
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to persist session: " + e.getMessage());
        }
    }

    private boolean loadPersistedSession(Context ctx) {
        try {
            Context use = ctx != null ? ctx : appContext;
            if (use == null) return false;
            SharedPreferences sp = use.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String access = sp.getString(KEY_ACCESS, null);
            String refresh = sp.getString(KEY_REFRESH, null);
            String uid = sp.getString(KEY_USER_ID, null);
            String email = sp.getString(KEY_USER_EMAIL, null);
            long exp = sp.getLong(KEY_EXPIRES_AT, 0L);
            if (access != null && uid != null) {
                sessionToken = "Bearer " + access;
                userId = uid;
                userEmail = email;
                refreshToken = refresh;
                expiresAtMillis = exp;
                return true;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to load persisted session: " + e.getMessage());
        }
        return false;
    }

    // Session initialization callback for UI flow
    public interface SessionInitCallback {
        void onReady();
        void onRequireLogin();
        void onError(String message);
    }

    // Produk
    public void addProduct(IceCreamProduct product, DatabaseCallback callback) {
        if (!isUserSignedIn()) {
            callback.onError("User not signed in");
            return;
        }

        Map<String, Object> productData = new HashMap<>();
        productData.put("name", product.getName());
        productData.put("price", product.getPrice());
        productData.put("stock", product.getStock());
        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
            productData.put("image_url", product.getImageUrl());
        }
        if (product.getCategoryId() != null && !product.getCategoryId().isEmpty()) {
            productData.put("category_id", product.getCategoryId());
        }
        if (product.getSku() != null && !product.getSku().isEmpty()) {
            productData.put("sku", product.getSku());
        }
        productData.put("user_id", userId);

        Call<Void> call = service.addProduct(ANON_KEY, sessionToken, "return=minimal", productData);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(product.getId());
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        callback.onError("Failed to add product: " + errorBody);
                    } catch (IOException e) {
                        callback.onError("Failed to add product: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    public void updateProduct(IceCreamProduct product, DatabaseCallback callback) {
        if (!isUserSignedIn()) {
            callback.onError("User not signed in");
            return;
        }

        Map<String, Object> productData = new HashMap<>();
        productData.put("name", product.getName());
        productData.put("price", product.getPrice());
        productData.put("stock", product.getStock());
        if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
            productData.put("image_url", product.getImageUrl());
        }
        if (product.getCategoryId() != null && !product.getCategoryId().isEmpty()) {
            productData.put("category_id", product.getCategoryId());
        }
        if (product.getSku() != null && !product.getSku().isEmpty()) {
            productData.put("sku", product.getSku());
        }

        // PERBAIKAN: Gunakan query parameter untuk id
        Call<Void> call = service.updateProduct(ANON_KEY, sessionToken, "return=minimal", "eq." + product.getId(), productData);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(product.getId());
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        callback.onError("Failed to update product: " + errorBody);
                    } catch (IOException e) {
                        callback.onError("Failed to update product: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    public void deleteProduct(String productId, DatabaseCallback callback) {
        if (!isUserSignedIn()) {
            callback.onError("User not signed in");
            return;
        }

        // PERBAIKAN: Gunakan query parameter untuk id
        Call<Void> call = service.deleteProduct(ANON_KEY, sessionToken, "eq." + productId);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(productId);
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        callback.onError("Failed to delete product: " + errorBody);
                    } catch (IOException e) {
                        callback.onError("Failed to delete product: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    public void loadProducts(ProductsCallback callback) {
        if (!isUserSignedIn()) {
            callback.onError("User not signed in");
            return;
        }

        Log.d(TAG, "Loading products for user: " + userId);

        // PERBAIKAN: Tambahkan log untuk parameter query
        Log.d(TAG, "Params: user_id=eq." + userId
                + ", select=id,name,price,stock,image_url,category_id,sku"
                + ", order=id.desc");

        Call<List<IceCreamProduct>> call = service.getProducts(
                ANON_KEY,
                sessionToken,
                "eq." + userId,
                "id,name,price,stock,image_url,category_id,sku",
                "id.desc"
        );

        call.enqueue(new Callback<List<IceCreamProduct>>() {
            @Override
            public void onResponse(Call<List<IceCreamProduct>> call, Response<List<IceCreamProduct>> response) {
                Log.d(TAG, "Products response code: " + response.code());

                if (response.isSuccessful()) {
                    Log.d(TAG, "Response is successful");

                    if (response.body() != null) {
                        Log.d(TAG, "Products count: " + response.body().size());
                        callback.onSuccess(response.body());
                    } else {
                        Log.e(TAG, "Response body is null");
                        callback.onError("Failed to load products: Response body is null");
                    }
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        Log.e(TAG, "Products load failed: " + errorBody);
                        callback.onError("Failed to load products: " + errorBody);
                    } catch (IOException e) {
                        Log.e(TAG, "Error parsing error body", e);
                        callback.onError("Failed to load products: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onFailure(Call<List<IceCreamProduct>> call, Throwable t) {
                Log.e(TAG, "Network error", t);
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    // Transaksi
    public void addTransaction(Transaction transaction, DatabaseCallback callback) {
        if (!isUserSignedIn()) {
            callback.onError("User not signed in");
            return;
        }

        Log.d(TAG, "Current user_id: " + userId);
        
        Map<String, Object> transactionData = new HashMap<>();
        transactionData.put("total", transaction.getTotal());
        transactionData.put("amount_paid", transaction.getAmountPaid());
        transactionData.put("change_amount", transaction.getChange()); // ⚠️ PERBAIKAN: change_amount bukan change
        transactionData.put("user_id", userId);
        // Customer name from transaction or default
        String customerName = transaction.getCustomerName();
        if (customerName == null || customerName.isEmpty()) {
            customerName = "Walk-in Customer";
        }
        transactionData.put("customer_name", customerName);
        // Generate invoice number if not set
        String invoiceNo = transaction.getInvoiceNo();
        if (invoiceNo == null || invoiceNo.isEmpty()) {
            invoiceNo = "INV-" + System.currentTimeMillis();
        }
        transactionData.put("invoice_no", invoiceNo);

        Log.d(TAG, "Creating transaction with data: " + transactionData.toString());

        Call<Transaction> call = service.addTransaction(ANON_KEY, sessionToken, "return=representation", "application/vnd.pgrst.object+json", transactionData);
        call.enqueue(new Callback<Transaction>() {
            @Override
            public void onResponse(Call<Transaction> call, Response<Transaction> response) {
                Log.d(TAG, "Transaction response code: " + response.code());
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "Transaction created successfully with ID: " + response.body().getId());
                    saveTransactionItems(response.body().getId(), transaction, callback);
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        Log.e(TAG, "Failed to create transaction. Response code: " + response.code() + ", Error: " + errorBody);
                        callback.onError("Failed to create transaction: " + errorBody);
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading error body", e);
                        callback.onError("Failed to create transaction: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onFailure(Call<Transaction> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    private void saveTransactionItems(String transactionId, Transaction transaction, DatabaseCallback callback) {
        // Aggregate by product_id to capture quantities from cart (+/-)
        Map<String, Integer> counts = new HashMap<>();
        Map<String, IceCreamProduct> productById = new HashMap<>();
        for (IceCreamProduct p : transaction.getItems()) {
            counts.put(p.getId(), counts.getOrDefault(p.getId(), 0) + 1);
            productById.put(p.getId(), p);
        }

        List<Map<String, Object>> itemsData = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            String productId = entry.getKey();
            int qty = entry.getValue();
            IceCreamProduct p = productById.get(productId);
            if (p == null) continue;

            Map<String, Object> itemData = new HashMap<>();
            itemData.put("transaction_id", transactionId);
            itemData.put("product_id", productId);
            itemData.put("quantity", qty);
            // store unit price; total shown uses transaction.total, and details show unit price x qty
            itemData.put("price", p.getPrice());
            itemsData.add(itemData);
        }

        Call<Void> call = service.addTransactionItems(ANON_KEY, sessionToken, "return=minimal", itemsData);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(transactionId);
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        callback.onError("Failed to save transaction items: " + errorBody);
                    } catch (IOException e) {
                        callback.onError("Failed to save transaction items: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    public void loadTransactions(TransactionsCallback callback) {
        if (!isUserSignedIn()) {
            callback.onError("User not signed in");
            return;
        }

        Log.d(TAG, "Loading transactions for user: " + userId);
        
        Call<List<Transaction>> call = service.getTransactions(
                ANON_KEY,
                sessionToken,
                "eq." + userId,
                "id,total,amount_paid,change_amount,timestamp,customer_name,invoice_no",
                "timestamp.desc"
        );

        call.enqueue(new Callback<List<Transaction>>() {
            @Override
            public void onResponse(Call<List<Transaction>> call, Response<List<Transaction>> response) {
                Log.d(TAG, "Transactions response code: " + response.code());
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "Loaded " + response.body().size() + " transactions");
                    callback.onSuccess(response.body());
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        Log.e(TAG, "Failed to load transactions: " + errorBody);
                        callback.onError("Failed to load transactions: " + errorBody);
                    } catch (IOException e) {
                        Log.e(TAG, "Error reading error body", e);
                        callback.onError("Failed to load transactions: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onFailure(Call<List<Transaction>> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    // Callback interfaces
    public interface AuthCallback {
        void onSuccess();
        void onError(String error);
    }

    public interface DatabaseCallback {
        void onSuccess(String id);
        void onError(String error);
    }

    public interface ProductsCallback {
        void onSuccess(List<IceCreamProduct> products);
        void onError(String error);
    }

    public interface TransactionsCallback {
        void onSuccess(List<Transaction> transactions);
        void onError(String error);
    }

    public interface ItemsCallback {
        void onSuccess(List<String> items);
        void onError(String error);
    }

    // Simple callback for one-shot operations like image upload
    public interface SimpleCallback {
        void onSuccess(String result);
        void onError(String error);
    }

    // Hapus transaksi terpilih (server-side)
    public void deleteTransactionsByIds(List<String> ids, DatabaseCallback callback) {
        if (!isUserSignedIn()) {
            callback.onError("User not signed in");
            return;
        }
        if (ids == null || ids.isEmpty()) {
            callback.onError("No transactions selected");
            return;
        }

        // Bentuk filter in.(id1,id2,...)
        StringBuilder in = new StringBuilder("in.(");
        for (int i = 0; i < ids.size(); i++) {
            in.append(ids.get(i));
            if (i < ids.size() - 1) in.append(',');
        }
        in.append(')');

        // 1) Hapus items
        Call<Void> delItems = service.deleteTransactionItems(ANON_KEY, sessionToken, in.toString());
        delItems.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                // Lanjutkan hapus transactions meski items sudah tidak ada
                deleteTransactionsFilter(in.toString(), null, callback);
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                // Tetap lanjut hapus transactions, namun laporkan kegagalan items bila transaksi juga gagal
                deleteTransactionsFilter(in.toString(), null, new DatabaseCallback() {
                    @Override
                    public void onSuccess(String id) { callback.onSuccess(id); }

                    @Override
                    public void onError(String error) { callback.onError("Delete items failed: " + t.getMessage() + "; Delete tx error: " + error); }
                });
            }
        });
    }

    // Hapus semua transaksi milik user saat ini (server-side)
    public void deleteAllTransactionsForUser(DatabaseCallback callback) {
        if (!isUserSignedIn()) {
            callback.onError("User not signed in");
            return;
        }

        // Hapus semua items milik transaksi user (menggunakan subfilter user_id melalui join tidak didukung langsung),
        // jadi kita panggil dua tahap: ambil id transaksi lalu hapus by ids. Untuk sederhana, gunakan delete transactions by user_id,
        // namun pastikan RLS mengizinkan. Untuk items, kita lakukan wildcard: first fetch ids.
        // Simpler approach: delete items by transaction_id using "in.(...)" requires ids; sehingga kita lakukan deletion order as:
        // 1) Delete transactions by user_id (RLS should cascade? If no FK cascade, do manual items first.)
        // Untuk amannya, kita lakukan: loadTransactions lalu delete by ids.

        loadTransactions(new TransactionsCallback() {
            @Override
            public void onSuccess(List<Transaction> transactions) {
                List<String> ids = new ArrayList<>();
                for (Transaction t : transactions) ids.add(t.getId());
                if (ids.isEmpty()) {
                    callback.onSuccess("none");
                    return;
                }
                deleteTransactionsByIds(ids, callback);
            }

            @Override
            public void onError(String error) { callback.onError(error); }
        });
    }

    private void deleteTransactionsFilter(String idFilterOrNull, String userIdFilterOrNull, DatabaseCallback callback) {
        Call<Void> delTx = service.deleteTransactions(
                ANON_KEY,
                sessionToken,
                idFilterOrNull,
                userIdFilterOrNull
        );
        delTx.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess("ok");
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        callback.onError("Failed to delete transactions: " + errorBody);
                    } catch (IOException e) {
                        callback.onError("Failed to delete transactions: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    // ===============================
    // Category CRUD Methods
    // ===============================

    public void loadCategories(CategoriesCallback callback) {
        if (!isUserSignedIn()) {
            callback.onError("User not signed in");
            return;
        }

        Call<List<com.example.inktouchapp_android.product.Category>> call = service.getCategories(
                ANON_KEY,
                sessionToken,
                "id,name,description,created_at",
                "name.asc"
        );

        call.enqueue(new Callback<List<com.example.inktouchapp_android.product.Category>>() {
            @Override
            public void onResponse(Call<List<com.example.inktouchapp_android.product.Category>> call, Response<List<com.example.inktouchapp_android.product.Category>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        callback.onError("Failed to load categories: " + errorBody);
                    } catch (IOException e) {
                        callback.onError("Failed to load categories: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onFailure(Call<List<com.example.inktouchapp_android.product.Category>> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    public void addCategory(com.example.inktouchapp_android.product.Category category, DatabaseCallback callback) {
        if (!isUserSignedIn()) {
            callback.onError("User not signed in");
            return;
        }

        Map<String, Object> categoryData = new HashMap<>();
        categoryData.put("name", category.getName());
        if (category.getDescription() != null && !category.getDescription().isEmpty()) {
            categoryData.put("description", category.getDescription());
        }

        Call<Void> call = service.addCategory(ANON_KEY, sessionToken, "return=minimal", categoryData);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(category.getId());
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        callback.onError("Failed to add category: " + errorBody);
                    } catch (IOException e) {
                        callback.onError("Failed to add category: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    public void updateCategory(com.example.inktouchapp_android.product.Category category, DatabaseCallback callback) {
        if (!isUserSignedIn()) {
            callback.onError("User not signed in");
            return;
        }

        Map<String, Object> categoryData = new HashMap<>();
        categoryData.put("name", category.getName());
        if (category.getDescription() != null) {
            categoryData.put("description", category.getDescription());
        }

        Call<Void> call = service.updateCategory(ANON_KEY, sessionToken, "return=minimal", "eq." + category.getId(), categoryData);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(category.getId());
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        callback.onError("Failed to update category: " + errorBody);
                    } catch (IOException e) {
                        callback.onError("Failed to update category: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    public void deleteCategory(String categoryId, DatabaseCallback callback) {
        if (!isUserSignedIn()) {
            callback.onError("User not signed in");
            return;
        }

        Call<Void> call = service.deleteCategory(ANON_KEY, sessionToken, "eq." + categoryId);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    callback.onSuccess(categoryId);
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        callback.onError("Failed to delete category: " + errorBody);
                    } catch (IOException e) {
                        callback.onError("Failed to delete category: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                callback.onError("Network error: " + t.getMessage());
            }
        });
    }

    // Callback interface for categories
    public interface CategoriesCallback {
        void onSuccess(List<com.example.inktouchapp_android.product.Category> categories);
        void onError(String error);
    }
}