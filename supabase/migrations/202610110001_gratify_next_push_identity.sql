-- Accept the successor app without changing device ownership, RLS or RPC grants.
BEGIN;

ALTER TABLE public.push_devices
  DROP CONSTRAINT push_devices_package_name_check;
ALTER TABLE public.push_devices
  ADD CONSTRAINT push_devices_package_name_check CHECK (
    package_name IN ('com.tan.gratify', 'com.tan.gratify.dev',
                     'com.tan.gratify.next', 'com.tan.gratify.next.dev')
  );

DO $migration$
DECLARE
  definition text;
  old_packages constant text := '''com.tan.gratify'',''com.tan.gratify.dev''';
  new_packages constant text := '''com.tan.gratify'',''com.tan.gratify.dev'',''com.tan.gratify.next'',''com.tan.gratify.next.dev''';
BEGIN
  definition := pg_catalog.pg_get_functiondef(
    'public.gratify_register_push_device(uuid,text,text)'::regprocedure
  );
  IF pg_catalog.strpos(definition, 'p_package NOT IN (' || new_packages || ')') > 0 THEN
    RETURN;
  END IF;
  IF pg_catalog.strpos(definition, 'p_package NOT IN (' || old_packages || ')') = 0 THEN
    RAISE EXCEPTION 'Unexpected push registration package validation; migration cancelled';
  END IF;
  EXECUTE pg_catalog.replace(definition,
    'p_package NOT IN (' || old_packages || ')',
    'p_package NOT IN (' || new_packages || ')');
END
$migration$;

COMMIT;
