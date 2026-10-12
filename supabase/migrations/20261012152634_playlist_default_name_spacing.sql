-- Older default titles contain a space after No.; count them as the same number
-- without rewriting any user-selected titles.
DO $migration$
DECLARE source text;
BEGIN
 SELECT pg_get_functiondef('gratify_workspace_private.api(text,jsonb)'::regprocedure) INTO source;
 IF position('lower(btrim(title))=lower(prefix||n)' IN source)=0 OR position('lower(btrim(t.title))=lower(prefix||n)' IN source)=0
 THEN RAISE EXCEPTION 'Default naming implementation changed; review migration'; END IF;
 source:=replace(source,'lower(btrim(title))=lower(prefix||n)',
  'lower(regexp_replace(btrim(title),''\s'','''',''g''))=lower(regexp_replace(prefix||n,''\s'','''',''g''))');
 source:=replace(source,'lower(btrim(t.title))=lower(prefix||n)',
  'lower(regexp_replace(btrim(t.title),''\s'','''',''g''))=lower(regexp_replace(prefix||n,''\s'','''',''g''))');
 EXECUTE source;
END $migration$;
