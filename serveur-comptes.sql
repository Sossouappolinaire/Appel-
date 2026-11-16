-- Serveur de l'application « Appel Prédictions » (comptes, abonnement, canaux par utilisateur).
-- Projet Supabase : tv-kouame (hvjgqaxejqnvgvefjhnj). À coller dans Supabase > SQL Editor > Run. Script rejouable (IF NOT EXISTS / CREATE OR REPLACE).

-- ───────────── Mise à niveau automatique depuis l'ancienne version (v1.1) : sans effet si la base est déjà en v1.2 ─────────────
DO $m$ DECLARE r record; BEGIN
  IF to_regclass('public.app_sessions') IS NOT NULL
     AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='app_sessions' AND column_name='token_hash') THEN
    -- anciennes fonctions et anciennes sessions (jeton en clair) : remplacées par la v1.2 (les utilisateurs devront se reconnecter)
    FOR r IN SELECT p.oid::regprocedure AS sig FROM pg_proc p
             WHERE p.pronamespace = 'public'::regnamespace
               AND (p.proname LIKE 'app\_%' OR p.proname LIKE 'adm\_%' OR p.proname LIKE '\_app\_%')
    LOOP EXECUTE 'DROP FUNCTION IF EXISTS ' || r.sig || ' CASCADE'; END LOOP;
    DROP TABLE public.app_sessions;
  END IF;
  IF to_regclass('public.app_users') IS NOT NULL THEN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='app_users' AND column_name='fails')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='app_users' AND column_name='fail_count') THEN
      ALTER TABLE public.app_users RENAME COLUMN fails TO fail_count;
    END IF;
    ALTER TABLE public.app_users DROP CONSTRAINT IF EXISTS app_users_status_check;
    UPDATE public.app_users SET status = 'active' WHERE status = 'approved';
    ALTER TABLE public.app_users ADD CONSTRAINT app_users_status_check CHECK (status IN ('pending','active','blocked'));
  END IF;
END $m$;

-- ───────────── Tables (RLS activée, aucune politique : seules les fonctions ci-dessous y accèdent) ─────────────
CREATE TABLE IF NOT EXISTS public.app_users (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email        text NOT NULL,
  pin_hash     text NOT NULL,
  is_admin     boolean NOT NULL DEFAULT false,
  status       text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','active','blocked')),
  access_until timestamptz,
  created_at   timestamptz NOT NULL DEFAULT now(),
  fail_count   int NOT NULL DEFAULT 0,
  locked_until timestamptz
);
CREATE UNIQUE INDEX IF NOT EXISTS app_users_email_uq ON public.app_users (lower(email));

CREATE TABLE IF NOT EXISTS public.app_sessions (
  token_hash text PRIMARY KEY,
  user_id    uuid NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_seen  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS app_sessions_user_idx ON public.app_sessions (user_id);

-- canaux accordés à chaque utilisateur (choisis par l'administrateur à la validation)
CREATE TABLE IF NOT EXISTS public.app_user_channels (
  user_id    uuid NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
  channel_id text NOT NULL,
  PRIMARY KEY (user_id, channel_id)
);

CREATE TABLE IF NOT EXISTS public.app_payments (
  id            bigserial PRIMARY KEY,
  user_id       uuid NOT NULL REFERENCES public.app_users(id) ON DELETE CASCADE,
  created_at    timestamptz NOT NULL DEFAULT now(),
  image         text NOT NULL,
  status        text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','done')),
  granted_hours int NOT NULL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS app_payments_user_idx ON public.app_payments (user_id);

ALTER TABLE public.app_users         ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_sessions      ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_user_channels ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_payments      ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON public.app_users, public.app_sessions, public.app_user_channels, public.app_payments FROM anon, authenticated;
REVOKE ALL ON SEQUENCE public.app_payments_id_seq FROM anon, authenticated;

-- Les appels ne sont plus lisibles avec la clé publique : l'application passe par app_calls / app_history (compte + canaux accordés).
DO $b$ BEGIN
  IF to_regclass('public.apk_calls') IS NOT NULL THEN
    EXECUTE 'DROP POLICY IF EXISTS apk_calls_lecture ON public.apk_calls';
    EXECUTE 'REVOKE ALL ON public.apk_calls FROM anon, authenticated';
  END IF;
END $b$;

-- ───────────── Fonctions internes ─────────────
CREATE OR REPLACE FUNCTION public._app_ms(t timestamptz) RETURNS bigint
LANGUAGE sql IMMUTABLE AS $$ SELECT CASE WHEN t IS NULL THEN 0 ELSE (extract(epoch FROM t) * 1000)::bigint END $$;

CREATE OR REPLACE FUNCTION public._app_hash(tok text) RETURNS text
LANGUAGE sql IMMUTABLE AS $$ SELECT encode(sha256(convert_to(tok, 'UTF8')), 'hex') $$;

-- session → utilisateur (id NULL si le jeton est inconnu ou expiré)
CREATE OR REPLACE FUNCTION public._app_auth(p_token text) RETURNS public.app_users
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, extensions AS $$
DECLARE u public.app_users; h text;
BEGIN
  IF p_token IS NULL OR length(p_token) < 20 THEN RETURN NULL; END IF;
  h := public._app_hash(p_token);
  SELECT au.* INTO u FROM public.app_sessions s JOIN public.app_users au ON au.id = s.user_id
   WHERE s.token_hash = h AND s.last_seen > now() - interval '180 days';
  IF NOT FOUND THEN RETURN NULL; END IF;
  UPDATE public.app_sessions SET last_seen = now() WHERE token_hash = h AND last_seen < now() - interval '10 minutes';
  RETURN u;
END $$;

-- catalogue des canaux accordés depuis le site (réglage « Appel APK ») + canaux déjà vus dans les appels
CREATE OR REPLACE FUNCTION public._app_channels() RETURNS TABLE(cid text, cname text)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE cfg jsonb;
BEGIN
  BEGIN
    SELECT s.value::jsonb INTO cfg FROM public.settings s WHERE s.key = 'apk_call_state';
  EXCEPTION WHEN OTHERS THEN cfg := NULL;
  END;
  RETURN QUERY
  WITH ids AS (
    SELECT j AS x FROM jsonb_array_elements_text(
      CASE WHEN jsonb_typeof(cfg->'config'->'channels') = 'array' THEN cfg->'config'->'channels' ELSE '[]'::jsonb END) AS j
    WHERE j NOT LIKE '@%'
    UNION
    SELECT c.channel_id FROM public.apk_calls c WHERE c.kind <> 'test' AND c.channel_id <> 'test'
  )
  SELECT i.x,
         coalesce(nullif(cfg->'channelTitles'->>i.x, ''),
                  (SELECT c2.channel_name FROM public.apk_calls c2 WHERE c2.channel_id = i.x AND c2.channel_name <> '' ORDER BY c2.id DESC LIMIT 1),
                  i.x)
  FROM ids i
  ORDER BY 2;
END $$;

-- état du compte renvoyé à l'application
CREATE OR REPLACE FUNCTION public._app_state_json(u public.app_users) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE act boolean; st text; chans jsonb;
BEGIN
  act := u.status = 'active' AND (u.is_admin OR (u.access_until IS NOT NULL AND u.access_until > now()));
  st := CASE WHEN u.status = 'active' AND NOT act THEN 'expired' ELSE u.status END;
  IF u.is_admin THEN
    SELECT coalesce(jsonb_agg(cname ORDER BY cname), '[]'::jsonb) INTO chans FROM public._app_channels();
  ELSE
    SELECT coalesce(jsonb_agg(coalesce(k.cname, uc.channel_id) ORDER BY coalesce(k.cname, uc.channel_id)), '[]'::jsonb) INTO chans
      FROM public.app_user_channels uc LEFT JOIN public._app_channels() k ON k.cid = uc.channel_id
     WHERE uc.user_id = u.id;
  END IF;
  RETURN jsonb_build_object('ok', true, 'is_admin', u.is_admin, 'active', act, 'status', st,
    'access_until_ms', public._app_ms(u.access_until), 'server_now_ms', public._app_ms(now()),
    'pending_proof', EXISTS (SELECT 1 FROM public.app_payments p WHERE p.user_id = u.id AND p.status = 'pending'),
    'channels', chans);
END $$;

-- code d'erreur si le compte ne peut pas lire les appels (NULL = autorisé)
CREATE OR REPLACE FUNCTION public._app_gate(u public.app_users) RETURNS text
LANGUAGE sql STABLE AS $$
  SELECT CASE WHEN u.status = 'blocked' THEN 'blocked'
              WHEN u.status = 'pending' THEN 'pending'
              WHEN NOT u.is_admin AND (u.access_until IS NULL OR u.access_until <= now()) THEN 'expire'
              ELSE NULL END $$;

CREATE OR REPLACE FUNCTION public._app_new_session(uid uuid) RETURNS text
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE tok text;
BEGIN
  tok := replace(gen_random_uuid()::text, '-', '') || replace(gen_random_uuid()::text, '-', '');
  DELETE FROM public.app_sessions WHERE last_seen < now() - interval '180 days';
  INSERT INTO public.app_sessions (token_hash, user_id) VALUES (public._app_hash(tok), uid);
  RETURN tok;
END $$;

-- ───────────── Fonctions appelées par l'application ─────────────
CREATE OR REPLACE FUNCTION public.app_register(p_email text, p_pin text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, extensions AS $$
DECLARE e text := lower(trim(coalesce(p_email, ''))); u public.app_users; first_acc boolean;
BEGIN
  IF length(e) > 200 OR e !~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$' THEN RETURN jsonb_build_object('ok', false, 'error', 'email_invalide'); END IF;
  IF p_pin IS NULL OR p_pin !~ '^[0-9]{4,8}$' THEN RETURN jsonb_build_object('ok', false, 'error', 'pin_invalide'); END IF;
  PERFORM pg_advisory_xact_lock(481516);
  IF EXISTS (SELECT 1 FROM public.app_users WHERE lower(email) = e) THEN RETURN jsonb_build_object('ok', false, 'error', 'email_existe'); END IF;
  first_acc := NOT EXISTS (SELECT 1 FROM public.app_users);   -- le premier compte créé devient l'administrateur
  INSERT INTO public.app_users (email, pin_hash, is_admin, status)
  VALUES (e, crypt(p_pin, gen_salt('bf', 8)), first_acc, CASE WHEN first_acc THEN 'active' ELSE 'pending' END)
  RETURNING * INTO u;
  RETURN public._app_state_json(u) || jsonb_build_object('token', public._app_new_session(u.id));
END $$;

CREATE OR REPLACE FUNCTION public.app_login(p_email text, p_pin text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public, extensions AS $$
DECLARE e text := lower(trim(coalesce(p_email, ''))); u public.app_users;
BEGIN
  SELECT * INTO u FROM public.app_users WHERE lower(email) = e;
  IF NOT FOUND THEN
    PERFORM crypt('0000', gen_salt('bf', 8));
    RETURN jsonb_build_object('ok', false, 'error', 'identifiants');
  END IF;
  IF u.locked_until IS NOT NULL AND u.locked_until > now() THEN
    RETURN jsonb_build_object('ok', false, 'error', 'verrouille', 'retry_s', ceil(extract(epoch FROM u.locked_until - now()))::int);
  END IF;
  IF p_pin IS NOT NULL AND u.pin_hash = crypt(p_pin, u.pin_hash) THEN
    UPDATE public.app_users SET fail_count = 0, locked_until = NULL WHERE id = u.id;
    RETURN public._app_state_json(u) || jsonb_build_object('token', public._app_new_session(u.id));
  END IF;
  UPDATE public.app_users
     SET fail_count   = CASE WHEN fail_count >= 4 THEN 0 ELSE fail_count + 1 END,
         locked_until = CASE WHEN fail_count >= 4 THEN now() + interval '10 minutes' ELSE locked_until END
   WHERE id = u.id;
  IF u.fail_count >= 4 THEN RETURN jsonb_build_object('ok', false, 'error', 'verrouille', 'retry_s', 600); END IF;
  RETURN jsonb_build_object('ok', false, 'error', 'identifiants');
END $$;

CREATE OR REPLACE FUNCTION public.app_state(p_token text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE u public.app_users;
BEGIN
  u := public._app_auth(p_token);
  IF u.id IS NULL THEN RETURN jsonb_build_object('ok', false, 'error', 'session'); END IF;
  RETURN public._app_state_json(u);
END $$;

CREATE OR REPLACE FUNCTION public.app_logout(p_token text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  DELETE FROM public.app_sessions WHERE token_hash = public._app_hash(coalesce(p_token, ''));
  RETURN jsonb_build_object('ok', true);
END $$;

-- nouveaux appels : seulement ceux des canaux accordés à ce compte (l'administrateur reçoit tout, y compris les tests du site)
CREATE OR REPLACE FUNCTION public.app_calls(p_token text, p_after bigint DEFAULT -1) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE u public.app_users; g text; mx bigint; arr jsonb;
BEGIN
  u := public._app_auth(p_token);
  IF u.id IS NULL THEN RETURN jsonb_build_object('ok', false, 'error', 'session'); END IF;
  g := public._app_gate(u);
  IF g IS NOT NULL THEN RETURN jsonb_build_object('ok', false, 'error', g); END IF;
  SELECT coalesce(max(id), 0) INTO mx FROM public.apk_calls;
  IF p_after IS NULL OR p_after < 0 THEN RETURN jsonb_build_object('ok', true, 'max_id', mx, 'calls', '[]'::jsonb); END IF;
  SELECT coalesce(jsonb_agg(to_jsonb(x) ORDER BY x.id), '[]'::jsonb) INTO arr FROM (
    SELECT c.id, c.game_number, c.suit, c.kind, c.channel_name, public._app_ms(c.created_at) AS at_ms
      FROM public.apk_calls c
     WHERE c.id > p_after
       AND (u.is_admin OR (c.kind <> 'test' AND EXISTS (SELECT 1 FROM public.app_user_channels uc WHERE uc.user_id = u.id AND uc.channel_id = c.channel_id)))
     ORDER BY c.id LIMIT 20) x;
  RETURN jsonb_build_object('ok', true, 'max_id', mx, 'calls', arr);
END $$;

CREATE OR REPLACE FUNCTION public.app_history(p_token text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE u public.app_users; g text; arr jsonb;
BEGIN
  u := public._app_auth(p_token);
  IF u.id IS NULL THEN RETURN jsonb_build_object('ok', false, 'error', 'session'); END IF;
  g := public._app_gate(u);
  IF g IS NOT NULL THEN RETURN jsonb_build_object('ok', false, 'error', g); END IF;
  SELECT coalesce(jsonb_agg(to_jsonb(x) ORDER BY x.id DESC), '[]'::jsonb) INTO arr FROM (
    SELECT c.id, c.game_number, c.suit, c.kind, c.channel_name, public._app_ms(c.created_at) AS at_ms
      FROM public.apk_calls c
     WHERE c.kind <> 'test'
       AND (u.is_admin OR EXISTS (SELECT 1 FROM public.app_user_channels uc WHERE uc.user_id = u.id AND uc.channel_id = c.channel_id))
     ORDER BY c.id DESC LIMIT 5) x;
  RETURN jsonb_build_object('ok', true, 'calls', arr);
END $$;

CREATE OR REPLACE FUNCTION public.app_submit_payment(p_token text, p_image text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE u public.app_users;
BEGIN
  u := public._app_auth(p_token);
  IF u.id IS NULL THEN RETURN jsonb_build_object('ok', false, 'error', 'session'); END IF;
  IF u.status <> 'active' THEN RETURN jsonb_build_object('ok', false, 'error', 'statut'); END IF;
  IF p_image IS NULL OR length(p_image) < 100 OR length(p_image) > 8000000 THEN RETURN jsonb_build_object('ok', false, 'error', 'image_invalide'); END IF;
  IF (SELECT count(*) FROM public.app_payments WHERE user_id = u.id AND status = 'pending') >= 3 THEN
    RETURN jsonb_build_object('ok', false, 'error', 'trop_de_demandes');
  END IF;
  INSERT INTO public.app_payments (user_id, image) VALUES (u.id, p_image);
  RETURN jsonb_build_object('ok', true);
END $$;

-- ───────────── Fonctions administrateur ─────────────
CREATE OR REPLACE FUNCTION public.adm_list(p_token text) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE a public.app_users; us jsonb; pr jsonb; ch jsonb;
BEGIN
  a := public._app_auth(p_token);
  IF a.id IS NULL THEN RETURN jsonb_build_object('ok', false, 'error', 'session'); END IF;
  IF NOT a.is_admin THEN RETURN jsonb_build_object('ok', false, 'error', 'admin'); END IF;
  SELECT coalesce(jsonb_agg(jsonb_build_object(
           'id', t.id, 'email', t.email, 'status', t.status, 'is_admin', t.is_admin,
           'created_ms', public._app_ms(t.created_at), 'access_until_ms', public._app_ms(t.access_until),
           'channels', coalesce((SELECT jsonb_agg(uc.channel_id ORDER BY uc.channel_id) FROM public.app_user_channels uc WHERE uc.user_id = t.id), '[]'::jsonb))
         ORDER BY (t.status = 'pending') DESC, t.created_at DESC), '[]'::jsonb) INTO us FROM public.app_users t;
  SELECT coalesce(jsonb_agg(jsonb_build_object(
           'id', p.id, 'user_id', p.user_id, 'email', t.email, 'status', p.status,
           'created_ms', public._app_ms(p.created_at), 'granted_hours', p.granted_hours)
         ORDER BY (p.status = 'pending') DESC, p.id DESC), '[]'::jsonb) INTO pr
    FROM (SELECT * FROM public.app_payments ORDER BY id DESC LIMIT 50) p JOIN public.app_users t ON t.id = p.user_id;
  SELECT coalesce(jsonb_agg(jsonb_build_object('id', cid, 'name', cname) ORDER BY cname), '[]'::jsonb) INTO ch FROM public._app_channels();
  RETURN jsonb_build_object('ok', true, 'users', us, 'proofs', pr, 'channels', ch);
END $$;

-- remplace les canaux d'un utilisateur (seuls les canaux connus du site sont acceptés)
CREATE OR REPLACE FUNCTION public._app_set_channels(uid uuid, p_channels jsonb) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF p_channels IS NULL OR jsonb_typeof(p_channels) <> 'array' THEN RETURN; END IF;
  DELETE FROM public.app_user_channels WHERE user_id = uid;
  INSERT INTO public.app_user_channels (user_id, channel_id)
  SELECT DISTINCT uid, v FROM jsonb_array_elements_text(p_channels) v WHERE v IN (SELECT cid FROM public._app_channels());
END $$;

CREATE OR REPLACE FUNCTION public.adm_user_status(p_token text, p_user uuid, p_action text, p_channels jsonb DEFAULT NULL) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE a public.app_users; t public.app_users;
BEGIN
  a := public._app_auth(p_token);
  IF a.id IS NULL THEN RETURN jsonb_build_object('ok', false, 'error', 'session'); END IF;
  IF NOT a.is_admin THEN RETURN jsonb_build_object('ok', false, 'error', 'admin'); END IF;
  SELECT * INTO t FROM public.app_users WHERE id = p_user;
  IF NOT FOUND THEN RETURN jsonb_build_object('ok', false, 'error', 'introuvable'); END IF;
  IF p_action = 'approve' THEN
    UPDATE public.app_users SET status = 'active',
           access_until = CASE WHEN access_until IS NULL OR access_until < now() THEN now() + interval '24 hours' ELSE access_until END
     WHERE id = t.id;
    PERFORM public._app_set_channels(t.id, p_channels);
  ELSIF p_action = 'unblock' THEN
    UPDATE public.app_users SET status = 'active' WHERE id = t.id;
  ELSIF p_action = 'block' THEN
    IF t.id = a.id OR t.is_admin THEN RETURN jsonb_build_object('ok', false, 'error', 'soi_meme'); END IF;
    UPDATE public.app_users SET status = 'blocked' WHERE id = t.id;
  ELSE
    RETURN jsonb_build_object('ok', false, 'error', 'action');
  END IF;
  RETURN jsonb_build_object('ok', true);
END $$;

CREATE OR REPLACE FUNCTION public.adm_set_channels(p_token text, p_user uuid, p_channels jsonb) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE a public.app_users;
BEGIN
  a := public._app_auth(p_token);
  IF a.id IS NULL THEN RETURN jsonb_build_object('ok', false, 'error', 'session'); END IF;
  IF NOT a.is_admin THEN RETURN jsonb_build_object('ok', false, 'error', 'admin'); END IF;
  IF NOT EXISTS (SELECT 1 FROM public.app_users WHERE id = p_user) THEN RETURN jsonb_build_object('ok', false, 'error', 'introuvable'); END IF;
  PERFORM public._app_set_channels(p_user, p_channels);
  RETURN jsonb_build_object('ok', true);
END $$;

CREATE OR REPLACE FUNCTION public.adm_grant(p_token text, p_user uuid, p_hours int, p_proof bigint DEFAULT NULL) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE a public.app_users; t public.app_users; total int := 0; until_ts timestamptz;
BEGIN
  a := public._app_auth(p_token);
  IF a.id IS NULL THEN RETURN jsonb_build_object('ok', false, 'error', 'session'); END IF;
  IF NOT a.is_admin THEN RETURN jsonb_build_object('ok', false, 'error', 'admin'); END IF;
  IF p_hours IS NULL OR p_hours < 1 OR p_hours > 720 THEN RETURN jsonb_build_object('ok', false, 'error', 'heures'); END IF;
  SELECT * INTO t FROM public.app_users WHERE id = p_user;
  IF NOT FOUND THEN RETURN jsonb_build_object('ok', false, 'error', 'introuvable'); END IF;
  UPDATE public.app_users
     SET access_until = greatest(coalesce(access_until, now()), now()) + p_hours * interval '1 hour',
         status = CASE WHEN status = 'pending' THEN 'active' ELSE status END
   WHERE id = t.id RETURNING access_until INTO until_ts;
  IF p_proof IS NOT NULL THEN
    UPDATE public.app_payments SET status = 'done', granted_hours = granted_hours + p_hours
     WHERE id = p_proof AND user_id = t.id RETURNING granted_hours INTO total;
  END IF;
  RETURN jsonb_build_object('ok', true, 'access_until_ms', public._app_ms(until_ts), 'granted_total', coalesce(total, 0));
END $$;

CREATE OR REPLACE FUNCTION public.adm_proof(p_token text, p_id bigint) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE a public.app_users; img text;
BEGIN
  a := public._app_auth(p_token);
  IF a.id IS NULL THEN RETURN jsonb_build_object('ok', false, 'error', 'session'); END IF;
  IF NOT a.is_admin THEN RETURN jsonb_build_object('ok', false, 'error', 'admin'); END IF;
  SELECT image INTO img FROM public.app_payments WHERE id = p_id;
  IF img IS NULL THEN RETURN jsonb_build_object('ok', false, 'error', 'introuvable'); END IF;
  RETURN jsonb_build_object('ok', true, 'image', img);
END $$;

-- ───────────── Droits : fonctions internes fermées, fonctions de l'application ouvertes à la clé publique ─────────────
REVOKE ALL ON FUNCTION public._app_ms(timestamptz), public._app_hash(text), public._app_auth(text), public._app_channels(),
  public._app_state_json(public.app_users), public._app_gate(public.app_users), public._app_new_session(uuid),
  public._app_set_channels(uuid, jsonb) FROM PUBLIC, anon, authenticated;

REVOKE ALL ON FUNCTION public.app_register(text, text), public.app_login(text, text), public.app_state(text), public.app_logout(text),
  public.app_calls(text, bigint), public.app_history(text), public.app_submit_payment(text, text),
  public.adm_list(text), public.adm_user_status(text, uuid, text, jsonb), public.adm_set_channels(text, uuid, jsonb),
  public.adm_grant(text, uuid, int, bigint), public.adm_proof(text, bigint) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.app_register(text, text), public.app_login(text, text), public.app_state(text), public.app_logout(text),
  public.app_calls(text, bigint), public.app_history(text), public.app_submit_payment(text, text),
  public.adm_list(text), public.adm_user_status(text, uuid, text, jsonb), public.adm_set_channels(text, uuid, jsonb),
  public.adm_grant(text, uuid, int, bigint), public.adm_proof(text, bigint) TO anon, authenticated;

NOTIFY pgrst, 'reload schema';
