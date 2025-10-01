package com.example.easycoff.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.easycoff.R;
import com.example.easycoff.product.Transaction;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TransactionAdapter extends RecyclerView.Adapter<TransactionAdapter.VH> {
    private final List<Transaction> items = new ArrayList<>();
    private final NumberFormat fmt = NumberFormat.getCurrencyInstance(new Locale("in","ID"));
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("dd MMM yyyy HH:mm", new Locale("in","ID"));

    public TransactionAdapter() {
        fmt.setMaximumFractionDigits(0);
    }

    public void setData(List<Transaction> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_transaction, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Transaction t = items.get(position);
        // Use ID or position as transaction number since invoice_no doesn't exist in schema
        String txId = t.getId() != null ? t.getId().substring(0, Math.min(8, t.getId().length())) : String.valueOf(position + 1);
        h.tvId.setText("TRX-" + txId);
        h.tvCustomer.setText(t.getCustomerName() != null ? t.getCustomerName() : "Walk-in Customer");
        h.tvTotal.setText(fmt.format(t.getTotal()));
        h.tvPaid.setText(fmt.format(t.getAmountPaid()));
        h.tvChange.setText(fmt.format(t.getChange()));
        
        // Format timestamp
        if (t.getTimestamp() != null && !t.getTimestamp().isEmpty()) {
            try {
                // Parse ISO timestamp from Supabase
                SimpleDateFormat isoFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                Date date = isoFmt.parse(t.getTimestamp());
                if (date != null) {
                    h.tvDate.setText(dateFmt.format(date));
                } else {
                    h.tvDate.setText(t.getTimestamp());
                }
            } catch (Exception e) {
                h.tvDate.setText(t.getTimestamp());
            }
        } else {
            h.tvDate.setText("-");
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvId, tvDate, tvCustomer, tvTotal, tvPaid, tvChange;
        VH(@NonNull View itemView) {
            super(itemView);
            tvId = itemView.findViewById(R.id.tvTransactionId);
            tvDate = itemView.findViewById(R.id.tvTransactionDate);
            tvCustomer = itemView.findViewById(R.id.tvCustomerName);
            tvTotal = itemView.findViewById(R.id.tvTransactionTotal);
            tvPaid = itemView.findViewById(R.id.tvAmountPaid);
            tvChange = itemView.findViewById(R.id.tvChange);
        }
    }
}
