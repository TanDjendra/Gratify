-- Owner-only Storage access matching the app's <auth uid>.jpg avatar path.
-- No existing objects are removed and the bucket's public-read setting is preserved.
BEGIN;
DO $preflight$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM storage.buckets WHERE id = 'avatars') THEN
    RAISE EXCEPTION 'Expected avatars bucket is missing';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'storage' AND tablename = 'objects'
    AND policyname NOT IN ('gratify_avatar_select', 'gratify_avatar_insert', 'gratify_avatar_update', 'gratify_avatar_delete')) THEN
    RAISE EXCEPTION 'Review existing Storage policies before applying this migration';
  END IF;
END;
$preflight$;

DO $policies$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='storage' AND tablename='objects' AND policyname='gratify_avatar_select') THEN
    CREATE POLICY gratify_avatar_select ON storage.objects FOR SELECT TO authenticated
      USING (bucket_id = 'avatars' AND name = (SELECT auth.uid())::text || '.jpg');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='storage' AND tablename='objects' AND policyname='gratify_avatar_insert') THEN
    CREATE POLICY gratify_avatar_insert ON storage.objects FOR INSERT TO authenticated
      WITH CHECK (bucket_id = 'avatars' AND name = (SELECT auth.uid())::text || '.jpg');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='storage' AND tablename='objects' AND policyname='gratify_avatar_update') THEN
    CREATE POLICY gratify_avatar_update ON storage.objects FOR UPDATE TO authenticated
      USING (bucket_id = 'avatars' AND name = (SELECT auth.uid())::text || '.jpg')
      WITH CHECK (bucket_id = 'avatars' AND name = (SELECT auth.uid())::text || '.jpg');
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='storage' AND tablename='objects' AND policyname='gratify_avatar_delete') THEN
    CREATE POLICY gratify_avatar_delete ON storage.objects FOR DELETE TO authenticated
      USING (bucket_id = 'avatars' AND name = (SELECT auth.uid())::text || '.jpg');
  END IF;
END;
$policies$;

UPDATE storage.buckets SET file_size_limit = 5242880,
  allowed_mime_types = ARRAY['image/jpeg','image/png','image/webp']
WHERE id = 'avatars';
COMMIT;
