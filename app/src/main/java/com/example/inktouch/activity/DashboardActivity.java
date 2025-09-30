package com.example.inktouch.activity;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.inktouch.R;
import com.example.inktouch.product.Transaction;
import com.example.inktouch.supabase.SupabaseHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardActivity extends AppCompatActivity {
    private TextView tvAdminName, tvAdminEmail;
    private TextView tvTotalSales, tvTotalTransactions, tvBestProduct;
    private ListView lvSalesHistory;
    private ArrayAdapter<String> salesHistoryAdapter;
    private List<String> salesHistoryList = new ArrayList<>();
    
    private SupabaseHelper supabaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        setTitle("📊 Dashboard");

        supabaseHelper = SupabaseHelper.getInstance();

        // Initialize views
        tvAdminName = findViewById(R.id.tv_admin_name);
        tvAdminEmail = findViewById(R.id.tv_admin_email);
        tvTotalSales = findViewById(R.id.tv_total_sales);
        tvTotalTransactions = findViewById(R.id.tv_total_transactions);
        tvBestProduct = findViewById(R.id.tv_best_product);
        lvSalesHistory = findViewById(R.id.lv_sales_history);
        Button btnBack = findViewById(R.id.btn_back);

        // Setup ListView
        salesHistoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, salesHistoryList);
        lvSalesHistory.setAdapter(salesHistoryAdapter);

        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Load data
        loadAdminInfo();
        loadDashboardData();
    }

    private void loadAdminInfo() {
        String email = supabaseHelper.getUserEmail();
        if (email != null) {
            tvAdminEmail.setText("Email: " + email);
            // Extract name from email (before @)
            String name = email.split("@")[0];
            tvAdminName.setText("Nama: " + name);
        } else {
            tvAdminName.setText("Nama: Admin");
            tvAdminEmail.setText("Email: -");
        }
    }

    private void loadDashboardData() {
        supabaseHelper.loadTransactions(new SupabaseHelper.TransactionsCallback() {
            @Override
            public void onSuccess(List<Transaction> transactions) {
                runOnUiThread(() -> {
                    calculateStatistics(transactions);
                    calculateBestProduct(transactions);
                    calculateLast7DaysSales(transactions);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(DashboardActivity.this, 
                        "Gagal memuat data: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void calculateStatistics(List<Transaction> transactions) {
        double totalSales = 0;
        int totalTransactions = transactions.size();

        for (Transaction transaction : transactions) {
            totalSales += transaction.getTotal();
        }

        tvTotalSales.setText(String.format(Locale.getDefault(), "Rp %.0f", totalSales));
        tvTotalTransactions.setText(String.valueOf(totalTransactions));
    }

    private void calculateBestProduct(List<Transaction> transactions) {
        Map<String, Integer> productCount = new HashMap<>();
        Map<String, String> productNames = new HashMap<>();

        for (Transaction transaction : transactions) {
            if (transaction.getItems() != null) {
                for (com.example.inktouch.product.IceCreamProduct product : transaction.getItems()) {
                    String productId = product.getId();
                    String productName = product.getName();
                    
                    productCount.put(productId, productCount.getOrDefault(productId, 0) + 1);
                    productNames.put(productId, productName);
                }
            }
        }

        // Find best selling product
        String bestProductId = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : productCount.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                bestProductId = entry.getKey();
            }
        }

        if (bestProductId != null) {
            String bestProductName = productNames.get(bestProductId);
            tvBestProduct.setText(String.format(Locale.getDefault(), 
                "%s (%d terjual)", bestProductName, maxCount));
        } else {
            tvBestProduct.setText("Belum ada data");
        }
    }

    private void calculateLast7DaysSales(List<Transaction> transactions) {
        // Get last 7 days
        Calendar calendar = Calendar.getInstance();
        Map<String, Double> dailySales = new HashMap<>();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

        // Initialize last 7 days with 0
        for (int i = 6; i >= 0; i--) {
            calendar.setTime(new Date());
            calendar.add(Calendar.DAY_OF_YEAR, -i);
            String dateKey = sdf.format(calendar.getTime());
            dailySales.put(dateKey, 0.0);
        }

        // Calculate sales per day
        for (Transaction transaction : transactions) {
            Date transactionDate = transaction.getTimestamp();
            if (transactionDate != null) {
                String dateKey = sdf.format(transactionDate);
                
                // Only count if within last 7 days
                if (dailySales.containsKey(dateKey)) {
                    double currentTotal = dailySales.get(dateKey);
                    dailySales.put(dateKey, currentTotal + transaction.getTotal());
                }
            }
        }

        // Display in ListView
        salesHistoryList.clear();
        calendar.setTime(new Date());
        for (int i = 6; i >= 0; i--) {
            calendar.setTime(new Date());
            calendar.add(Calendar.DAY_OF_YEAR, -i);
            String dateKey = sdf.format(calendar.getTime());
            double sales = dailySales.get(dateKey);
            
            String dayName = getDayName(calendar);
            salesHistoryList.add(String.format(Locale.getDefault(), 
                "%s (%s)\nRp %.0f", dayName, dateKey, sales));
        }
        salesHistoryAdapter.notifyDataSetChanged();
    }

    private String getDayName(Calendar calendar) {
        SimpleDateFormat dayFormat = new SimpleDateFormat("EEEE", new Locale("id", "ID"));
        return dayFormat.format(calendar.getTime());
    }
}
