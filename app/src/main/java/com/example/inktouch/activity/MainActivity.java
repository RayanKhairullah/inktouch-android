package com.example.inktouch.activity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.android.material.textfield.TextInputEditText;

import com.example.inktouch.product.IceCreamProduct;
import com.example.inktouch.R;
import com.example.inktouch.supabase.SupabaseHelper;
import com.example.inktouch.product.Transaction;
import com.example.inktouch.adapter.ProductAdapter;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private List<IceCreamProduct> products = new ArrayList<>();
    private List<IceCreamProduct> allProducts = new ArrayList<>(); // Original list for search
    private List<IceCreamProduct> cart = new ArrayList<>();
    private List<Transaction> transactionHistory = new ArrayList<>();
    private ArrayAdapter<CartLine> cartAdapter;
    private final List<CartLine> cartLines = new ArrayList<>();
    private TextView totalTextView;
    private double total = 0;
    private ProductAdapter productAdapter;

    // Konstanta untuk request code
    private static final int PRODUCT_MANAGEMENT_REQUEST_CODE = 3;

    private SupabaseHelper supabaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Inisialisasi sesi (auto-login dengan refresh token bila ada)
        supabaseHelper = SupabaseHelper.getInstance();
        supabaseHelper.init(getApplicationContext());
        supabaseHelper.initializeSession(this, new SupabaseHelper.SessionInitCallback() {
            @Override
            public void onReady() { runOnUiThread(() -> setupUI()); }
            @Override
            public void onRequireLogin() {
                runOnUiThread(() -> {
                    Intent intent = new Intent(MainActivity.this, AuthActivity.class);
                    startActivity(intent);
                    finish();
                });
            }
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(MainActivity.this, AuthActivity.class);
                    startActivity(intent);
                    finish();
                });
            }
        });
    }

    private void setupUI() {
        setContentView(R.layout.activity_main);

        // Setup cart ListView
        ListView cartListView = findViewById(R.id.cart_list_view);
        totalTextView = findViewById(R.id.total_text_view);

        cartAdapter = new ArrayAdapter<CartLine>(this, R.layout.cart_item, cartLines) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.cart_item, parent, false);
                }
                TextView itemText = convertView.findViewById(R.id.cart_item_text);
                TextView tvQty = convertView.findViewById(R.id.tv_quantity);
                View btnPlus = convertView.findViewById(R.id.btn_plus);
                View btnMinus = convertView.findViewById(R.id.btn_minus);

                CartLine line = cartLines.get(position);
                IceCreamProduct product = line.product;
                int qty = line.quantity;

                itemText.setText(String.format(Locale.getDefault(), "%s - Rp %.0f", product.getName(), product.getPrice()));
                tvQty.setText(String.valueOf(qty));

                btnPlus.setOnClickListener(v -> {
                    // Add one unit if stock allows
                    int inCart = countInCart(product.getId());
                    if (product.getStock() > inCart) {
                        cart.add(product);
                        total += product.getPrice();
                        totalTextView.setText(String.format(Locale.getDefault(), "Total: Rp %.0f", total));
                        rebuildCartLines();
                        notifyDataSetChanged();
                    } else {
                        Toast.makeText(MainActivity.this, "Stok " + product.getName() + " habis!", Toast.LENGTH_SHORT).show();
                    }
                });

                btnMinus.setOnClickListener(v -> {
                    // Remove one unit
                    boolean removed = removeOneFromCart(product.getId());
                    if (removed) {
                        total -= product.getPrice();
                        if (total < 0) total = 0;
                        totalTextView.setText(String.format(Locale.getDefault(), "Total: Rp %.0f", total));
                        rebuildCartLines();
                        notifyDataSetChanged();
                    }
                });
                
                return convertView;
            }
        };
        cartListView.setAdapter(cartAdapter);

        // Setup RecyclerView untuk daftar produk
        RecyclerView productsRecyclerView = findViewById(R.id.products_recycler_view);
        productsRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        productAdapter = new ProductAdapter(products, this::addToCart);
        productsRecyclerView.setAdapter(productAdapter);

        // Setup search functionality
        TextInputEditText etSearchProduct = findViewById(R.id.et_search_product);
        etSearchProduct.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterProducts(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Setup tombol checkout
        Button btnCheckout = findViewById(R.id.btn_checkout);
        btnCheckout.setOnClickListener(v -> checkout());

        // Muat data dari Supabase
        loadDataFromSupabase();
    }

    private void filterProducts(String query) {
        if (query.isEmpty()) {
            products.clear();
            products.addAll(allProducts);
        } else {
            List<IceCreamProduct> filteredList = new ArrayList<>();
            for (IceCreamProduct product : allProducts) {
                if (product.getName().toLowerCase().contains(query.toLowerCase())) {
                    filteredList.add(product);
                }
            }
            products.clear();
            products.addAll(filteredList);
        }
        productAdapter.notifyDataSetChanged();
    }

    private void loadDataFromSupabase() {
        supabaseHelper.loadProducts(new SupabaseHelper.ProductsCallback() {

            @Override
            public void onSuccess(List<IceCreamProduct> loadedProducts) {
                runOnUiThread(() -> {
                    allProducts.clear();
                    allProducts.addAll(loadedProducts);
                    products.clear();
                    products.addAll(loadedProducts);
                    productAdapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e("MainActivity", "Error loading products: " + error);
                    Toast.makeText(MainActivity.this, "Gagal memuat produk: " + error,
                            Toast.LENGTH_SHORT).show();
                });
            }
        });

        // Muat riwayat transaksi dari Supabase
        supabaseHelper.loadTransactions(new SupabaseHelper.TransactionsCallback() {
            @Override
            public void onSuccess(List<Transaction> transactions) {
                runOnUiThread(() -> {
                    transactionHistory.clear();
                    transactionHistory.addAll(transactions);

                    Log.d("MainActivity", "Transactions loaded: " + transactions.size());
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e("MainActivity", "Error loading transactions: " + error);
                    Toast.makeText(MainActivity.this, "Gagal memuat riwayat: " + error,
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void addToCart(IceCreamProduct product) {
        // Cek stok yang tersedia (stok - jumlah di keranjang)
        int countInCart = countInCart(product.getId());

        if (product.getStock() > countInCart) {
            cart.add(product);
            total += product.getPrice();
            totalTextView.setText(String.format(Locale.getDefault(), "Total: Rp %.0f", total));
            rebuildCartLines();
            cartAdapter.notifyDataSetChanged();
        } else {
            Toast.makeText(this, "Stok " + product.getName() + " habis!",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void checkout() {
        if (cart.isEmpty()) {
            Toast.makeText(this, "Keranjang kosong!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Agregasi keranjang untuk mendapatkan jumlah per produk
        Map<String, Integer> productCount = new HashMap<>();
        for (IceCreamProduct product : cart) {
            productCount.put(product.getId(),
                    productCount.getOrDefault(product.getId(), 0) + 1);
        }

        // Cek stok untuk semua produk
        boolean sufficientStock = true;
        for (Map.Entry<String, Integer> entry : productCount.entrySet()) {
            String productId = entry.getKey();
            int quantity = entry.getValue();
            IceCreamProduct product = findProductById(productId);
            if (product == null || product.getStock() < quantity) {
                sufficientStock = false;
                break;
            }
        }

        if (sufficientStock) {
            // Tampilkan dialog input pembayaran
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("💸 Pembayaran");

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (16 * getResources().getDisplayMetrics().density);
            layout.setPadding(pad, pad, pad, pad);

            TextView tvTotal = new TextView(this);
            tvTotal.setText(String.format(Locale.getDefault(), "Total: Rp %.0f", total));
            tvTotal.setPadding(0, 0, 0, pad);
            layout.addView(tvTotal);

            EditText etCustomerName = new EditText(this);
            etCustomerName.setHint("Nama Pelanggan (opsional)");
            etCustomerName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            LinearLayout.LayoutParams paramsName = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            paramsName.setMargins(0, 0, 0, pad / 2);
            etCustomerName.setLayoutParams(paramsName);
            layout.addView(etCustomerName);

            EditText etPaid = new EditText(this);
            etPaid.setHint("Tunai diterima");
            etPaid.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            LinearLayout.LayoutParams paramsPaid = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            );
            paramsPaid.setMargins(0, pad / 2, 0, 0);
            etPaid.setLayoutParams(paramsPaid);
            layout.addView(etPaid);

            builder.setView(layout);
            builder.setNegativeButton("Batal", null);
            builder.setPositiveButton("Bayar", null);

            AlertDialog dialog = builder.create();
            dialog.setOnShowListener(d -> {
                // Set button text colors for dark/light mode
                Button btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                Button btnNegative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                int onSurface = ContextCompat.getColor(MainActivity.this, R.color.on_surface);
                if (btnPositive != null) btnPositive.setTextColor(onSurface);
                if (btnNegative != null) btnNegative.setTextColor(onSurface);

                btnPositive.setOnClickListener(v -> {
                    String customerName = etCustomerName.getText().toString().trim();
                    if (customerName.isEmpty()) {
                        customerName = "Walk-in Customer";
                    }

                    String paidStr = etPaid.getText().toString().trim();
                    if (paidStr.isEmpty()) {
                        etPaid.setError("Masukkan nominal");
                        return;
                    }
                    double paid;
                    try {
                        paid = Double.parseDouble(paidStr);
                    } catch (NumberFormatException e) {
                        etPaid.setError("Nominal tidak valid");
                        return;
                    }
                    if (paid < total) {
                        etPaid.setError("Tunai kurang dari total");
                        return;
                    }

                    double change = paid - total;
                    final String finalCustomerName = customerName;

                    // Kurangi stok dan update ke Supabase
                    for (Map.Entry<String, Integer> entry : productCount.entrySet()) {
                        String productId = entry.getKey();
                        int quantity = entry.getValue();
                        IceCreamProduct product = findProductById(productId);
                        if (product != null) {
                            product.setStock(product.getStock() - quantity);
                            supabaseHelper.updateProduct(product, new SupabaseHelper.DatabaseCallback() {
                                @Override
                                public void onSuccess(String id) { /* no-op */ }

                                @Override
                                public void onError(String error) {
                                    runOnUiThread(() ->
                                            Toast.makeText(MainActivity.this, "Gagal memperbarui stok: " + error, Toast.LENGTH_SHORT).show());
                                }
                            });
                        }
                    }

                    // Simpan transaksi
                    String transactionId = UUID.randomUUID().toString();
                    Transaction transaction = new Transaction(
                            transactionId,
                            new ArrayList<>(cart),
                            total,
                            paid,
                            change,
                            new Date());
                    transaction.setCustomerName(finalCustomerName);

                    supabaseHelper.addTransaction(transaction, new SupabaseHelper.DatabaseCallback() {
                        @Override
                        public void onSuccess(String id) {
                            runOnUiThread(() ->
                                    Toast.makeText(MainActivity.this, String.format(Locale.getDefault(), "Transaksi berhasil. Kembalian: Rp %.0f", change), Toast.LENGTH_LONG).show());
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() ->
                                    Toast.makeText(MainActivity.this, "Gagal menyimpan transaksi: " + error, Toast.LENGTH_SHORT).show());
                        }
                    });

                    // Bersihkan keranjang dan perbarui UI
                    cart.clear();
                    total = 0;
                    totalTextView.setText("Total: Rp 0");
                    rebuildCartLines();
                    cartAdapter.notifyDataSetChanged();

                    RecyclerView productsRecyclerView = findViewById(R.id.products_recycler_view);
                    ProductAdapter adapter = (ProductAdapter) productsRecyclerView.getAdapter();
                    if (adapter != null) adapter.notifyDataSetChanged();

                    dialog.dismiss();
                });
            });
            dialog.show();
        } else {
            Toast.makeText(this, "Gagal checkout: stok tidak mencukupi",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private IceCreamProduct findProductById(String id) {
        for (IceCreamProduct product : products) {
            if (product.getId().equals(id)) {
                return product;
            }
        }
        return null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_dashboard) {
            Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
            startActivity(intent);
            return true;
        }
        else if (id == R.id.action_category_management) {
            Intent intent = new Intent(MainActivity.this, CategoryManagementActivity.class);
            startActivity(intent);
            return true;
        }
        else if (id == R.id.action_product_management) {
            Intent intent = new Intent(MainActivity.this, ProductManagementActivity.class);
            intent.putParcelableArrayListExtra("products", new ArrayList<>(products));
            startActivityForResult(intent, PRODUCT_MANAGEMENT_REQUEST_CODE);
            return true;
        }
        else if (id == R.id.action_history) {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            intent.putParcelableArrayListExtra("transactions",
                    new ArrayList<>(transactionHistory));
            startActivityForResult(intent, 1);
            return true;
        }
        else if (id == R.id.action_logout) {
            // Tampilkan konfirmasi sebelum logout
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("💁‍♂️ Keluar Aplikasi")
                    .setMessage("Yakin ingin keluar dari aplikasi?")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Keluar", (dialog, which) -> {
                        supabaseHelper.signOut(new SupabaseHelper.AuthCallback() {
                            @Override
                            public void onSuccess() {
                                runOnUiThread(() -> {
                                    Intent intent = new Intent(MainActivity.this, AuthActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                });
                            }

                            @Override
                            public void onError(String error) {
                                runOnUiThread(() -> {
                                    Toast.makeText(MainActivity.this, "Gagal logout: " + error,
                                            Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    })
                    .show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
            super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PRODUCT_MANAGEMENT_REQUEST_CODE && resultCode == RESULT_OK) {
            // MUAT ULANG DATA DI SINI
            loadDataFromSupabase();

            Toast.makeText(this, "Daftar produk berhasil diperbarui",
                    Toast.LENGTH_SHORT).show();
        }
        else if (requestCode == 1 && resultCode == RESULT_OK) {
            // Riwayat transaksi telah dihapus, perbarui data
            loadDataFromSupabase();
        }
    }

    // Helper to count occurrences of a product id in cart
    private int countInCart(String productId) {
        int c = 0;
        for (IceCreamProduct p : cart) {
            if (p.getId().equals(productId)) c++;
        }
        return c;
    }

    // Helper to remove one occurrence of a product id from cart
    private boolean removeOneFromCart(String productId) {
        for (int i = 0; i < cart.size(); i++) {
            if (cart.get(i).getId().equals(productId)) {
                cart.remove(i);
                return true;
            }
        }
        return false;
    }

    // Build aggregated cart lines for display
    private void rebuildCartLines() {
        Map<String, CartLine> map = new HashMap<>();
        for (IceCreamProduct p : cart) {
            CartLine line = map.get(p.getId());
            if (line == null) {
                line = new CartLine(p, 0);
                map.put(p.getId(), line);
            }
            line.quantity += 1;
        }
        cartLines.clear();
        cartLines.addAll(map.values());
    }

    // Aggregated cart line
    private static class CartLine {
        final IceCreamProduct product;
        int quantity;

        CartLine(IceCreamProduct product, int quantity) {
            this.product = product;
            this.quantity = quantity;
        }
    }
}