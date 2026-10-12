BEGIN;

CREATE TABLE public.push_devices (
  installation_id uuid PRIMARY KEY, owner_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  token text NOT NULL UNIQUE CHECK(length(token) BETWEEN 32 AND 4096),
  package_name text NOT NULL CHECK(package_name IN ('com.tan.gratify','com.tan.gratify.dev')),
  enabled boolean NOT NULL DEFAULT true, updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE public.social_notification_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(), recipient_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  actor_id uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  kind text NOT NULL CHECK(kind IN ('message','message_request','story')), source_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(), expires_at timestamptz NOT NULL,
  UNIQUE(recipient_id,kind,source_id), CHECK(recipient_id<>actor_id)
);
CREATE INDEX social_notification_inbox ON public.social_notification_events(recipient_id,created_at DESC);
CREATE TABLE public.push_delivery_jobs (
  event_id uuid NOT NULL REFERENCES public.social_notification_events(id) ON DELETE CASCADE,
  installation_id uuid NOT NULL REFERENCES public.push_devices(installation_id) ON DELETE CASCADE,
  attempts integer NOT NULL DEFAULT 0, next_attempt_at timestamptz NOT NULL DEFAULT now(),
  claim_id uuid, leased_until timestamptz, finished boolean NOT NULL DEFAULT false,
  PRIMARY KEY(event_id,installation_id)
);
ALTER TABLE public.push_devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.social_notification_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.push_delivery_jobs ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.push_devices,public.social_notification_events,public.push_delivery_jobs FROM PUBLIC,anon,authenticated;
GRANT SELECT ON public.social_notification_events TO authenticated;
GRANT ALL ON public.push_devices,public.social_notification_events,public.push_delivery_jobs TO service_role;
CREATE POLICY social_notification_read ON public.social_notification_events FOR SELECT TO authenticated USING (
  recipient_id=auth.uid() AND expires_at>now() AND (
    (kind='story' AND EXISTS(SELECT 1 FROM public.music_stories s WHERE s.id=source_id)) OR
    (kind IN ('message','message_request') AND EXISTS(SELECT 1 FROM public.direct_messages m
      JOIN public.direct_conversations c ON c.id=m.conversation_id WHERE m.id=source_id AND NOT c.rejected))
  )
);

CREATE FUNCTION public.gratify_register_push_device(p_installation uuid,p_token text,p_package text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Authentication required' USING ERRCODE='42501'; END IF;
  IF p_installation IS NULL OR length(p_token) NOT BETWEEN 32 AND 4096 OR p_token IS NULL
     OR p_package NOT IN ('com.tan.gratify','com.tan.gratify.dev') OR p_package IS NULL
    THEN RAISE EXCEPTION 'Invalid device'; END IF;
  PERFORM pg_catalog.pg_advisory_xact_lock(pg_catalog.hashtextextended(p_token,13));
  DELETE FROM public.push_devices WHERE token=p_token AND installation_id<>p_installation;
  INSERT INTO public.push_devices(installation_id,owner_id,token,package_name)
    VALUES(p_installation,auth.uid(),p_token,p_package)
    ON CONFLICT(installation_id) DO UPDATE SET owner_id=EXCLUDED.owner_id,token=EXCLUDED.token,
      package_name=EXCLUDED.package_name,enabled=true,updated_at=now();
END $$;
REVOKE ALL ON FUNCTION public.gratify_register_push_device(uuid,text,text) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_register_push_device(uuid,text,text) TO authenticated;

CREATE FUNCTION public.gratify_unregister_push_device(p_installation uuid) RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$
  UPDATE public.push_devices SET enabled=false WHERE installation_id=p_installation AND owner_id=auth.uid()
$$;
REVOKE ALL ON FUNCTION public.gratify_unregister_push_device(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_unregister_push_device(uuid) TO authenticated;

CREATE FUNCTION public.gratify_queue_social_push() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE c public.direct_conversations; v_recipient uuid;
BEGIN
  IF TG_TABLE_NAME='direct_messages' THEN
    SELECT * INTO c FROM public.direct_conversations WHERE id=NEW.conversation_id;
    IF c.rejected THEN RETURN NEW; END IF;
    v_recipient:=CASE WHEN c.user_low=NEW.sender_id THEN c.user_high ELSE c.user_low END;
    INSERT INTO public.social_notification_events(recipient_id,actor_id,kind,source_id,expires_at)
      VALUES(v_recipient,NEW.sender_id,CASE WHEN c.accepted THEN 'message' ELSE 'message_request' END,NEW.id,now()+interval '24 hours')
      ON CONFLICT DO NOTHING;
  ELSE
    INSERT INTO public.social_notification_events(recipient_id,actor_id,kind,source_id,expires_at)
      SELECT DISTINCT f.follower_id::uuid,NEW.owner_id,'story',NEW.id,NEW.expires_at FROM public.follows f
      WHERE f.following_id::text=NEW.owner_id::text AND f.follower_id::text<>NEW.owner_id::text
      AND (NEW.audience='public' OR EXISTS(SELECT 1 FROM public.follows back
        WHERE back.follower_id::text=NEW.owner_id::text AND back.following_id::text=f.follower_id::text))
      ON CONFLICT DO NOTHING;
  END IF;
  RETURN NEW;
END $$;
CREATE TRIGGER dm_push AFTER INSERT ON public.direct_messages FOR EACH ROW EXECUTE FUNCTION public.gratify_queue_social_push();
CREATE TRIGGER story_push AFTER INSERT ON public.music_stories FOR EACH ROW EXECUTE FUNCTION public.gratify_queue_social_push();

CREATE FUNCTION public.gratify_create_push_jobs() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
  INSERT INTO public.push_delivery_jobs(event_id,installation_id)
    SELECT NEW.id,d.installation_id FROM public.push_devices d WHERE d.owner_id=NEW.recipient_id AND d.enabled
      AND d.updated_at>now()-interval '30 days' ON CONFLICT DO NOTHING;
  RETURN NEW;
END $$;
CREATE TRIGGER social_push_jobs AFTER INSERT ON public.social_notification_events FOR EACH ROW EXECUTE FUNCTION public.gratify_create_push_jobs();

CREATE FUNCTION public.gratify_claim_push_jobs(p_limit integer DEFAULT 50) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
DECLARE v_result jsonb;
BEGIN
  WITH eligible AS (
    SELECT j.event_id,j.installation_id FROM public.push_delivery_jobs j
      JOIN public.social_notification_events e ON e.id=j.event_id JOIN public.push_devices d ON d.installation_id=j.installation_id
    WHERE NOT j.finished AND j.attempts<5 AND j.next_attempt_at<=now() AND (j.leased_until IS NULL OR j.leased_until<now())
      AND e.expires_at>now() AND d.enabled AND d.owner_id=e.recipient_id AND d.updated_at>now()-interval '30 days'
      AND (
        (e.kind IN ('message','message_request') AND EXISTS(SELECT 1 FROM public.direct_messages m
          JOIN public.direct_conversations c ON c.id=m.conversation_id WHERE m.id=e.source_id AND NOT c.rejected
            AND e.recipient_id IN(c.user_low,c.user_high))) OR
        (e.kind='story' AND EXISTS(SELECT 1 FROM public.music_stories s WHERE s.id=e.source_id AND s.expires_at>now()
          AND EXISTS(SELECT 1 FROM public.follows f WHERE f.follower_id::text=e.recipient_id::text AND f.following_id::text=s.owner_id::text)
          AND (s.audience='public' OR EXISTS(SELECT 1 FROM public.follows f WHERE f.follower_id::text=s.owner_id::text AND f.following_id::text=e.recipient_id::text))))
      )
    ORDER BY e.created_at LIMIT greatest(1,least(p_limit,100)) FOR UPDATE OF j SKIP LOCKED
  ), claimed AS (
    UPDATE public.push_delivery_jobs j SET claim_id=gen_random_uuid(),leased_until=now()+interval '90 seconds',attempts=j.attempts+1
    FROM eligible x WHERE j.event_id=x.event_id AND j.installation_id=x.installation_id RETURNING j.*
  ) SELECT coalesce(jsonb_agg(jsonb_build_object('event_id',e.id,'installation_id',d.installation_id,'claim_id',j.claim_id,
      'owner_id',e.recipient_id,'actor_id',e.actor_id,'kind',e.kind,'source_id',e.source_id,'token',d.token,'package_name',d.package_name)), '[]'::jsonb)
    INTO v_result FROM claimed j JOIN public.social_notification_events e ON e.id=j.event_id
      JOIN public.push_devices d ON d.installation_id=j.installation_id;
  RETURN v_result;
END $$;
REVOKE ALL ON FUNCTION public.gratify_claim_push_jobs(integer) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.gratify_claim_push_jobs(integer) TO service_role;

CREATE FUNCTION public.gratify_ack_push_job(p_event uuid,p_installation uuid,p_claim uuid,p_result text,p_token text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path='' AS $$
BEGIN
  UPDATE public.push_delivery_jobs SET finished=p_result IN ('sent','invalid'),leased_until=NULL,
    next_attempt_at=now()+interval '1 minute'*power(2,attempts)
    WHERE event_id=p_event AND installation_id=p_installation AND claim_id=p_claim;
  IF FOUND AND p_result='invalid' THEN
    UPDATE public.push_devices SET enabled=false WHERE installation_id=p_installation AND token=p_token;
  END IF;
END $$;
REVOKE ALL ON FUNCTION public.gratify_ack_push_job(uuid,uuid,uuid,text,text) FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.gratify_ack_push_job(uuid,uuid,uuid,text,text) TO service_role;

CREATE FUNCTION public.gratify_social_notifications(p_event uuid DEFAULT NULL,p_limit integer DEFAULT 50) RETURNS jsonb
LANGUAGE sql SECURITY INVOKER SET search_path='' AS $$
  SELECT coalesce(jsonb_agg(to_jsonb(t)),'[]'::jsonb) FROM (
    SELECT e.id,e.actor_id,e.kind,e.source_id,e.created_at,p.display_name,p.avatar_url,
      m.conversation_id FROM public.social_notification_events e
      LEFT JOIN public.public_profiles p ON p.id::text=e.actor_id::text
      LEFT JOIN public.direct_messages m ON m.id=e.source_id AND e.kind IN ('message','message_request')
    WHERE p_event IS NULL OR e.id=p_event ORDER BY e.created_at DESC LIMIT greatest(1,least(p_limit,100))
  ) t
$$;
REVOKE ALL ON FUNCTION public.gratify_social_notifications(uuid,integer) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_social_notifications(uuid,integer) TO authenticated;

NOTIFY pgrst,'reload schema';
CREATE FUNCTION public.gratify_notification_story(p_story uuid) RETURNS jsonb
LANGUAGE sql SECURITY INVOKER SET search_path='' AS $$
  SELECT coalesce(jsonb_agg(to_jsonb(s)||jsonb_build_object('owner_name',p.display_name,'owner_avatar',p.avatar_url)),'[]'::jsonb)
    FROM public.music_stories s LEFT JOIN public.public_profiles p ON p.id::text=s.owner_id::text WHERE s.id=p_story
$$;
REVOKE ALL ON FUNCTION public.gratify_notification_story(uuid) FROM PUBLIC,anon;
GRANT EXECUTE ON FUNCTION public.gratify_notification_story(uuid) TO authenticated;
CREATE FUNCTION public.gratify_prune_push_history() RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path='' AS $$
  DELETE FROM public.social_notification_events WHERE expires_at < now()-interval '7 days';
  DELETE FROM public.push_devices WHERE updated_at < now()-interval '90 days'
$$;
REVOKE ALL ON FUNCTION public.gratify_prune_push_history() FROM PUBLIC,anon,authenticated;
GRANT EXECUTE ON FUNCTION public.gratify_prune_push_history() TO service_role;
COMMIT;
