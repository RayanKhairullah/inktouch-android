-- =========================================
-- EASYCOFF POS - DATABASE MIGRATION FINAL
-- =========================================
-- Aplikasi POS Cafe dengan Supabase
-- Tanggal: 2025-09-30
-- =========================================

-- =========================================
-- 0) Prasyarat UUID
-- =========================================
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- =========================================
-- 1) Tabel Utama
-- =========================================

-- Products
CREATE TABLE IF NOT EXISTS public.products (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name TEXT NOT NULL,
  price DOUBLE PRECISION NOT NULL CHECK (price >= 0),
  stock INTEGER NOT NULL CHECK (stock >= 0),
  image_url TEXT,                          -- Kategori: Coffee, Food, Snack, Beverage, dll
  description TEXT,                            -- Deskripsi produk
  is_active BOOLEAN DEFAULT true,              -- Soft delete flag
  sku TEXT UNIQUE,                             -- Stock Keeping Unit (barcode/kode unik)
  user_id UUID NOT NULL REFERENCES auth.users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Transactions (header)
CREATE TABLE IF NOT EXISTS public.transactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  total DOUBLE PRECISION NOT NULL CHECK (total >= 0),
  amount_paid DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK (amount_paid >= 0),
  "change" DOUBLE PRECISION NOT NULL DEFAULT 0 CHECK ("change" >= 0),
  discount_amount DOUBLE PRECISION DEFAULT 0 CHECK (discount_amount >= 0),
  tax_amount DOUBLE PRECISION DEFAULT 0 CHECK (tax_amount >= 0),
  customer_name TEXT NOT NULL DEFAULT 'Walk-in Customer',
  payment_method TEXT DEFAULT 'cash' CHECK (payment_method IN ('cash', 'card', 'qris', 'e-wallet')),
  status TEXT DEFAULT 'completed' CHECK (status IN ('pending', 'completed', 'cancelled', 'refunded')),
  notes TEXT,                                  -- Catatan khusus transaksi
  timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
  user_id UUID NOT NULL REFERENCES auth.users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Transaction items (detail)
CREATE TABLE IF NOT EXISTS public.transaction_items (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  transaction_id UUID NOT NULL REFERENCES public.transactions(id) ON DELETE CASCADE,
  product_id UUID NOT NULL REFERENCES public.products(id),
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  unit_price DOUBLE PRECISION NOT NULL CHECK (unit_price >= 0),
  discount_amount DOUBLE PRECISION DEFAULT 0 CHECK (discount_amount >= 0),
  notes TEXT,                                  -- Customization: "Extra shot", "Less sugar", dll
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- =========================================
-- 2) Trigger updated_at
-- =========================================
CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  new.updated_at = now();
  RETURN new;
END; $$;

DROP TRIGGER IF EXISTS trg_products_updated_at ON public.products;
CREATE TRIGGER trg_products_updated_at
BEFORE UPDATE ON public.products
FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- =========================================
-- 3) Trigger Auto-Update Stock
-- =========================================
CREATE OR REPLACE FUNCTION public.update_product_stock()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
  IF TG_OP = 'INSERT' THEN
    -- Kurangi stock saat item ditambahkan
    UPDATE public.products
    SET stock = stock - NEW.quantity
    WHERE id = NEW.product_id;
    
    -- Validasi stock tidak boleh negatif
    IF (SELECT stock FROM public.products WHERE id = NEW.product_id) < 0 THEN
      RAISE EXCEPTION 'Insufficient stock for product_id: %', NEW.product_id;
    END IF;
  ELSIF TG_OP = 'DELETE' THEN
    -- Kembalikan stock saat item dihapus
    UPDATE public.products
    SET stock = stock + OLD.quantity
    WHERE id = OLD.product_id;
  ELSIF TG_OP = 'UPDATE' THEN
    -- Update stock saat quantity berubah
    UPDATE public.products
    SET stock = stock + OLD.quantity - NEW.quantity
    WHERE id = NEW.product_id;
    
    IF (SELECT stock FROM public.products WHERE id = NEW.product_id) < 0 THEN
      RAISE EXCEPTION 'Insufficient stock for product_id: %', NEW.product_id;
    END IF;
  END IF;
  RETURN NEW;
END; $$;

DROP TRIGGER IF EXISTS trg_update_stock ON public.transaction_items;
CREATE TRIGGER trg_update_stock
AFTER INSERT OR DELETE OR UPDATE ON public.transaction_items
FOR EACH ROW EXECUTE FUNCTION public.update_product_stock();

-- =========================================
-- 4) Indeks untuk Performa
-- =========================================
CREATE INDEX IF NOT EXISTS idx_products_user ON public.products(user_id);
CREATE INDEX IF NOT EXISTS idx_products_active ON public.products(is_active);
CREATE INDEX IF NOT EXISTS idx_products_sku ON public.products(sku);

CREATE INDEX IF NOT EXISTS idx_transactions_user ON public.transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_transactions_timestamp ON public.transactions(timestamp);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON public.transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_payment ON public.transactions(payment_method);
CREATE INDEX IF NOT EXISTS idx_transactions_customer_name ON public.transactions(customer_name);

CREATE INDEX IF NOT EXISTS idx_items_tx ON public.transaction_items(transaction_id);
CREATE INDEX IF NOT EXISTS idx_items_product ON public.transaction_items(product_id);

-- =========================================
-- 5) RLS Enable
-- =========================================
ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transaction_items ENABLE ROW LEVEL SECURITY;

-- Bersihkan policy lama
DROP POLICY IF EXISTS products_policy ON public.products;
DROP POLICY IF EXISTS transactions_policy ON public.transactions;
DROP POLICY IF EXISTS transaction_items_policy ON public.transaction_items;

DROP POLICY IF EXISTS products_select ON public.products;
DROP POLICY IF EXISTS products_insert ON public.products;
DROP POLICY IF EXISTS products_update ON public.products;
DROP POLICY IF EXISTS products_delete ON public.products;

DROP POLICY IF EXISTS transactions_select ON public.transactions;
DROP POLICY IF EXISTS transactions_insert ON public.transactions;
DROP POLICY IF EXISTS transactions_update ON public.transactions;
DROP POLICY IF EXISTS transactions_delete ON public.transactions;

DROP POLICY IF EXISTS transaction_items_select ON public.transaction_items;
DROP POLICY IF EXISTS transaction_items_insert ON public.transaction_items;
DROP POLICY IF EXISTS transaction_items_update ON public.transaction_items;
DROP POLICY IF EXISTS transaction_items_delete ON public.transaction_items;

-- =========================================
-- 6) RLS Policies
-- =========================================

-- Products: hanya milik user
CREATE POLICY products_select ON public.products
FOR SELECT TO authenticated
USING (user_id = auth.uid());

CREATE POLICY products_insert ON public.products
FOR INSERT TO authenticated
WITH CHECK (user_id = auth.uid());

CREATE POLICY products_update ON public.products
FOR UPDATE TO authenticated
USING (user_id = auth.uid())
WITH CHECK (user_id = auth.uid());

CREATE POLICY products_delete ON public.products
FOR DELETE TO authenticated
USING (user_id = auth.uid());

-- Transactions: hanya milik user
CREATE POLICY transactions_select ON public.transactions
FOR SELECT TO authenticated
USING (user_id = auth.uid());

CREATE POLICY transactions_insert ON public.transactions
FOR INSERT TO authenticated
WITH CHECK (user_id = auth.uid());

CREATE POLICY transactions_update ON public.transactions
FOR UPDATE TO authenticated
USING (user_id = auth.uid())
WITH CHECK (user_id = auth.uid());

CREATE POLICY transactions_delete ON public.transactions
FOR DELETE TO authenticated
USING (user_id = auth.uid());

-- Transaction items: terkait transaksi milik user
CREATE POLICY transaction_items_select ON public.transaction_items
FOR SELECT TO authenticated
USING (EXISTS (
  SELECT 1 FROM public.transactions t
  WHERE t.id = transaction_items.transaction_id
    AND t.user_id = auth.uid()
));

CREATE POLICY transaction_items_insert ON public.transaction_items
FOR INSERT TO authenticated
WITH CHECK (
  EXISTS (
    SELECT 1 FROM public.transactions t
    WHERE t.id = transaction_items.transaction_id
      AND t.user_id = auth.uid()
  )
  AND EXISTS (
    SELECT 1 FROM public.products p
    WHERE p.id = transaction_items.product_id
      AND p.user_id = auth.uid()
  )
);

CREATE POLICY transaction_items_update ON public.transaction_items
FOR UPDATE TO authenticated
USING (EXISTS (
  SELECT 1 FROM public.transactions t
  WHERE t.id = transaction_items.transaction_id
    AND t.user_id = auth.uid()
))
WITH CHECK (EXISTS (
  SELECT 1 FROM public.transactions t
  WHERE t.id = transaction_items.transaction_id
    AND t.user_id = auth.uid()
));

CREATE POLICY transaction_items_delete ON public.transaction_items
FOR DELETE TO authenticated
USING (EXISTS (
  SELECT 1 FROM public.transactions t
  WHERE t.id = transaction_items.transaction_id
    AND t.user_id = auth.uid()
));

-- =========================================
-- 7) Storage: Bucket untuk Product Images
-- =========================================

-- Buat bucket (jalankan sekali saja di Supabase Dashboard atau uncomment di bawah)
-- INSERT INTO storage.buckets (id, name, public)
-- VALUES ('product-images', 'product-images', true)
-- ON CONFLICT (id) DO NOTHING;

-- Public read agar URL file bisa diakses langsung
DROP POLICY IF EXISTS "Public read product-images" ON storage.objects;
CREATE POLICY "Public read product-images"
ON storage.objects FOR SELECT
USING (bucket_id = 'product-images');

-- Izinkan upload oleh user login
DROP POLICY IF EXISTS "Authenticated insert product-images" ON storage.objects;
CREATE POLICY "Authenticated insert product-images"
ON storage.objects FOR INSERT TO authenticated
WITH CHECK (bucket_id = 'product-images');

-- Izinkan update/delete oleh user login
DROP POLICY IF EXISTS "Authenticated update product-images" ON storage.objects;
CREATE POLICY "Authenticated update product-images"
ON storage.objects FOR UPDATE TO authenticated
USING (bucket_id = 'product-images');

DROP POLICY IF EXISTS "Authenticated delete product-images" ON storage.objects;
CREATE POLICY "Authenticated delete product-images"
ON storage.objects FOR DELETE TO authenticated
USING (bucket_id = 'product-images');

-- =========================================
-- 8) Verification Queries
-- =========================================
-- Jalankan query di bawah untuk verifikasi:

-- SELECT * FROM public.products LIMIT 5;
-- SELECT * FROM public.transactions LIMIT 5;
-- SELECT * FROM public.transaction_items LIMIT 5;

-- =========================================
-- 9) Sample Data (Optional - untuk testing)
-- =========================================
-- Uncomment untuk insert sample data

/*
-- Insert sample products (ganti 'YOUR_USER_ID' dengan auth.uid() yang valid)
INSERT INTO public.products (name, price, stock, description, user_id) VALUES
('Espresso', 25000, 100, 'Coffee', 'Single shot espresso', 'YOUR_USER_ID'),
('Cappuccino', 35000, 100, 'Coffee', 'Espresso with steamed milk', 'YOUR_USER_ID'),
('Croissant', 20000, 50, 'Food', 'Buttery French pastry', 'YOUR_USER_ID'),
('Mineral Water', 10000, 200, 'Beverage', 'Bottled water', 'YOUR_USER_ID');
*/

-- =========================================
-- END OF MIGRATION
-- =========================================

-- =========================================
-- RLS POLICIES FOR EASYCOFF POS
-- =========================================
-- Run this in Supabase SQL Editor to enable RLS and allow authenticated users to manage their data

-- Enable RLS on all tables
ALTER TABLE public.products ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transaction_items ENABLE ROW LEVEL SECURITY;

-- =========================================
-- PRODUCTS POLICIES
-- =========================================

-- Allow users to view their own products
CREATE POLICY "Users can view own products"
ON public.products FOR SELECT
USING (auth.uid() = user_id);

-- Allow users to insert their own products
CREATE POLICY "Users can insert own products"
ON public.products FOR INSERT
WITH CHECK (auth.uid() = user_id);

-- Allow users to update their own products
CREATE POLICY "Users can update own products"
ON public.products FOR UPDATE
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

-- Allow users to delete their own products
CREATE POLICY "Users can delete own products"
ON public.products FOR DELETE
USING (auth.uid() = user_id);

-- =========================================
-- TRANSACTIONS POLICIES
-- =========================================

-- Allow users to view their own transactions
CREATE POLICY "Users can view own transactions"
ON public.transactions FOR SELECT
USING (auth.uid() = user_id);

-- Allow users to insert their own transactions
CREATE POLICY "Users can insert own transactions"
ON public.transactions FOR INSERT
WITH CHECK (auth.uid() = user_id);

-- Allow users to update their own transactions
CREATE POLICY "Users can update own transactions"
ON public.transactions FOR UPDATE
USING (auth.uid() = user_id)
WITH CHECK (auth.uid() = user_id);

-- Allow users to delete their own transactions
CREATE POLICY "Users can delete own transactions"
ON public.transactions FOR DELETE
USING (auth.uid() = user_id);

-- =========================================
-- TRANSACTION_ITEMS POLICIES
-- =========================================

-- Allow users to view transaction items for their transactions
CREATE POLICY "Users can view own transaction items"
ON public.transaction_items FOR SELECT
USING (
  EXISTS (
    SELECT 1 FROM public.transactions
    WHERE transactions.id = transaction_items.transaction_id
    AND transactions.user_id = auth.uid()
  )
);

-- Allow users to insert transaction items for their transactions
CREATE POLICY "Users can insert own transaction items"
ON public.transaction_items FOR INSERT
WITH CHECK (
  EXISTS (
    SELECT 1 FROM public.transactions
    WHERE transactions.id = transaction_items.transaction_id
    AND transactions.user_id = auth.uid()
  )
);

-- Allow users to update transaction items for their transactions
CREATE POLICY "Users can update own transaction items"
ON public.transaction_items FOR UPDATE
USING (
  EXISTS (
    SELECT 1 FROM public.transactions
    WHERE transactions.id = transaction_items.transaction_id
    AND transactions.user_id = auth.uid()
  )
)
WITH CHECK (
  EXISTS (
    SELECT 1 FROM public.transactions
    WHERE transactions.id = transaction_items.transaction_id
    AND transactions.user_id = auth.uid()
  )
);

-- Allow users to delete transaction items for their transactions
CREATE POLICY "Users can delete own transaction items"
ON public.transaction_items FOR DELETE
USING (
  EXISTS (
    SELECT 1 FROM public.transactions
    WHERE transactions.id = transaction_items.transaction_id
    AND transactions.user_id = auth.uid()
  )
);

ALTER TABLE public.transactions 
ADD COLUMN invoice_no TEXT UNIQUE;