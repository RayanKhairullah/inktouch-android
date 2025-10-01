package com.example.easycoff.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.easycoff.R;
import com.example.easycoff.product.EasyCoffProduct;
import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
    public interface Listener {
        void onAdd(EasyCoffProduct product);
    }

    private final Context context;
    private final Listener listener;
    private final List<EasyCoffProduct> items = new ArrayList<>();
    private final NumberFormat fmt = NumberFormat.getCurrencyInstance(new Locale("in","ID"));

    public ProductAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        fmt.setMaximumFractionDigits(0);
    }

    public void setData(List<EasyCoffProduct> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_product, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        EasyCoffProduct p = items.get(position);
        h.tvName.setText(p.getName());
        h.tvPrice.setText(fmt.format(p.getPrice()));
        h.tvStock.setText("Stok: " + p.getStock());
        
        // Set stock color based on availability
        if (p.getStock() <= 0) {
            h.tvStock.setTextColor(context.getResources().getColor(android.R.color.holo_red_dark));
            h.btnAdd.setEnabled(false);
            h.btnAdd.setText("Habis");
        } else if (p.getStock() < 10) {
            h.tvStock.setTextColor(context.getResources().getColor(android.R.color.holo_orange_dark));
            h.btnAdd.setEnabled(true);
            h.btnAdd.setText("Tambah");
        } else {
            h.tvStock.setTextColor(context.getResources().getColor(android.R.color.darker_gray));
            h.btnAdd.setEnabled(true);
            h.btnAdd.setText("Tambah");
        }
        
        String img = p.getImageUrl();
        Glide.with(context)
                .load(img != null && !img.isEmpty() ? img : R.drawable.ic_launcher_foreground)
                .centerCrop()
                .into(h.img);
        h.btnAdd.setOnClickListener(v -> {
            if (listener != null) listener.onAdd(p);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        ImageView img; TextView tvName; TextView tvPrice; TextView tvStock; MaterialButton btnAdd;
        VH(@NonNull View itemView) {
            super(itemView);
            img = itemView.findViewById(R.id.imgProduct);
            tvName = itemView.findViewById(R.id.tvName);
            tvPrice = itemView.findViewById(R.id.tvPrice);
            tvStock = itemView.findViewById(R.id.tvStock);
            btnAdd = itemView.findViewById(R.id.btnAdd);
        }
    }
}
