package com.example.easycoff.activity;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.easycoff.R;
import com.example.easycoff.product.Transaction;
import com.example.easycoff.supabase.SupabaseHelper;
import com.example.easycoff.ui.TransactionAdapter;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class HistoryActivity extends AppCompatActivity {
    private RecyclerView rv;
    private TransactionAdapter adapter;
    private SupabaseHelper supabase;
    private TextView tvTotalTransactions, tvTotalSales;
    private final NumberFormat fmt = NumberFormat.getCurrencyInstance(new Locale("in","ID"));

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_history);
        supabase = SupabaseHelper.getInstance();

        Toolbar toolbar = findViewById(R.id.toolbarHistory);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvTotalTransactions = findViewById(R.id.tvTotalTransactions);
        tvTotalSales = findViewById(R.id.tvTotalSales);
        fmt.setMaximumFractionDigits(0);

        rv = findViewById(R.id.rvHistory);
        adapter = new TransactionAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        loadTransactions();
    }

    private void loadTransactions() {
        supabase.loadTransactions(new SupabaseHelper.TransactionsCallback() {
            @Override
            public void onSuccess(List<Transaction> transactions) {
                runOnUiThread(() -> {
                    adapter.setData(transactions);
                    updateSummary(transactions);
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(HistoryActivity.this, error, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void updateSummary(List<Transaction> transactions) {
        int totalCount = transactions != null ? transactions.size() : 0;
        double totalSales = 0;
        
        if (transactions != null) {
            for (Transaction t : transactions) {
                totalSales += t.getTotal();
            }
        }
        
        tvTotalTransactions.setText(String.valueOf(totalCount));
        tvTotalSales.setText(fmt.format(totalSales));
    }
}
