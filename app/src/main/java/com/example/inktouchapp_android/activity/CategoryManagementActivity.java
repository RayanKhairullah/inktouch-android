package com.example.inktouchapp_android.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.inktouchapp_android.R;
import com.example.inktouchapp_android.product.Category;
import com.example.inktouchapp_android.supabase.SupabaseHelper;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CategoryManagementActivity extends AppCompatActivity {
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private List<Category> categories = new ArrayList<>();
    private List<Category> allCategories = new ArrayList<>(); // Original list for search
    private List<String> categoryDisplayList = new ArrayList<>();
    private SupabaseHelper supabaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_management);

        setTitle("Manajemen Kategori");

        supabaseHelper = SupabaseHelper.getInstance();

        listView = findViewById(R.id.lv_categories);
        FloatingActionButton fabAdd = findViewById(R.id.fab_add_category);
        Button btnBack = findViewById(R.id.btn_back);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, categoryDisplayList);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            Category category = categories.get(position);
            showEditCategoryDialog(category);
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            Category category = categories.get(position);
            showDeleteConfirmation(category);
            return true;
        });

        fabAdd.setOnClickListener(v -> showAddCategoryDialog());
        
        btnBack.setOnClickListener(v -> finish());

        // Setup search functionality
        TextInputEditText etSearchCategory = findViewById(R.id.et_search_category);
        etSearchCategory.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterCategories(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadCategories();
    }

    private void loadCategories() {
        supabaseHelper.loadCategories(new SupabaseHelper.CategoriesCallback() {
            @Override
            public void onSuccess(List<Category> loadedCategories) {
                runOnUiThread(() -> {
                    allCategories.clear();
                    allCategories.addAll(loadedCategories);
                    categories.clear();
                    categories.addAll(loadedCategories);
                    updateDisplayList();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(CategoryManagementActivity.this,
                        "Gagal memuat kategori: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void filterCategories(String query) {
        if (query.isEmpty()) {
            categories.clear();
            categories.addAll(allCategories);
        } else {
            List<Category> filteredList = new ArrayList<>();
            for (Category category : allCategories) {
                if (category.getName().toLowerCase().contains(query.toLowerCase()) ||
                    (category.getDescription() != null && category.getDescription().toLowerCase().contains(query.toLowerCase()))) {
                    filteredList.add(category);
                }
            }
            categories.clear();
            categories.addAll(filteredList);
        }
        updateDisplayList();
    }

    private void updateDisplayList() {
        categoryDisplayList.clear();
        for (Category category : categories) {
            String display = category.getName();
            if (category.getDescription() != null && !category.getDescription().isEmpty()) {
                display += "\n" + category.getDescription();
            }
            categoryDisplayList.add(display);
        }
        adapter.notifyDataSetChanged();
    }

    private void showAddCategoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("➕ Tambah Kategori");

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_category, null);
        EditText etName = dialogView.findViewById(R.id.et_category_name);
        EditText etDescription = dialogView.findViewById(R.id.et_category_description);

        builder.setView(dialogView);
        builder.setNegativeButton("Batal", null);
        builder.setPositiveButton("Simpan", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String description = etDescription.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, "Nama kategori tidak boleh kosong", Toast.LENGTH_SHORT).show();
                return;
            }

            Category category = new Category(UUID.randomUUID().toString(), name, description);
            supabaseHelper.addCategory(category, new SupabaseHelper.DatabaseCallback() {
                @Override
                public void onSuccess(String id) {
                    runOnUiThread(() -> {
                        Toast.makeText(CategoryManagementActivity.this,
                                "Kategori berhasil ditambahkan", Toast.LENGTH_SHORT).show();
                        loadCategories();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(CategoryManagementActivity.this,
                            "Gagal menambah kategori: " + error, Toast.LENGTH_SHORT).show());
                }
            });
        });

        builder.show();
    }

    private void showEditCategoryDialog(Category category) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("✏️ Edit Kategori");

        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_category, null);
        EditText etName = dialogView.findViewById(R.id.et_category_name);
        EditText etDescription = dialogView.findViewById(R.id.et_category_description);

        etName.setText(category.getName());
        etDescription.setText(category.getDescription());

        builder.setView(dialogView);
        builder.setNegativeButton("Batal", null);
        builder.setPositiveButton("Simpan", (dialog, which) -> {
            String name = etName.getText().toString().trim();
            String description = etDescription.getText().toString().trim();

            if (TextUtils.isEmpty(name)) {
                Toast.makeText(this, "Nama kategori tidak boleh kosong", Toast.LENGTH_SHORT).show();
                return;
            }

            category.setName(name);
            category.setDescription(description);

            supabaseHelper.updateCategory(category, new SupabaseHelper.DatabaseCallback() {
                @Override
                public void onSuccess(String id) {
                    runOnUiThread(() -> {
                        Toast.makeText(CategoryManagementActivity.this,
                                "Kategori berhasil diperbarui", Toast.LENGTH_SHORT).show();
                        loadCategories();
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(CategoryManagementActivity.this,
                            "Gagal memperbarui kategori: " + error, Toast.LENGTH_SHORT).show());
                }
            });
        });

        builder.show();
    }

    private void showDeleteConfirmation(Category category) {
        new AlertDialog.Builder(this)
                .setTitle("🗑️ Hapus Kategori")
                .setMessage("Yakin ingin menghapus kategori \"" + category.getName() + "\"?")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Hapus", (dialog, which) -> {
                    supabaseHelper.deleteCategory(category.getId(), new SupabaseHelper.DatabaseCallback() {
                        @Override
                        public void onSuccess(String id) {
                            runOnUiThread(() -> {
                                Toast.makeText(CategoryManagementActivity.this,
                                        "Kategori berhasil dihapus", Toast.LENGTH_SHORT).show();
                                loadCategories();
                            });
                        }

                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> Toast.makeText(CategoryManagementActivity.this,
                                    "Gagal menghapus kategori: " + error, Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .show();
    }
}
