"""Archive the actual Android sources, never the workspace's credentials or audit data."""
from pathlib import Path
import hashlib, json, os, re, zipfile

out = Path(__file__).resolve().parent
root = out.parents[1]
directories = ('androidApp', 'composeApp', 'core', 'gradle', 'desktopApp', 'crashlytics', 'crashlytics-empty')
excluded = {'build', '.git', '.gradle', '.kotlin', '.idea', 'cache', 'node_modules', '__pycache__', 'kcef-bundle'}
allowed = {'.kt', '.kts', '.java', '.xml', '.toml', '.json', '.png', '.jpg', '.jpeg', '.webp', '.svg',
           '.ttf', '.otf', '.md', '.txt', '.properties', '.jar', '.aar', '.so', '.pro', '.c', '.h', '.cpp',
           '.sh', '.bat', '.py', '.yaml', '.yml', '.html', '.css', '.js', '.map', '.ico', '.icns'}
files = []
for directory in directories:
    for base, folders, names in os.walk(root / directory):
        folders[:] = sorted(folder for folder in folders if folder not in excluded)
        for name in sorted(names):
            path = Path(base) / name
            lower = name.lower()
            if path.suffix.lower() not in allowed and lower not in {'license', 'notice', 'copying'}:
                continue
            if lower in {'local.properties', 'sentry.properties'} or any(word in lower for word in ('keystore', 'password', 'service-account', 'service_account', 'credentials')):
                continue
            assert not path.is_symlink() and path.resolve().is_relative_to(root.resolve())
            files.append(path)
for name in ('settings.gradle.kts', 'build.gradle.kts', 'gradle.properties', 'gradlew', 'gradlew.bat',
             'LICENSE', 'README.md', '.gitmodules', 'build_and_sign_apk.sh',
             'scripts/gratify_delta.py', 'scripts/prepare-update-release.py'):
    path = root / name
    if path.is_file():
        files.append(path)

files = sorted(set(files))
text_extensions = allowed - {'.jar', '.aar', '.so', '.png', '.jpg', '.jpeg', '.webp', '.ttf', '.otf', '.ico', '.icns'}
for path in files:
    if path.suffix.lower() in text_extensions or path.name in {'LICENSE', 'gradlew', '.gitmodules'}:
        content = path.read_text(encoding='utf-8', errors='replace')
        assert not re.search(r'gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{50,}|-----BEGIN (?:RSA |EC )?PRIVATE KEY-----', content), 'Private credential pattern detected; source publication stopped.'

archive = out / 'Gratify-2.1.5-source.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as package:
    for path in files:
        package.write(path, 'Gratify-2.1.5/' + path.relative_to(root).as_posix())
    package.writestr('Gratify-2.1.5/RELEASE-SOURCE.md', '''# Gratify 2.1.5 (code 66)

This archive contains the Android application sources used for the accompanying APK,
including local changes. Use this archive as the source corresponding to this binary.
The automatically generated GitHub tag archive may represent an earlier repository commit.

Build with JDK 21 or newer, Android SDK 37 and the bundled Gradle wrapper:
`./gradlew :androidApp:assembleRelease -PisFullBuild=true`

Create a private local.properties with sdk.dir, SUPABASE_URL and SUPABASE_KEY
(the application's publishable/anon key), and SENTRY_DSN for the full build.
Signing requires your own private keystore and passwords; none are distributed here.
Firebase client configuration is included; server credentials and user data are excluded.
Read the project's build files for optional desktop/media dependency configuration.

Collaboration, Blend and folders are retained in source but disabled through
PlaylistFeaturePolicy.experimentalEnabled=false. SimpMusic credits and GPL-3.0
remain included. Developer links use https://tanheradhe.my.id.
''')
record = {'archive': archive.name, 'files': len(files) + 1, 'size_bytes': archive.stat().st_size,
          'sha256': hashlib.sha256(archive.read_bytes()).hexdigest(),
          'source_files': [path.relative_to(root).as_posix() for path in files],
          'private_signing_credentials_included': False, 'user_data_included': False}
(out / 'source-snapshot.json').write_text(json.dumps(record, indent=2), encoding='utf-8')
print(json.dumps({key: value for key, value in record.items() if key != 'source_files'}))
