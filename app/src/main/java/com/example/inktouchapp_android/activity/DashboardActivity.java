package com.example.inktouchapp_android.activity;

import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.example.inktouchapp_android.R;
import com.example.inktouchapp_android.product.Transaction;
import com.example.inktouchapp_android.supabase.SupabaseHelper;

import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DashboardActivity extends AppCompatActivity {
    private TextView tvAdminName, tvAdminEmail;
    private TextView tvTotalSales, tvTotalTransactions;
    private Button btnDownload;
    private List<Transaction> latestTransactions;
    private ActivityResultLauncher<Intent> createDocumentLauncher;
    
    private SupabaseHelper supabaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        setTitle("Dashboard Admin");

        supabaseHelper = SupabaseHelper.getInstance();

        // Initialize views
        tvAdminName = findViewById(R.id.tv_admin_name);
        tvAdminEmail = findViewById(R.id.tv_admin_email);
        tvTotalSales = findViewById(R.id.tv_total_sales);
        tvTotalTransactions = findViewById(R.id.tv_total_transactions);
        Button btnBack = findViewById(R.id.btn_back);
        btnDownload = findViewById(R.id.btn_download_report);


        // Back button
        btnBack.setOnClickListener(v -> finish());

        // Register document creator for XLSX export
        createDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            exportToXlsx(uri);
                        }
                    }
                }
        );

        // Download report button
        if (btnDownload != null) {
            btnDownload.setOnClickListener(v -> {
                String suggested = "laporan-penjualan-" + new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()) + ".xlsx";
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                intent.putExtra(Intent.EXTRA_TITLE, suggested);
                createDocumentLauncher.launch(intent);
            });
        }

        // Load data (totals only)
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
                    latestTransactions = transactions;
                    calculateStatistics(transactions);
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

    private void exportToXlsx(Uri uri) {
        if (latestTransactions == null) {
            Toast.makeText(this, "Data transaksi belum siap", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            OutputStream os = getContentResolver().openOutputStream(uri);
            if (os == null) {
                Toast.makeText(this, "Gagal membuka lokasi file", Toast.LENGTH_SHORT).show();
                return;
            }

            // FastExcel
            org.dhatim.fastexcel.Workbook wb = new org.dhatim.fastexcel.Workbook(os, "Inktouch", "1.0");
            org.dhatim.fastexcel.Worksheet ws = wb.newWorksheet("Laporan");

            int r = 0;
            // Header
            ws.value(r, 0, "Tanggal");
            ws.value(r, 1, "Invoice");
            ws.value(r, 2, "Pelanggan");
            ws.value(r, 3, "Total");
            ws.value(r, 4, "Jumlah Item");
            r++;

            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
            for (Transaction t : latestTransactions) {
                String dateStr = t.getTimestamp() != null ? sdf.format(t.getTimestamp()) : "-";
                String invoice = t.getInvoiceNo() != null ? t.getInvoiceNo() : "-";
                String customer = t.getCustomerName() != null ? t.getCustomerName() : "Walk-in Customer";
                int itemCount = (t.getItems() != null) ? t.getItems().size() : 0;

                ws.value(r, 0, dateStr);
                ws.value(r, 1, invoice);
                ws.value(r, 2, customer);
                ws.value(r, 3, t.getTotal());
                ws.value(r, 4, itemCount);
                r++;
            }

            wb.finish();
            os.close();
            Toast.makeText(this, "Laporan disimpan", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Gagal menyimpan: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
