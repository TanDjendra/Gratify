-- Jalankan sekali di Supabase SQL Editor untuk database yang sudah memakai
-- supabase_setup.sql versi lama. Seluruh perubahan berada dalam satu transaksi.
-- Jika satu tabel tidak ada atau satu perintah gagal, perubahan dibatalkan.
-- Data yang telanjur diakses atau diubah pihak lain tidak dapat dipulihkan oleh skrip ini.

BEGIN;

DO $$
DECLARE
    t TEXT;
    p RECORD;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'user_liked_songs',
        'user_followed_artists',
        'user_saved_albums',
        'user_play_history',
        'user_queue',
        'user_settings'
    ] LOOP
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t);

        -- Semua policy lama dihapus: policy permisif PostgreSQL digabung dengan OR.
        FOR p IN
            SELECT policyname FROM pg_policies
            WHERE schemaname = 'public' AND tablename = t
        LOOP
            EXECUTE format('DROP POLICY %I ON public.%I', p.policyname, t);
        END LOOP;

        -- user_id::text mendukung skema lama (TEXT) dan migrasi (UUID).
        EXECUTE format(
            'CREATE POLICY %I ON public.%I FOR ALL TO authenticated '
            || 'USING (auth.uid()::text = user_id::text) '
            || 'WITH CHECK (auth.uid()::text = user_id::text)',
            t || '_owner_only', t
        );

        EXECUTE format('REVOKE ALL ON public.%I FROM anon, authenticated, PUBLIC', t);
        EXECUTE format(
            'GRANT SELECT, INSERT, UPDATE, DELETE ON public.%I TO authenticated', t
        );
    END LOOP;
END $$;

COMMIT;

-- Setelah menjalankan, periksa pg_policies: harus ada satu policy _owner_only
-- per tabel di atas. Pastikan role anon tidak punya hak tabel dan uji akses
-- memakai dua akun berbeda sebelum mengaktifkan sinkronisasi lagi.
