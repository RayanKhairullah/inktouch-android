package com.example.inktouchapp_android.activity;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputEditText;

import com.example.inktouchapp_android.R;
import com.example.inktouchapp_android.supabase.SupabaseHelper;
import com.example.inktouchapp_android.product.Transaction;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {
    private List<Transaction> transactionHistory;
    private List<Transaction> allTransactions = new ArrayList<>(); // Original list for search
    private ArrayAdapter<Transaction> historyAdapter;
    private boolean isDeletingMode = false;
    private List<Transaction> selectedTransactions = new ArrayList<>();
    private TextView tvTotalHistory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        tvTotalHistory = findViewById(R.id.tv_total_history);

        // Ambil riwayat transaksi dari intent (fallback awal)
        transactionHistory = getIntent().getParcelableArrayListExtra("transactions");
        if (transactionHistory == null) {
            transactionHistory = new ArrayList<>();
        }

        // Setup ListView
        ListView historyListView = findViewById(R.id.history_list_view);
        historyAdapter = new ArrayAdapter<Transaction>(this,
                R.layout.transaction_item, transactionHistory) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.transaction_item, parent, false);
                }

                Transaction transaction = getItem(position);
                TextView tvTransaction = convertView.findViewById(R.id.tv_transaction);
                TextView tvSelect = convertView.findViewById(R.id.tv_select);

                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
                Date ts = transaction.getTimestamp();
                String dateString = ts != null ? sdf.format(ts) : "-";
                tvTransaction.setText(String.format("Total: Rp %.0f | %s", transaction.getTotal(), dateString));

                tvSelect.setVisibility(isDeletingMode ? View.VISIBLE : View.GONE);
                tvSelect.setText(selectedTransactions.contains(transaction) ? "✓" : "○");

                return convertView;
            }
        };
        historyListView.setAdapter(historyAdapter);

        // Tombol kembali
        Button btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> {
            if (isDeletingMode) {
                isDeletingMode = false;
                selectedTransactions.clear();
                historyAdapter.notifyDataSetChanged();
                updateDeleteUI();
            } else {
                finish();
            }
        });

        // Tombol hapus
        Button btnDelete = findViewById(R.id.btn_delete);
        btnDelete.setOnClickListener(v -> {
            if (isDeletingMode) {
                if (selectedTransactions.isEmpty()) {
                    showDeleteConfirmationDialog();
                } else {
                    deleteSelectedTransactions();
                }
            } else {
                isDeletingMode = true;
                selectedTransactions.clear();
                historyAdapter.notifyDataSetChanged();
                updateDeleteUI();
            }
        });

        // Klik item list
        historyListView.setOnItemClickListener((parent, view, position, id) -> {
            if (isDeletingMode) {
                Transaction transaction = transactionHistory.get(position);
                if (selectedTransactions.contains(transaction)) {
                    selectedTransactions.remove(transaction);
                } else {
                    selectedTransactions.add(transaction);
                }
                historyAdapter.notifyDataSetChanged();
                updateDeleteUI();
            } else {
                showTransactionDetails(transactionHistory.get(position));
            }
        });

        // Setup search functionality
        TextInputEditText etSearchHistory = findViewById(R.id.et_search_history);
        etSearchHistory.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterTransactions(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Refresh data dari Supabase
        refreshTransactions();

        updateDeleteUI();
        updateTotalHistory();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTransactions();
    }

    private void refreshTransactions() {
        SupabaseHelper.getInstance().loadTransactions(new SupabaseHelper.TransactionsCallback() {
            @Override
            public void onSuccess(List<Transaction> transactions) {
                runOnUiThread(() -> {
                    allTransactions.clear();
                    allTransactions.addAll(transactions);
                    transactionHistory.clear();
                    transactionHistory.addAll(transactions);
                    selectedTransactions.clear();
                    isDeletingMode = false;
                    historyAdapter.notifyDataSetChanged();
                    updateDeleteUI();
                    updateTotalHistory();
                });
            }

            @Override
            public void onError(String error) {
                // Biarkan pakai data intent
            }
        });
    }

    private void filterTransactions(String query) {
        if (query.isEmpty()) {
            transactionHistory.clear();
            transactionHistory.addAll(allTransactions);
        } else {
            List<Transaction> filteredList = new ArrayList<>();
            for (Transaction transaction : allTransactions) {
                String invoiceNo = transaction.getInvoiceNo();
                String customerName = transaction.getCustomerName();
                String date = transaction.getFormattedDate();
                String total = String.format(Locale.getDefault(), "%.0f", transaction.getTotal());
                
                if ((invoiceNo != null && invoiceNo.toLowerCase().contains(query.toLowerCase())) ||
                    (customerName != null && customerName.toLowerCase().contains(query.toLowerCase())) ||
                    date.contains(query) ||
                    total.contains(query)) {
                    filteredList.add(transaction);
                }
            }
            transactionHistory.clear();
            transactionHistory.addAll(filteredList);
        }
        historyAdapter.notifyDataSetChanged();
        updateTotalHistory();
    }

    private void updateDeleteUI() {
        Button btnDelete = findViewById(R.id.btn_delete);
        Button btnBack = findViewById(R.id.btn_back);
        if (btnDelete == null || btnBack == null) return;

        if (isDeletingMode) {
            btnBack.setText("Batalkan");
            int count = selectedTransactions.size();
            if (count > 0) {
                btnDelete.setText("Hapus (" + count + ")");
            } else {
                btnDelete.setText("Hapus Semua");
            }
        } else {
            btnBack.setText("Kembali");
            btnDelete.setText("Hapus");
        }
    }

    private void showTransactionDetails(Transaction transaction) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🛒 Detail Transaksi");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_transaction_details, null);
        builder.setView(dialogView);

        TextView tvInvoiceNo = dialogView.findViewById(R.id.tv_invoice_no);
        TextView tvCustomerName = dialogView.findViewById(R.id.tv_customer_name);
        TextView tvDate = dialogView.findViewById(R.id.tv_date);
        TextView tvTotal = dialogView.findViewById(R.id.tv_total);
        TextView tvAmountPaid = dialogView.findViewById(R.id.tv_amount_paid);
        TextView tvChange = dialogView.findViewById(R.id.tv_change);
        ListView lvItems = dialogView.findViewById(R.id.lv_items);

        // Set invoice number
        String invoiceNo = transaction.getInvoiceNo();
        tvInvoiceNo.setText(invoiceNo != null && !invoiceNo.isEmpty() ? invoiceNo : "-");

        // Set customer name
        String customerName = transaction.getCustomerName();
        tvCustomerName.setText(customerName != null && !customerName.isEmpty() ? customerName : "Walk-in Customer");

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault());
        Date ts = transaction.getTimestamp();
        tvDate.setText(ts != null ? sdf.format(ts) : "-");
        tvTotal.setText(String.format("Total: Rp %.0f", transaction.getTotal()));
        tvAmountPaid.setText(String.format(Locale.getDefault(), "Rp %.0f", transaction.getAmountPaid()));
        tvChange.setText(String.format(Locale.getDefault(), "Rp %.0f", transaction.getChange()));

        ArrayList<String> displayItems = new ArrayList<>();
        ArrayAdapter<String> itemAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, displayItems);
        lvItems.setAdapter(itemAdapter);

        displayItems.add("Memuat item...");
        itemAdapter.notifyDataSetChanged();

        SupabaseHelper.getInstance().loadTransactionItems(transaction.getId(), new SupabaseHelper.ItemsCallback() {
            @Override
            public void onSuccess(List<String> items) {
                runOnUiThread(() -> {
                    displayItems.clear();
                    displayItems.addAll(items);
                    if (displayItems.isEmpty()) {
                        displayItems.add("Tidak ada item");
                    }
                    itemAdapter.notifyDataSetChanged();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    displayItems.clear();
                    displayItems.add("Gagal memuat item: " + error);
                    itemAdapter.notifyDataSetChanged();
                });
            }
        });

        builder.setPositiveButton("Tutup", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) {
                positive.setTextColor(ContextCompat.getColor(HistoryActivity.this, R.color.on_surface));
            }
        });
        dialog.show();
    }

    private void showDeleteConfirmationDialog() {
        historyAdapter.notifyDataSetChanged();
        setResult(RESULT_OK);
        updateDeleteUI();
        updateTotalHistory();

        new AlertDialog.Builder(this)
                .setTitle("❌ Hapus Semua Riwayat")
                .setMessage("Tidak dapat menghapus riwayat transaksi dari database online.\n\n" +
                        "Riwayat transaksi disimpan di database untuk keperluan arsip dan laporan.\n\n" +
                        "Apakah Anda yakin ingin melanjutkan?")
                .setPositiveButton("Ya", (dialog, which) -> {
                    SupabaseHelper.getInstance().deleteAllTransactionsForUser(new SupabaseHelper.DatabaseCallback() {
                        @Override
                        public void onSuccess(String id) {
                            transactionHistory.clear();
                            selectedTransactions.clear();
                            isDeletingMode = false;
                            historyAdapter.notifyDataSetChanged();
                            setResult(RESULT_OK);
                            updateDeleteUI();
                            updateTotalHistory();
                            finish();
                        }

                        @Override
                        public void onError(String error) {
                            new AlertDialog.Builder(HistoryActivity.this)
                                    .setTitle("Gagal Menghapus")
                                    .setMessage(error)
                                    .setPositiveButton("OK", null)
                                    .show();
                        }
                    });
                })
                .setNegativeButton("Tidak", null)
                .show();
    }

    private void deleteSelectedTransactions() {
        historyAdapter.notifyDataSetChanged();
        setResult(RESULT_OK);
        updateDeleteUI();
        updateTotalHistory();

        List<String> ids = new ArrayList<>();
        for (Transaction t : selectedTransactions) ids.add(t.getId());

        SupabaseHelper.getInstance().deleteTransactionsByIds(ids, new SupabaseHelper.DatabaseCallback() {
            @Override
            public void onSuccess(String id) {
                transactionHistory.removeAll(selectedTransactions);
                selectedTransactions.clear();
                isDeletingMode = false;
                historyAdapter.notifyDataSetChanged();
                setResult(RESULT_OK);
                updateDeleteUI();
                updateTotalHistory();
            }

            @Override
            public void onError(String error) {
                new AlertDialog.Builder(HistoryActivity.this)
                        .setTitle("Gagal Menghapus")
                        .setMessage(error)
                        .setPositiveButton("OK", null)
                        .show();
            }
        });
    }

    private void updateTotalHistory() {
        if (transactionHistory == null) return;
        double totalAll = 0;
        for (Transaction t : transactionHistory) {
            totalAll += t.getTotal();
        }
        if (tvTotalHistory != null) {
            tvTotalHistory.setText(String.format(Locale.getDefault(), "Total: Rp %.0f", totalAll));
        }
    }

    @Override
    public void onBackPressed() {
        if (isDeletingMode) {
            isDeletingMode = false;
            selectedTransactions.clear();
            Button btnDelete = findViewById(R.id.btn_delete);
            btnDelete.setText("Hapus");
            Button btnBack = findViewById(R.id.btn_back);
            btnBack.setText("Kembali");
            historyAdapter.notifyDataSetChanged();
        } else {
            super.onBackPressed();
        }
    }
}
