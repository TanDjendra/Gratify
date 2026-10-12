BEGIN;

-- Music is resolved by its Gratify track ID at playback time. Never store expiring stream URLs.
CREATE TABLE public.music_stories (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  video_id text NOT NULL CHECK (length(btrim(video_id)) BETWEEN 1 AND 128),
  title text NOT NULL CHECK (length(btrim(title)) BETWEEN 1 AND 200),
  artist text NOT NULL CHECK (length(artist) <= 300),
  thumbnail_url text CHECK (length(thumbnail_url) <= 2048),
  track_duration_ms bigint NOT NULL CHECK (track_duration_ms BETWEEN 8000 AND 86400000),
  start_ms bigint NOT NULL CHECK (start_ms >= 0),
  clip_seconds integer NOT NULL CHECK (clip_seconds IN (8,15,30)),
  caption text NOT NULL DEFAULT '' CHECK (length(caption) <= 200),
  audience text NOT NULL DEFAULT 'friends' CHECK (audience IN ('friends','public')),
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL DEFAULT (now() + interval '24 hours'),
  CHECK (start_ms + clip_seconds::bigint * 1000 <= track_duration_ms)
);
CREATE INDEX music_stories_feed ON public.music_stories (created_at DESC,id DESC);
CREATE INDEX music_stories_owner ON public.music_stories (owner_id,expires_at);
ALTER TABLE public.music_stories ENABLE ROW LEVEL SECURITY;

CREATE FUNCTION public.gratify_is_friend(p_other uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path='' AS $$
  SELECT auth.uid() IS NOT NULL AND EXISTS (
    SELECT 1 FROM public.follows a JOIN public.follows b
      ON a.follower_id::text=b.following_id::text AND a.following_id::text=b.follower_id::text
    WHERE a.follower_id::text=auth.uid()::text AND a.following_id::text=p_other::text
  )
$$;
REVOKE ALL ON FUNCTION public.gratify_is_friend(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_is_friend(uuid) TO authenticated;
CREATE POLICY story_read ON public.music_stories FOR SELECT TO authenticated USING (
  expires_at > now() AND (owner_id=auth.uid() OR audience='public' OR public.gratify_is_friend(owner_id))
);
CREATE POLICY story_delete ON public.music_stories FOR DELETE TO authenticated USING (owner_id=auth.uid());
REVOKE ALL ON public.music_stories FROM PUBLIC,anon,authenticated;
GRANT SELECT,DELETE ON public.music_stories TO authenticated;

CREATE FUNCTION public.gratify_publish_music_story(p_video_id text,p_title text,p_artist text,
  p_thumbnail_url text,p_track_duration_ms bigint,p_start_ms bigint,p_clip_seconds integer,
  p_caption text,p_audience text) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_id uuid;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
  -- Serializes the per-user bound even when multiple devices publish together.
  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(auth.uid()::text,1));
  IF (SELECT count(*) FROM public.music_stories WHERE owner_id=auth.uid() AND expires_at>now()) >= 20
    THEN RAISE EXCEPTION 'Story limit reached'; END IF;
  INSERT INTO public.music_stories(owner_id,video_id,title,artist,thumbnail_url,track_duration_ms,
    start_ms,clip_seconds,caption,audience)
  VALUES(auth.uid(),btrim(p_video_id),btrim(p_title),p_artist,p_thumbnail_url,p_track_duration_ms,
    p_start_ms,p_clip_seconds,btrim(p_caption),p_audience) RETURNING id INTO v_id;
  RETURN v_id;
END $$;

CREATE FUNCTION public.gratify_music_stories(p_public boolean DEFAULT false,p_offset integer DEFAULT 0)
RETURNS jsonb LANGUAGE plpgsql SECURITY INVOKER SET search_path='' AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
  IF p_offset IS NULL OR p_offset NOT BETWEEN 0 AND 10000 OR p_public IS NULL THEN RAISE EXCEPTION 'Invalid page'; END IF;
  RETURN coalesce((SELECT jsonb_agg(to_jsonb(s) ORDER BY s.created_at DESC,s.id DESC) FROM (
    SELECT ms.*,p.display_name AS owner_name,p.avatar_url AS owner_avatar
    FROM public.music_stories ms LEFT JOIN public.public_profiles p ON p.id::text=ms.owner_id::text
    WHERE ms.expires_at>now() AND (CASE WHEN p_public THEN ms.audience='public'
      ELSE ms.owner_id=auth.uid() OR public.gratify_is_friend(ms.owner_id) END)
    ORDER BY ms.created_at DESC,ms.id DESC LIMIT 50 OFFSET p_offset
  ) s),'[]'::jsonb);
END $$;

CREATE TABLE public.direct_conversations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_low uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  user_high uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  request_from uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  accepted boolean NOT NULL DEFAULT false,
  rejected boolean NOT NULL DEFAULT false,
  low_read_at timestamptz,
  high_read_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(user_low,user_high), CHECK(user_low<user_high), CHECK(request_from IN (user_low,user_high))
);
CREATE TABLE public.direct_messages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id uuid NOT NULL REFERENCES public.direct_conversations(id) ON DELETE CASCADE,
  sender_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  client_id uuid NOT NULL,
  body text NOT NULL CHECK (length(btrim(body)) BETWEEN 1 AND 2000),
  created_at timestamptz NOT NULL DEFAULT clock_timestamp(),
  UNIQUE(sender_id,client_id)
);
CREATE INDEX direct_messages_page ON public.direct_messages(conversation_id,created_at DESC,id DESC);
CREATE INDEX direct_conversations_low ON public.direct_conversations(user_low);
CREATE INDEX direct_conversations_high ON public.direct_conversations(user_high);
ALTER TABLE public.direct_conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.direct_messages ENABLE ROW LEVEL SECURITY;
CREATE POLICY dm_participant ON public.direct_conversations FOR SELECT TO authenticated
  USING (auth.uid() IN (user_low,user_high));
CREATE POLICY dm_message_participant ON public.direct_messages FOR SELECT TO authenticated USING (
  EXISTS(SELECT 1 FROM public.direct_conversations c WHERE c.id=conversation_id)
);
REVOKE ALL ON public.direct_conversations,public.direct_messages FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.direct_conversations,public.direct_messages TO authenticated;

CREATE FUNCTION public.gratify_dm_open(p_peer uuid) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_me uuid:=auth.uid(); v_id uuid;
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
  IF p_peer IS NULL OR p_peer=v_me OR NOT EXISTS(SELECT 1 FROM public.profiles WHERE id::text=p_peer::text)
    THEN RAISE EXCEPTION 'Invalid recipient'; END IF;
  INSERT INTO public.direct_conversations(user_low,user_high,request_from,accepted)
  VALUES(least(v_me,p_peer),greatest(v_me,p_peer),v_me,public.gratify_is_friend(p_peer))
  ON CONFLICT(user_low,user_high) DO NOTHING;
  SELECT id INTO v_id FROM public.direct_conversations
    WHERE user_low=least(v_me,p_peer) AND user_high=greatest(v_me,p_peer) AND NOT rejected;
  IF v_id IS NULL THEN RAISE EXCEPTION 'Request unavailable' USING ERRCODE='42501'; END IF;
  IF public.gratify_is_friend(p_peer) THEN
    UPDATE public.direct_conversations SET accepted=true WHERE id=v_id;
  END IF;
  RETURN v_id;
END $$;

CREATE FUNCTION public.gratify_dm_send(p_conversation uuid,p_body text,p_client_id uuid)
RETURNS SETOF public.direct_messages LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_me uuid:=auth.uid(); c public.direct_conversations; existing public.direct_messages;
BEGIN
  IF v_me IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
  SELECT * INTO c FROM public.direct_conversations WHERE id=p_conversation FOR UPDATE;
  IF c.id IS NULL OR v_me NOT IN (c.user_low,c.user_high) OR c.rejected THEN
    RAISE EXCEPTION 'Conversation unavailable' USING ERRCODE='42501'; END IF;
  SELECT * INTO existing FROM public.direct_messages WHERE sender_id=v_me AND client_id=p_client_id;
  IF existing.id IS NOT NULL THEN
    IF existing.conversation_id<>p_conversation OR existing.body<>btrim(p_body) THEN RAISE EXCEPTION 'Retry identity mismatch'; END IF;
    RETURN NEXT existing; RETURN;
  END IF;
  IF NOT c.accepted THEN
    IF c.request_from<>v_me THEN RAISE EXCEPTION 'Accept request before replying' USING ERRCODE='42501'; END IF;
    IF EXISTS(SELECT 1 FROM public.direct_messages WHERE conversation_id=c.id) THEN
      RAISE EXCEPTION 'Wait until request is accepted' USING ERRCODE='42501'; END IF;
  END IF;
  -- A small per-conversation burst bound prevents accidental repeated taps from flooding a thread.
  IF (SELECT count(*) FROM public.direct_messages WHERE conversation_id=c.id AND sender_id=v_me
    AND created_at>clock_timestamp()-interval '10 seconds') >= 10 THEN RAISE EXCEPTION 'Please wait before sending'; END IF;
  RETURN QUERY INSERT INTO public.direct_messages(conversation_id,sender_id,client_id,body)
    VALUES(c.id,v_me,p_client_id,btrim(p_body)) RETURNING *;
END $$;

CREATE FUNCTION public.gratify_dm_respond(p_conversation uuid,p_accept boolean) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
  IF auth.uid() IS NULL OR p_accept IS NULL THEN RAISE EXCEPTION 'Invalid request' USING ERRCODE='42501'; END IF;
  UPDATE public.direct_conversations SET accepted=p_accept,rejected=NOT p_accept
    WHERE id=p_conversation AND auth.uid() IN (user_low,user_high) AND request_from<>auth.uid() AND NOT accepted AND NOT rejected;
  IF NOT FOUND THEN RAISE EXCEPTION 'Request unavailable' USING ERRCODE='42501'; END IF;
END $$;

CREATE FUNCTION public.gratify_dm_messages(p_conversation uuid,p_before uuid DEFAULT NULL) RETURNS jsonb
LANGUAGE plpgsql SECURITY INVOKER SET search_path='' AS $$
DECLARE v_time timestamptz;
BEGIN
  IF NOT EXISTS(SELECT 1 FROM public.direct_conversations WHERE id=p_conversation) THEN
    RAISE EXCEPTION 'Conversation unavailable' USING ERRCODE='42501'; END IF;
  IF p_before IS NOT NULL THEN
    SELECT created_at INTO v_time FROM public.direct_messages WHERE id=p_before AND conversation_id=p_conversation;
    IF v_time IS NULL THEN RAISE EXCEPTION 'Invalid message cursor'; END IF;
  END IF;
  RETURN coalesce((SELECT jsonb_agg(to_jsonb(m) ORDER BY m.created_at,m.id) FROM (
    SELECT * FROM public.direct_messages WHERE conversation_id=p_conversation AND
      (p_before IS NULL OR (created_at,id)<(v_time,p_before)) ORDER BY created_at DESC,id DESC LIMIT 50
  ) m),'[]'::jsonb);
END $$;

CREATE FUNCTION public.gratify_dm_read(p_conversation uuid,p_message uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_time timestamptz;
BEGIN
  SELECT m.created_at INTO v_time FROM public.direct_messages m JOIN public.direct_conversations c ON c.id=m.conversation_id
    WHERE c.id=p_conversation AND m.id=p_message AND auth.uid() IN (c.user_low,c.user_high);
  IF v_time IS NULL THEN RAISE EXCEPTION 'Message unavailable' USING ERRCODE='42501'; END IF;
  UPDATE public.direct_conversations SET
    low_read_at=CASE WHEN user_low=auth.uid() THEN greatest(low_read_at,v_time) ELSE low_read_at END,
    high_read_at=CASE WHEN user_high=auth.uid() THEN greatest(high_read_at,v_time) ELSE high_read_at END
    WHERE id=p_conversation;
END $$;

CREATE FUNCTION public.gratify_dm_inbox() RETURNS jsonb
LANGUAGE plpgsql SECURITY INVOKER SET search_path='' AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
  RETURN coalesce((SELECT jsonb_agg(to_jsonb(t) ORDER BY t.last_at DESC NULLS LAST,t.id) FROM (
    SELECT c.id,CASE WHEN c.user_low=auth.uid() THEN c.user_high ELSE c.user_low END AS peer_id,
      p.display_name AS peer_name,p.avatar_url AS peer_avatar,c.accepted,c.request_from,
      m.body AS last_body,m.sender_id AS last_sender_id,m.created_at AS last_at,
      CASE WHEN c.accepted THEN CASE WHEN c.user_low=auth.uid() THEN c.high_read_at ELSE c.low_read_at END END AS peer_read_at,
      (SELECT count(*)::integer FROM public.direct_messages u WHERE u.conversation_id=c.id AND u.sender_id<>auth.uid()
        AND u.created_at>coalesce(CASE WHEN c.user_low=auth.uid() THEN c.low_read_at ELSE c.high_read_at END,'-infinity'::timestamptz)) AS unread_count
    FROM public.direct_conversations c
    LEFT JOIN public.public_profiles p ON p.id::text=(CASE WHEN c.user_low=auth.uid() THEN c.user_high ELSE c.user_low END)::text
    LEFT JOIN LATERAL(SELECT * FROM public.direct_messages WHERE conversation_id=c.id ORDER BY created_at DESC,id DESC LIMIT 1) m ON true
    WHERE NOT c.rejected ORDER BY m.created_at DESC NULLS LAST,c.id LIMIT 200
  ) t),'[]'::jsonb);
END $$;

REVOKE ALL ON FUNCTION public.gratify_publish_music_story(text,text,text,text,bigint,bigint,integer,text,text),
  public.gratify_music_stories(boolean,integer),public.gratify_dm_open(uuid),public.gratify_dm_send(uuid,text,uuid),
  public.gratify_dm_respond(uuid,boolean),public.gratify_dm_messages(uuid,uuid),public.gratify_dm_read(uuid,uuid),public.gratify_dm_inbox()
  FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_publish_music_story(text,text,text,text,bigint,bigint,integer,text,text),
  public.gratify_music_stories(boolean,integer),public.gratify_dm_open(uuid),public.gratify_dm_send(uuid,text,uuid),
  public.gratify_dm_respond(uuid,boolean),public.gratify_dm_messages(uuid,uuid),public.gratify_dm_read(uuid,uuid),public.gratify_dm_inbox()
  TO authenticated;
-- Make the newly-created RPCs visible to the app immediately after the transaction commits.
NOTIFY pgrst, 'reload schema';
COMMIT;
