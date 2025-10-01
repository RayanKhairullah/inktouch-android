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
