BEGIN;
ALTER TABLE public.user_play_history ADD COLUMN IF NOT EXISTS listen_count bigint NOT NULL DEFAULT 1 CHECK (listen_count >= 0);

CREATE OR REPLACE FUNCTION public.gratify_replace_queue(p_items jsonb) RETURNS void
LANGUAGE plpgsql SECURITY INVOKER SET search_path = '' AS $$
DECLARE v_owner text := auth.uid()::text;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_items IS NULL OR jsonb_typeof(p_items) <> 'array' OR jsonb_array_length(p_items) > 10000 THEN
        RAISE EXCEPTION 'Invalid queue';
    END IF;
    PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner || ':queue',0));
    DELETE FROM public.user_queue WHERE user_id=v_owner;
    INSERT INTO public.user_queue(id,user_id,video_id,title,artist_name,duration,thumbnail_url,position)
    SELECT v_owner || '_queue_' || (ordinality-1),v_owner,item->>'video_id',item->>'title',
        item->>'artist_name',(item->>'duration')::integer,item->>'thumbnail_url',(ordinality-1)::integer
    FROM jsonb_array_elements(p_items) WITH ORDINALITY AS tracks(item,ordinality);
END $$;
REVOKE ALL ON FUNCTION public.gratify_replace_queue(jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_replace_queue(jsonb) TO authenticated;
COMMIT;
