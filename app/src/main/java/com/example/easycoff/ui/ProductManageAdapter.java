package com.example.easycoff.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.easycoff.R;
import com.example.easycoff.product.EasyCoffProduct;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class ProductManageAdapter extends RecyclerView.Adapter<ProductManageAdapter.VH> {
    public interface Listener {
        void onEdit(int position);
        void onDelete(int position);
    }

    private final List<EasyCoffProduct> items;
    private final Listener listener;
    private final NumberFormat fmt = NumberFormat.getCurrencyInstance(new Locale("in","ID"));

    public ProductManageAdapter(List<EasyCoffProduct> items, Listener listener) {
        this.items = items;
        this.listener = listener;
        fmt.setMaximumFractionDigits(0);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product_manage, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        EasyCoffProduct p = items.get(position);
        h.tvName.setText(p.getName());
        String meta = fmt.format(p.getPrice()) + " • Stok " + p.getStock();
        h.tvMeta.setText(meta);
        h.btnEdit.setOnClickListener(v -> { if (listener != null) listener.onEdit(h.getBindingAdapterPosition()); });
        h.btnDelete.setOnClickListener(v -> { if (listener != null) listener.onDelete(h.getBindingAdapterPosition()); });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvMeta; Button btnEdit, btnDelete;
        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvMeta = itemView.findViewById(R.id.tvMeta);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
