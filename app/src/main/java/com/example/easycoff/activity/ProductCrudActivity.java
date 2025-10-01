package com.example.easycoff.activity;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;
import android.net.Uri;
import android.widget.Button;
import android.widget.ImageView;
import android.content.Intent;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.easycoff.R;
import com.example.easycoff.product.EasyCoffProduct;
import com.example.easycoff.supabase.SupabaseHelper;
import com.example.easycoff.ui.ProductManageAdapter;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class ProductCrudActivity extends AppCompatActivity implements ProductManageAdapter.Listener {
    private RecyclerView rv;
    private FloatingActionButton fab;
    private ProductManageAdapter adapter;
    private SupabaseHelper supabase;
    private final List<EasyCoffProduct> products = new ArrayList<>();
    private Uri pickedImage;
    private ImageView ivPreview;
    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_product_crud);
        supabase = SupabaseHelper.getInstance();

        androidx.appcompat.widget.Toolbar tb = findViewById(R.id.toolbarProduct);
        tb.setNavigationOnClickListener(v -> finish());

        rv = findViewById(R.id.rvManageProducts);
        fab = findViewById(R.id.fabAddProduct);
        adapter = new ProductManageAdapter(products, this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        fab.setOnClickListener(v -> openForm(null));

        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null && ivPreview != null) {
                pickedImage = uri;
                ivPreview.setImageURI(uri);
            }
        });

        loadProducts();
    }

    private void loadProducts() {
        supabase.loadProducts(new SupabaseHelper.ProductsCallback() {
            @Override
            public void onSuccess(List<EasyCoffProduct> list) {
                runOnUiThread(() -> {
                    products.clear();
                    if (list != null) products.addAll(list);
                    adapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(ProductCrudActivity.this, error, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void openForm(@Nullable EasyCoffProduct existing) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_product_form, null, false);
        EditText etName = view.findViewById(R.id.etName);
        EditText etPrice = view.findViewById(R.id.etPrice);
        EditText etStock = view.findViewById(R.id.etStock);
        EditText etDescription = view.findViewById(R.id.etDescription);
        EditText etSku = view.findViewById(R.id.etSku);
        Switch swActive = view.findViewById(R.id.swActive);
        ivPreview = view.findViewById(R.id.ivPreview);
        Button btnPick = view.findViewById(R.id.btnPickImage);

        pickedImage = null;
        btnPick.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        if (existing != null) {
            etName.setText(existing.getName());
            etPrice.setText(String.valueOf((long) existing.getPrice()));
            etStock.setText(String.valueOf(existing.getStock()));
            etDescription.setText(existing.getDescription());
            etSku.setText(existing.getSku());
            if (existing.getImageUrl() != null) {
                // Simple preview using URI parse may not work for network; leave empty for simplicity
            }
            swActive.setChecked(existing.isActive());
        } else {
            swActive.setChecked(true);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(view)
                .setTitle(existing == null ? "Tambah Produk" : "Edit Produk")
                .setPositiveButton(existing == null ? "Simpan" : "Update", null)
                .setNegativeButton("Batal", (d,w)->d.dismiss())
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(btn -> {
                String name = etName.getText().toString().trim();
                String priceStr = etPrice.getText().toString().trim();
                String stockStr = etStock.getText().toString().trim();
                if (name.isEmpty() || priceStr.isEmpty() || stockStr.isEmpty()) {
                    Toast.makeText(this, "Nama/Price/Stock wajib diisi", Toast.LENGTH_SHORT).show();
                    return;
                }
                double price = 0; int stock = 0;
                try { price = Double.parseDouble(priceStr); } catch (Exception ignored) {}
                try { stock = Integer.parseInt(stockStr); } catch (Exception ignored) {}
                String desc = etDescription.getText().toString().trim();
                String sku = etSku.getText().toString().trim();
                boolean active = swActive.isChecked();

                final Runnable afterSaved = () -> runOnUiThread(() -> {
                    Toast.makeText(ProductCrudActivity.this, existing == null ? "Produk disimpan" : "Produk diupdate", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadProducts();
                });

                if (existing == null) {
                    EasyCoffProduct p = new EasyCoffProduct();
                    p.setName(name);
                    p.setPrice(price);
                    p.setStock(stock);
                    p.setDescription(desc);
                    p.setSku(sku);
                    p.setActive(active);

                    if (pickedImage != null) {
                        supabase.uploadImageAndGetUrl(ProductCrudActivity.this, pickedImage, new SupabaseHelper.UploadCallback() {
                            @Override public void onSuccess(String url) {
                                p.setImageUrl(url);
                                supabase.addProduct(p, new SupabaseHelper.DatabaseCallback() {
                                    @Override public void onSuccess(String id) { afterSaved.run(); }
                                    @Override public void onError(String error) { runOnUiThread(() -> Toast.makeText(ProductCrudActivity.this, error, Toast.LENGTH_LONG).show()); }
                                });
                            }
                            @Override public void onError(String error) {
                                runOnUiThread(() -> Toast.makeText(ProductCrudActivity.this, error, Toast.LENGTH_LONG).show());
                            }
                        });
                    } else {
                        supabase.addProduct(p, new SupabaseHelper.DatabaseCallback() {
                            @Override public void onSuccess(String id) { afterSaved.run(); }
                            @Override public void onError(String error) { runOnUiThread(() -> Toast.makeText(ProductCrudActivity.this, error, Toast.LENGTH_LONG).show()); }
                        });
                    }
                } else {
                    existing.setName(name);
                    existing.setPrice(price);
                    existing.setStock(stock);
                    existing.setDescription(desc);
                    existing.setSku(sku);
                    existing.setActive(active);

                    if (pickedImage != null) {
                        supabase.uploadImageAndGetUrl(ProductCrudActivity.this, pickedImage, new SupabaseHelper.UploadCallback() {
                            @Override public void onSuccess(String url) {
                                existing.setImageUrl(url);
                                supabase.updateProduct(existing, new SupabaseHelper.DatabaseCallback() {
                                    @Override public void onSuccess(String id) { afterSaved.run(); }
                                    @Override public void onError(String error) { runOnUiThread(() -> Toast.makeText(ProductCrudActivity.this, error, Toast.LENGTH_LONG).show()); }
                                });
                            }
                            @Override public void onError(String error) {
                                runOnUiThread(() -> Toast.makeText(ProductCrudActivity.this, error, Toast.LENGTH_LONG).show());
                            }
                        });
                    } else {
                        supabase.updateProduct(existing, new SupabaseHelper.DatabaseCallback() {
                            @Override public void onSuccess(String id) { afterSaved.run(); }
                            @Override public void onError(String error) { runOnUiThread(() -> Toast.makeText(ProductCrudActivity.this, error, Toast.LENGTH_LONG).show()); }
                        });
                    }
                }
            });
        });
        dialog.show();
    }

    @Override
    public void onEdit(int position) {
        if (position < 0 || position >= products.size()) return;
        openForm(products.get(position));
    }

    @Override
    public void onDelete(int position) {
        if (position < 0 || position >= products.size()) return;
        EasyCoffProduct p = products.get(position);
        new AlertDialog.Builder(this)
                .setTitle("Hapus Produk")
                .setMessage("Yakin hapus '" + p.getName() + "'?")
                .setPositiveButton("Hapus", (d,w)-> {
                    supabase.deleteProduct(p.getId(), new SupabaseHelper.DatabaseCallback() {
                        @Override
                        public void onSuccess(String id) {
                            runOnUiThread(() -> {
                                Toast.makeText(ProductCrudActivity.this, "Produk dihapus", Toast.LENGTH_SHORT).show();
                                loadProducts();
                            });
                        }
                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> Toast.makeText(ProductCrudActivity.this, error, Toast.LENGTH_LONG).show());
                        }
                    });
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}
