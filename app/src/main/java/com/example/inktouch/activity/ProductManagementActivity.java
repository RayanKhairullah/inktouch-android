package com.example.inktouch.activity;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.inktouch.product.IceCreamProduct;
import com.example.inktouch.R;
import com.example.inktouch.supabase.SupabaseHelper;
import com.example.inktouch.adapter.ProductManagementAdapter;

import java.util.List;
import java.util.ArrayList;

public class ProductManagementActivity extends AppCompatActivity {
    private List<IceCreamProduct> products;
    private List<IceCreamProduct> allProducts = new ArrayList<>(); // Original list for search
    private ProductManagementAdapter productAdapter;
    private boolean isDeletingMode = false;
    private SupabaseHelper supabaseHelper;

    // Konstanta untuk request code
    public static final int PRODUCT_ADDED_REQUEST_CODE = 1001;
    public static final int PRODUCT_UPDATED_REQUEST_CODE = 1002;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_management);

        // Inisialisasi SupabaseHelper
        supabaseHelper = SupabaseHelper.getInstance();

        // Ambil daftar produk dari intent
        products = getIntent().getParcelableArrayListExtra("products");
        if (products == null) {
            products = new ArrayList<>();
        }
        allProducts.addAll(products);
        // Setup ListView untuk daftar produk
        ListView productsListView = findViewById(R.id.products_list_view);
        productAdapter = new ProductManagementAdapter(this, products);
        productsListView.setAdapter(productAdapter);

        // Setup tombol kembali
        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // Setup FAB untuk tambah produk
        FloatingActionButton fabAdd = findViewById(R.id.fab_add_product);
        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(this, AddProductActivity.class);
            startActivityForResult(intent, 1);
        });

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

        // Setup klik item
        productsListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (isDeletingMode) {
                    // Toggle selection via adapter
                    IceCreamProduct product = products.get(position);
                    productAdapter.toggleSelection(product);
                } else {
                    // Edit produk
                    IceCreamProduct product = products.get(position);
                    Intent intent = new Intent(ProductManagementActivity.this, AddProductActivity.class);

                    // Jika ini mode edit, kirim data produk yang akan diedit
                    intent.putExtra("edit_product", product);
                    startActivityForResult(intent, 1);
                }
            }
        });

        // Long click to enter deleting mode and select the item
        productsListView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (!isDeletingMode) {
                isDeletingMode = true;
                productAdapter.setDeletingMode(true);
            }
            IceCreamProduct product = products.get(position);
            productAdapter.toggleSelection(product);
            return true;
        });
    }

    private void filterProducts(String query) {
        if (query.isEmpty()) {
            products.clear();
            products.addAll(allProducts);
        } else {
            List<IceCreamProduct> filteredList = new ArrayList<>();
            for (IceCreamProduct product : allProducts) {
                if (product.getName().toLowerCase().contains(query.toLowerCase()) ||
                    (product.getSku() != null && product.getSku().toLowerCase().contains(query.toLowerCase()))) {
                    filteredList.add(product);
                }
            }
            products.clear();
            products.addAll(filteredList);
        }
        productAdapter.notifyDataSetChanged();
    }

    private void deleteSelectedProducts() {
        List<IceCreamProduct> selected = productAdapter.getSelectedProducts();
        if (selected == null || selected.isEmpty()) {
            Toast.makeText(this, "Tidak ada produk terpilih", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Konfirmasi Hapus")
                .setMessage("Apakah Anda yakin ingin menghapus " + selected.size() + " produk terpilih?")
                .setPositiveButton("Ya", (dialog, which) -> {
                    for (IceCreamProduct product : selected) {
                        // Hapus dari Supabase
                        supabaseHelper.deleteProduct(product.getId(), new SupabaseHelper.DatabaseCallback() {
                            @Override
                            public void onSuccess(String id) {
                                runOnUiThread(() -> {
                                    Toast.makeText(ProductManagementActivity.this,
                                            "Produk berhasil dihapus", Toast.LENGTH_SHORT).show();
                                });
                            }

                            @Override
                            public void onError(String error) {
                                runOnUiThread(() -> {
                                    Toast.makeText(ProductManagementActivity.this,
                                            "Gagal menghapus produk: " + error, Toast.LENGTH_SHORT).show();
                                });
                            }
                        });
                    }

                    // Beri tahu MainActivity untuk memperbarui daftar produk
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("product_deleted", true);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                })
                .setNegativeButton("Tidak", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1 && resultCode == RESULT_OK) {
            // Notifikasi ke MainActivity untuk refresh data
            Intent resultIntent = new Intent();
            setResult(RESULT_OK, resultIntent);
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (isDeletingMode) {
            isDeletingMode = false;
            // Clear adapter selections
            List<IceCreamProduct> selected = productAdapter.getSelectedProducts();
            productAdapter.setDeletingMode(false);
            productAdapter.notifyDataSetChanged();
        } else {
            super.onBackPressed();
        }
    }
}