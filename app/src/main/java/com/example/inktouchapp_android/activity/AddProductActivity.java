package com.example.inktouchapp_android.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.bumptech.glide.Glide;
import com.example.inktouchapp_android.product.IceCreamProduct;
import com.example.inktouchapp_android.product.Category;
import com.example.inktouchapp_android.R;
import com.example.inktouchapp_android.supabase.SupabaseHelper;

import java.util.ArrayList;
import java.util.List;

import java.util.UUID;

public class AddProductActivity extends AppCompatActivity {
    private EditText etProductName, etProductPrice, etProductStock, etProductSku;
    private Spinner spinnerCategory;
    private Button btnSave, btnSelectImage;
    private ImageView ivProductPreview;
    private boolean isEditMode = false;
    private IceCreamProduct editProduct;
    private int selectedImageResId = R.drawable.inktouch;
    private byte[] selectedImageBytes = null;
    private String uploadedImageUrl = null;
    private String selectedFileName = null;
    private List<Category> categories = new ArrayList<>();
    private ArrayAdapter<Category> categoryAdapter;

    private final ActivityResultLauncher<String> pickImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    handleImagePicked(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product);

        // Inisialisasi view
        etProductName = findViewById(R.id.et_product_name);
        etProductPrice = findViewById(R.id.et_product_price);
        etProductStock = findViewById(R.id.et_product_stock);
        etProductSku = findViewById(R.id.et_product_sku);
        spinnerCategory = findViewById(R.id.spinner_category);
        btnSave = findViewById(R.id.btn_save_product);
        btnSelectImage = findViewById(R.id.btn_select_image);
        ivProductPreview = findViewById(R.id.iv_product_preview);

        // Setup category spinner
        categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);

        // Load categories
        loadCategories();

        // Setup image picker
        btnSelectImage.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        // Periksa apakah ini mode edit
        if (getIntent().hasExtra("edit_product")) {
            isEditMode = true;
            editProduct = getIntent().getParcelableExtra("edit_product");

            // Isi form dengan data produk yang akan diedit
            etProductName.setText(editProduct.getName());
            etProductPrice.setText(String.valueOf(editProduct.getPrice()));
            etProductStock.setText(String.valueOf(editProduct.getStock()));
            if (editProduct.getSku() != null) {
                etProductSku.setText(editProduct.getSku());
            }
            btnSave.setText("Perbarui Produk");
            // Set title khusus edit
            setTitle("✏️ Edit Produk Baru");

            // Preview gambar sebelumnya
            String prevUrl = editProduct.getImageUrl();
            selectedImageResId = editProduct.getImageResId();
            if (prevUrl != null && !prevUrl.isEmpty()) {
                Glide.with(this)
                        .load(prevUrl)
                        .placeholder(R.drawable.inktouch)
                        .error(R.drawable.inktouch)
                        .into(ivProductPreview);
            } else if (selectedImageResId > 0) {
                ivProductPreview.setImageResource(selectedImageResId);
            } else {
                ivProductPreview.setImageResource(R.drawable.inktouch);
            }
        }
    }

    private void loadCategories() {
        SupabaseHelper.getInstance().loadCategories(new SupabaseHelper.CategoriesCallback() {
            @Override
            public void onSuccess(List<Category> loadedCategories) {
                runOnUiThread(() -> {
                    categories.clear();
                    categories.addAll(loadedCategories);
                    categoryAdapter.notifyDataSetChanged();

                    // Set selected category if edit mode
                    if (isEditMode && editProduct != null && editProduct.getCategoryId() != null) {
                        for (int i = 0; i < categories.size(); i++) {
                            if (categories.get(i).getId().equals(editProduct.getCategoryId())) {
                                spinnerCategory.setSelection(i);
                                break;
                            }
                        }
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(AddProductActivity.this,
                        "Gagal memuat kategori: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void handleImagePicked(Uri uri) {
        try {
            // Load and compress to JPEG to keep size low
            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
            if (bitmap != null) {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, baos);
                selectedImageBytes = baos.toByteArray();
                ivProductPreview.setImageBitmap(bitmap);
                selectedFileName = "product_" + System.currentTimeMillis() + ".jpg";
                uploadedImageUrl = null; // reset, will upload on save
            } else {
                Toast.makeText(this, "Gagal memuat gambar", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Gagal memilih gambar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public void saveProduct(View view) {
        String name = etProductName.getText().toString().trim();
        String priceStr = etProductPrice.getText().toString().trim();
        String stockStr = etProductStock.getText().toString().trim();
        String sku = etProductSku.getText().toString().trim();

        // Validasi input
        if (TextUtils.isEmpty(name)) {
            etProductName.setError("Nama produk tidak boleh kosong");
            return;
        }

        if (TextUtils.isEmpty(priceStr) || !priceStr.matches("\\d+(\\.\\d+)?")) {
            etProductPrice.setError("Harga harus berupa angka");
            return;
        }

        if (TextUtils.isEmpty(stockStr) || !stockStr.matches("\\d+")) {
            etProductStock.setError("Stok harus berupa angka");
            return;
        }

        double price = Double.parseDouble(priceStr);
        int stock = Integer.parseInt(stockStr);

        // Validasi category
        if (spinnerCategory.getSelectedItem() == null) {
            Toast.makeText(this, "Pilih kategori terlebih dahulu", Toast.LENGTH_SHORT).show();
            return;
        }
        Category selectedCategory = (Category) spinnerCategory.getSelectedItem();

        // Jika ada gambar yang dipilih dari perangkat, upload dulu ke Supabase Storage
        if (selectedImageBytes != null && uploadedImageUrl == null) {
            btnSave.setEnabled(false);
            SupabaseHelper.getInstance().uploadImageToStorage(selectedImageBytes, selectedFileName, new SupabaseHelper.SimpleCallback() {
                @Override
                public void onSuccess(String url) {
                    runOnUiThread(() -> {
                        uploadedImageUrl = url;
                        proceedSaveProduct(name, price, stock);
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        btnSave.setEnabled(true);
                        Toast.makeText(AddProductActivity.this, "Upload gambar gagal: " + error, Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            proceedSaveProduct(name, price, stock);
        }
    }

    private void proceedSaveProduct(String name, double price, int stock) {
        // Buat objek produk
        IceCreamProduct product;
        String imageUrl = uploadedImageUrl;
        String sku = etProductSku.getText().toString().trim();
        Category selectedCategory = (Category) spinnerCategory.getSelectedItem();
        
        // Jika edit mode dan tidak ada upload baru, pertahankan URL lama
        if (isEditMode && imageUrl == null && editProduct != null) {
            imageUrl = editProduct.getImageUrl();
        }
        if (isEditMode) {
            product = new IceCreamProduct(
                    editProduct.getId(), name, price, stock, selectedImageResId, imageUrl);
        } else {
            String productId = UUID.randomUUID().toString();
            product = new IceCreamProduct(
                    productId, name, price, stock, selectedImageResId, imageUrl);
        }
        
        // Set category and SKU
        if (selectedCategory != null) {
            product.setCategoryId(selectedCategory.getId());
        }
        if (sku != null && !sku.isEmpty()) {
            product.setSku(sku);
        }

        // Simpan ke Supabase
        btnSave.setEnabled(false);
        if (isEditMode) {
            // Update produk
            SupabaseHelper.getInstance().updateProduct(product, new SupabaseHelper.DatabaseCallback() {
                @Override
                public void onSuccess(String id) {
                    runOnUiThread(() -> {
                        Toast.makeText(AddProductActivity.this, "Produk berhasil diperbarui", Toast.LENGTH_SHORT).show();
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("edit_product", product);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        btnSave.setEnabled(true);
                        Toast.makeText(AddProductActivity.this, "Gagal memperbarui produk: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        } else {
            // Tambah produk baru
            SupabaseHelper.getInstance().addProduct(product, new SupabaseHelper.DatabaseCallback() {
                @Override
                public void onSuccess(String id) {
                    runOnUiThread(() -> {
                        Toast.makeText(AddProductActivity.this, "Produk berhasil ditambahkan", Toast.LENGTH_SHORT).show();
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("new_product", product);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        btnSave.setEnabled(true);
                        Toast.makeText(AddProductActivity.this, "Gagal menambahkan produk: " + error, Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
    }
}