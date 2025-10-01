package com.example.easycoff.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.content.Intent;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.easycoff.R;
import com.example.easycoff.product.CartItem;
import com.example.easycoff.product.EasyCoffProduct;
import com.example.easycoff.product.Transaction;
import com.example.easycoff.supabase.SupabaseHelper;
import com.example.easycoff.ui.CartAdapter;
import com.example.easycoff.ui.ProductAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.appbar.MaterialToolbar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;

public class MainActivity extends AppCompatActivity {
    private RecyclerView rvProducts, rvCart;
    private TextView tvTotal, tvChange, tvGreeting;
    private EditText etPaid, etCustomer;
    private MaterialButton btnCheckout;
    private ProductAdapter productAdapter;
    private CartAdapter cartAdapter;
    private final java.util.List<CartItem> cart = new java.util.ArrayList<>();
    private final java.text.NumberFormat fmt = java.text.NumberFormat.getCurrencyInstance(new java.util.Locale("in","ID"));
    private SupabaseHelper supabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        fmt.setMaximumFractionDigits(0);

        supabase = SupabaseHelper.getInstance();
        initViews();
        setupLists();
        loadProducts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh product list after returning from CRUD
        loadProducts();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        // Show hamburger menu on the RIGHT (action menu)
        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.setOnMenuItemClickListener(this::onToolbarMenuItem);
        
        // Set menu icon tint to white for contrast with coffee brown toolbar
        if (toolbar.getMenu() != null && toolbar.getMenu().size() > 0) {
            android.graphics.drawable.Drawable menuIcon = toolbar.getMenu().getItem(0).getIcon();
            if (menuIcon != null) {
                menuIcon.setTint(getResources().getColor(android.R.color.white));
            }
        }

        rvProducts = findViewById(R.id.rvProducts);
        rvCart = findViewById(R.id.rvCart);
        tvTotal = findViewById(R.id.tvTotal);
        tvChange = findViewById(R.id.tvChange);
        etPaid = findViewById(R.id.etPaid);
        etCustomer = findViewById(R.id.etCustomer);
        btnCheckout = findViewById(R.id.btnCheckout);

        // Title: POS only (no email)
        toolbar.setTitle("☕ EasyCoff");

        etPaid.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateTotals(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnCheckout.setOnClickListener(v -> checkout());
    }

    private void showToolbarMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenuInflater().inflate(R.menu.menu_main, pm.getMenu());
        pm.setOnMenuItemClickListener(this::onToolbarMenuItem);
        pm.show();
    }

    private boolean onToolbarMenuItem(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_product_management) {
            startActivity(new Intent(this, ProductCrudActivity.class));
            return true;
        } else if (id == R.id.action_history) {
            startActivity(new Intent(this, HistoryActivity.class));
            return true;
        } else if (id == R.id.action_logout) {
            new AlertDialog.Builder(this)
                    .setTitle("Logout")
                    .setMessage("Yakin ingin keluar?")
                    .setPositiveButton("Keluar", (d, w) -> {
                        supabase.signOut(this);
                        Intent i = new Intent(this, AuthActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        finish();
                    })
                    .setNegativeButton("Batal", null)
                    .show();
            return true;
        }
        return false;
    }

    private void setupLists() {
        productAdapter = new ProductAdapter(this, product -> {
            addToCart(product);
        });
        rvProducts.setLayoutManager(new GridLayoutManager(this, 2));
        rvProducts.setAdapter(productAdapter);

        cartAdapter = new CartAdapter(new CartAdapter.Listener() {
            @Override public void onIncrease(int position) { changeQty(position, +1); }
            @Override public void onDecrease(int position) { changeQty(position, -1); }
        });
        rvCart.setLayoutManager(new LinearLayoutManager(this));
        rvCart.setAdapter(cartAdapter);
        cartAdapter.setData(cart);
        updateTotals();
    }

    private void loadProducts() {
        supabase.loadProducts(new SupabaseHelper.ProductsCallback() {
            @Override
            public void onSuccess(java.util.List<EasyCoffProduct> products) {
                runOnUiThread(() -> productAdapter.setData(products));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void addToCart(EasyCoffProduct p) {
        for (CartItem ci : cart) {
            if (ci.getProduct().getId().equals(p.getId())) {
                ci.setQuantity(ci.getQuantity() + 1);
                cartAdapter.setData(cart);
                updateTotals();
                return;
            }
        }
        cart.add(new CartItem(p, 1));
        cartAdapter.setData(cart);
        updateTotals();
    }

    private void changeQty(int position, int delta) {
        if (position < 0 || position >= cart.size()) return;
        CartItem ci = cart.get(position);
        int q = ci.getQuantity() + delta;
        if (q <= 0) {
            cart.remove(position);
        } else {
            ci.setQuantity(q);
        }
        cartAdapter.setData(cart);
        updateTotals();
    }

    private double calcTotal() {
        double total = 0;
        for (CartItem ci : cart) total += ci.getLineTotal();
        return total;
    }

    private void updateTotals() {
        double total = calcTotal();
        tvTotal.setText("Total: " + fmt.format(total));
        double paid = 0;
        try { paid = Double.parseDouble(String.valueOf(etPaid.getText()).trim()); } catch (Exception ignored) {}
        double change = Math.max(0, paid - total);
        tvChange.setText("Kembalian: " + fmt.format(change));
    }

    private void checkout() {
        if (cart.isEmpty()) {
            Toast.makeText(this, "Keranjang kosong", Toast.LENGTH_SHORT).show();
            return;
        }
        double total = calcTotal();
        double paid = 0;
        try { paid = Double.parseDouble(String.valueOf(etPaid.getText()).trim()); } catch (Exception ignored) {}
        if (paid < total) {
            Toast.makeText(this, "Bayar kurang dari total", Toast.LENGTH_SHORT).show();
            return;
        }

        Transaction tx = new Transaction();
        tx.setTotal(total);
        tx.setAmountPaid(paid);
        tx.setChange(paid - total);
        String customer = String.valueOf(etCustomer.getText());
        tx.setCustomerName(customer == null || customer.trim().isEmpty() ? "Walk-in Customer" : customer.trim());

        // Expand cart to items list (duplicated per quantity), SupabaseHelper will aggregate
        java.util.List<EasyCoffProduct> items = new java.util.ArrayList<>();
        for (CartItem ci : cart) {
            for (int i = 0; i < ci.getQuantity(); i++) items.add(ci.getProduct());
        }
        tx.setItems(items);

        btnCheckout.setEnabled(false);
        supabase.addTransaction(tx, new SupabaseHelper.DatabaseCallback() {
            @Override
            public void onSuccess(String id) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Transaksi berhasil", Toast.LENGTH_LONG).show();
                    cart.clear();
                    cartAdapter.setData(cart);
                    etPaid.setText("");
                    etCustomer.setText("");
                    updateTotals();
                    btnCheckout.setEnabled(true);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, error, Toast.LENGTH_LONG).show();
                    btnCheckout.setEnabled(true);
                });
            }
        });
    }
}