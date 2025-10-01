package com.example.easycoff.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.easycoff.R;
import com.example.easycoff.product.CartItem;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.VH> {
    public interface Listener {
        void onIncrease(int position);
        void onDecrease(int position);
    }

    private final List<CartItem> items = new ArrayList<>();
    private final NumberFormat fmt = NumberFormat.getCurrencyInstance(new Locale("in","ID"));
    private final Listener listener;

    public CartAdapter(Listener listener) {
        this.listener = listener;
        fmt.setMaximumFractionDigits(0);
    }

    public void setData(List<CartItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public List<CartItem> getItems() { return items; }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cart, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        CartItem ci = items.get(position);
        h.tvName.setText(ci.getProduct().getName());
        h.tvQty.setText(ci.getQuantity() + "x");
        h.tvPrice.setText(fmt.format(ci.getLineTotal()));
        h.btnPlus.setOnClickListener(v -> { if (listener != null) listener.onIncrease(h.getBindingAdapterPosition()); });
        h.btnMinus.setOnClickListener(v -> { if (listener != null) listener.onDecrease(h.getBindingAdapterPosition()); });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName, tvQty, tvPrice; Button btnPlus, btnMinus;
        VH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvCartName);
            tvQty = itemView.findViewById(R.id.tvCartQty);
            tvPrice = itemView.findViewById(R.id.tvCartPrice);
            btnPlus = itemView.findViewById(R.id.btnPlus);
            btnMinus = itemView.findViewById(R.id.btnMinus);
        }
    }
}
