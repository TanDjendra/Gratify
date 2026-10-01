BEGIN;
CREATE TABLE IF NOT EXISTS public.user_library_state (
    user_id text NOT NULL, kind text NOT NULL, item_id text NOT NULL,
    enabled boolean NOT NULL, updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(user_id,kind,item_id)
);
ALTER TABLE public.user_library_state ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS library_state_owner ON public.user_library_state;
CREATE POLICY library_state_owner ON public.user_library_state FOR ALL TO authenticated
    USING(user_id=auth.uid()::text) WITH CHECK(user_id=auth.uid()::text);
REVOKE ALL ON public.user_library_state FROM PUBLIC,anon,authenticated;
GRANT SELECT,INSERT,UPDATE,DELETE ON public.user_library_state TO authenticated;

CREATE OR REPLACE FUNCTION public.gratify_apply_library_changes(p_changes jsonb) RETURNS void
LANGUAGE plpgsql SECURITY INVOKER SET search_path='' AS $$
DECLARE v_owner text:=auth.uid()::text; c jsonb; p jsonb; k text; i text; e boolean;
BEGIN
    IF v_owner IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
    IF p_changes IS NULL OR jsonb_typeof(p_changes)<>'array' OR jsonb_array_length(p_changes)>100 THEN RAISE EXCEPTION 'Invalid changes'; END IF;
    FOR c IN SELECT value FROM jsonb_array_elements(p_changes) LOOP
        k:=c->>'kind'; i:=c->>'item_id'; e:=(c->>'enabled')::boolean; p:=c->'payload';
        IF k IS NULL OR k NOT IN ('user_liked_songs','user_followed_artists','user_saved_albums') OR i IS NULL OR length(i)=0 OR e IS NULL THEN RAISE EXCEPTION 'Invalid library item'; END IF;
        PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(v_owner || ':' || k || ':' || i,0));
        IF NOT e THEN
            CASE k
            WHEN 'user_liked_songs' THEN DELETE FROM public.user_liked_songs WHERE user_id=v_owner AND video_id=i;
            WHEN 'user_followed_artists' THEN DELETE FROM public.user_followed_artists WHERE user_id=v_owner AND channel_id=i;
            WHEN 'user_saved_albums' THEN DELETE FROM public.user_saved_albums WHERE user_id=v_owner AND browse_id=i;
            END CASE;
        ELSE
            IF p IS NULL OR jsonb_typeof(p)<>'object' THEN RAISE EXCEPTION 'Missing library metadata'; END IF;
            CASE k
            WHEN 'user_liked_songs' THEN
                INSERT INTO public.user_liked_songs(id,user_id,video_id,title,artist_name,artist_id,album_name,album_id,duration,thumbnail_url,favorite_at)
                VALUES(v_owner || '_' || i,v_owner,i,p->>'title',p->>'artist_name',p->>'artist_id',p->>'album_name',p->>'album_id',(p->>'duration')::integer,p->>'thumbnail_url',p->>'favorite_at')
                ON CONFLICT(user_id,video_id) DO UPDATE SET title=excluded.title,artist_name=excluded.artist_name,favorite_at=excluded.favorite_at;
            WHEN 'user_followed_artists' THEN
                INSERT INTO public.user_followed_artists(id,user_id,channel_id,name,thumbnail_url,followed_at)
                VALUES(v_owner || '_' || i,v_owner,i,p->>'name',p->>'thumbnail_url',p->>'followed_at')
                ON CONFLICT(user_id,channel_id) DO UPDATE SET name=excluded.name,thumbnail_url=excluded.thumbnail_url,followed_at=excluded.followed_at;
            WHEN 'user_saved_albums' THEN
                INSERT INTO public.user_saved_albums(id,user_id,browse_id,title,artist_name,artist_id,thumbnail_url,track_count,favorite_at)
                VALUES(v_owner || '_' || i,v_owner,i,p->>'title',p->>'artist_name',p->>'artist_id',p->>'thumbnail_url',(p->>'track_count')::integer,p->>'favorite_at')
                ON CONFLICT(user_id,browse_id) DO UPDATE SET title=excluded.title,thumbnail_url=excluded.thumbnail_url,favorite_at=excluded.favorite_at;
            END CASE;
        END IF;
        INSERT INTO public.user_library_state(user_id,kind,item_id,enabled) VALUES(v_owner,k,i,e)
        ON CONFLICT(user_id,kind,item_id) DO UPDATE SET enabled=excluded.enabled,updated_at=now();
    END LOOP;
END $$;
REVOKE ALL ON FUNCTION public.gratify_apply_library_changes(jsonb) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_apply_library_changes(jsonb) TO authenticated;
COMMIT;
