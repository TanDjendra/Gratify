-- Credentials can only be accessed by the authenticated Edge Function's service role.
BEGIN;
CREATE TABLE IF NOT EXISTS public.youtube_oauth_connections (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    google_subject text NOT NULL,
    token_ciphertext text NOT NULL,
    expires_at timestamptz NOT NULL,
    needs_reconnect boolean NOT NULL DEFAULT false,
    updated_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.youtube_oauth_connections ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.youtube_oauth_connections FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.youtube_oauth_connections TO service_role;
CREATE TABLE IF NOT EXISTS public.youtube_oauth_attempts (
    user_id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    nonce uuid UNIQUE NOT NULL,
    expires_at timestamptz NOT NULL
);
ALTER TABLE public.youtube_oauth_attempts ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.youtube_oauth_attempts FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.youtube_oauth_attempts TO service_role;
-- Consume the grant and store its credentials in one transaction. A pending browser
-- callback cannot recreate a connection after the user disconnects it.
CREATE OR REPLACE FUNCTION public.complete_youtube_oauth_attempt(
    p_user_id uuid, p_nonce uuid, p_google_subject text,
    p_token_ciphertext text, p_expires_at timestamptz
) RETURNS boolean LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
BEGIN
    DELETE FROM public.youtube_oauth_attempts
      WHERE user_id = p_user_id AND nonce = p_nonce AND expires_at > now();
    IF NOT FOUND THEN RETURN false; END IF;
    INSERT INTO public.youtube_oauth_connections(user_id, google_subject, token_ciphertext, expires_at)
      VALUES (p_user_id, p_google_subject, p_token_ciphertext, p_expires_at)
      ON CONFLICT (user_id) DO UPDATE SET google_subject = EXCLUDED.google_subject,
        token_ciphertext = EXCLUDED.token_ciphertext, expires_at = EXCLUDED.expires_at,
        needs_reconnect = false, updated_at = now();
    RETURN true;
END;
$$;
CREATE OR REPLACE FUNCTION public.disconnect_youtube_oauth(p_user_id uuid)
RETURNS void LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
BEGIN
    DELETE FROM public.youtube_oauth_attempts WHERE user_id = p_user_id;
    DELETE FROM public.youtube_oauth_connections WHERE user_id = p_user_id;
END;
$$;
REVOKE ALL ON FUNCTION public.complete_youtube_oauth_attempt(uuid, uuid, text, text, timestamptz) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.disconnect_youtube_oauth(uuid) FROM PUBLIC, anon, authenticated;
GRANT EXECUTE ON FUNCTION public.complete_youtube_oauth_attempt(uuid, uuid, text, text, timestamptz) TO service_role;
GRANT EXECUTE ON FUNCTION public.disconnect_youtube_oauth(uuid) TO service_role;
COMMIT;
