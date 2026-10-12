# Audit profil dan alur melihat akun pengguna

Tanggal: 3 Oktober 2026. Audit mencakup profil sendiri, profil pengguna lain, pencarian pengguna, daftar pengikut/mengikuti, QR dan tautan profil, playlist, serta kaitannya dengan Friends. Aplikasi menggunakan Compose Multiplatform dan Supabase.

Kesimpulan: alur dasar pencarian → profil dan Friends → profil tersedia, tetapi keadaan gagal/memuat belum andal. Temuan paling mendesak adalah catatan Friends yang tersedia melalui profil publik tanpa pembatasan pertemanan atau kedaluwarsa di server. Aturan penyembunyian aktivitas dan playlist yang dipajang sudah aktif di server.

## Bukti dan batas audit

- Memeriksa kode aplikasi, repository, navigasi, model, dan migrasi.
- Membaca definisi view, kebijakan akses, izin, dan status RLS di Supabase aktif `bnabldxsqpvkyqpjcsdv`. Tidak membaca isi catatan pengguna atau mengubah skema/data.
- 25 pemeriksaan lulus: 7 pemeriksaan audit, 8 regresi profil/discovery, dan 10 regresi tautan desktop. Tujuh pemeriksaan audit terdiri dari **enam reproduksi cacat yang masih ada** dan satu verifikasi penyembunyian aktivitas. Hasil ini membuktikan temuan, bukan berarti seluruh fitur profil sudah benar.
- Reproduksi memakai akun/data sintetis dan repository pengganti; tidak mengikuti akun, mengirim pesan, atau membuat playlist di server aktif.
- Belum menjalankan pengujian layar profil Android dengan dua akun nyata atau pengujian tautan profil saat aplikasi tertutup/terbuka. Risiko tata letak ditandai sebagai usulan pemeriksaan visual.
- Tidak membangun APK baru dan tidak menerapkan perbaikan dalam audit ini. Berkas pengujian sementara sudah dikeluarkan dari suite normal; salinannya disimpan sebagai bukti audit.

## Temuan yang perlu diperbaiki

### F01 · P1 · Catatan Friends tersedia untuk pembaca profil publik

**Terkonfirmasi di server aktif.** View `public_profiles` memberikan akses baca kepada `anon` dan `authenticated`, lalu mengembalikan `p.note` dan `p.note_updated_at` langsung. Tidak ada syarat pertemanan maupun waktu kedaluwarsa. Di UI Friends, catatan ditampilkan sebagai konten 24 jam; kedaluwarsa hanya diperiksa di aplikasi. Catatan yang tidak lagi tampil setelah 24 jam masih dapat diambil melalui API jika masih tersimpan.

Bukti: [proyeksi profil publik](/D:/Tan/Gratify/supabase/migrations/202610010002_profile_privacy.sql:57), [masa berlaku di UI](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/friends/FriendsActivityScreen.kt:193), dan [metadata server aktif](/D:/Tan/Gratify/artifacts/profile-audit/live-metadata.json).

Usulan: tentukan penonton catatan secara eksplisit, lalu batasi pembaca dan masa berlaku 24 jam di server. Jika catatan hanya untuk teman, jangan menyertakannya dalam proyeksi publik tanpa syarat. Jika pengguna boleh memilih publik, penonton harus disimpan dan ditegakkan di server, sama seperti story.

### F02 · P2 · Loading, profil tidak ditemukan, dan error bercampur

**Direproduksi.** Status `isLoading=false` ditentukan oleh selesainya permintaan playlist, walaupun permintaan profil belum selesai. UI dapat menampilkan “Pengguna tidak ditemukan” sementara data profil masih dalam perjalanan. Repository mengubah kegagalan jaringan menjadi `null`; ViewModel kemudian membuat akun “Teman Gratify” dengan ID yang diminta. Akun tidak ada dan koneksi gagal menjadi sulit dibedakan dari profil yang valid.

Saat pemuatan ulang gagal, aktivitas lama juga dapat tetap tampil di bawah profil pengganti tersebut. UI aktivitas hanya memeriksa ada tidaknya judul, tanpa memeriksa kembali izin profil saat ini.

Bukti: [kegagalan diubah menjadi null](/D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/UserRepositoryImpl.kt:49), [profil pengganti](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/UserProfileViewModel.kt:105), [loading selesai oleh playlist](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/UserProfileViewModel.kt:167), [aktivitas tanpa pemeriksaan izin UI](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/social/UserProfileScreen.kt:249).

Usulan: pisahkan keadaan loading, berhasil, akun tidak ditemukan, dan error. Identitas profil menentukan keadaan utama; bagian playlist/aktivitas punya status masing-masing. Bersihkan aktivitas saat hasil profil tidak dapat digunakan, dan sediakan tombol coba lagi serta kembali pada layar gagal.

### F03 · P2 · Tautan dan QR profil belum ditangani konsisten

**Terkonfirmasi dari kode.** Tautan berbagi memakai `https://gratify.org/app/profile/{id}`. QR memakai `gratify://profile/{id}`, sedangkan navigasi mendaftarkan `gratify://app/profile/{id}`. Pemindai internal memang mengekstrak ID secara manual, sehingga bisa bekerja; pemindai luar tidak mendapat jalur yang sama.

Penanganan intent di `MainScreen` juga belum memiliki cabang `profile` dan mengonsumsi intent Gratify sebelum jatuh ke pesan tautan tidak didukung. Navigasi memiliki deep link HTTPS, tetapi itu tidak menggantikan kebutuhan penanganan intent baru saat aplikasi sudah berjalan. Perilaku Android saat membuka tautan dari luar perlu diuji dalam kedua keadaan aplikasi tersebut.

Pemindai menerima hampir semua teks yang tidak kosong sebagai ID, termasuk isi QR yang bukan profil Gratify.

Bukti: [tautan berbagi](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/home/ProfileScreen.kt:1394), [QR](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/home/ProfileScreen.kt:1772), [pendaftaran navigasi](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/navigation/graph/ListScreenGraph.kt:92), [penanganan intent](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/MainScreen.kt:186), [pemindai](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/home/ProfileScreen.kt:197).

Usulan: satu pembaca tautan/QR yang memvalidasi domain, format rute, dan ID; dukung QR lama agar pengguna tidak kehilangan akses.

### F04 · P2 · “Terakhir diputar” dapat menampilkan lagu yang belum pernah diputar

**Direproduksi.** Saat tidak ada aktivitas yang masih aktif, ViewModel mengambil playlist publik pertama, kemudian lagu pertama di dalamnya. Lagu tersebut diberi label “Terakhir diputar”, padahal data playlist bukan riwayat pemutaran.

Bukti: [fallback playlist](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/UserProfileViewModel.kt:199), [label aktivitas](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/social/UserProfileScreen.kt:287).

Usulan: hapus fallback ini dari aktivitas pemutaran. Tampilkan keadaan kosong, atau buat riwayat pemutaran dengan persetujuan privasi jika fitur tersebut memang diinginkan. Lagu dari playlist bisa menjadi rekomendasi, dengan label yang sesuai.

### F05 · P2 · Jumlah pengikut dan keadaan daftar dapat menyesatkan

**Aturan server terkonfirmasi; loading daftar direproduksi.** Jumlah dihitung dari ukuran daftar relasi yang dapat dibaca oleh penonton. Kebijakan server hanya mengizinkan relasi ketika penonton adalah salah satu pihak atau kedua pihak membuka daftar pengikutnya. Orang lain dapat melihat jumlah yang lebih kecil daripada pemilik; itu adalah jumlah relasi terlihat, bukan jumlah pengikut total.

Daftar juga menutup loading ketika permintaan “mengikuti” selesai walaupun permintaan “pengikut” masih berlangsung. Kegagalan repository menjadi daftar kosong, sehingga UI dapat mengatakan “Belum ada pengikut” saat jaringan gagal.

Bukti: [perhitungan jumlah](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/UserProfileViewModel.kt:140), [aturan relasi](/D:/Tan/Gratify/supabase/migrations/202610010002_profile_privacy.sql:65), [loading daftar](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/FollowListViewModel.kt:29), [error menjadi kosong](/D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/UserRepositoryImpl.kt:160).

Usulan: tetap lindungi identitas relasi privat. Jika angka total boleh ditampilkan, gunakan hitungan terkontrol di server; jika tidak, jelaskan bahwa yang tampil adalah relasi terlihat. Pisahkan loading/error setiap tab dan tambahkan paginasi.

### F06 · P2 · Artis “baru diputar” sebenarnya artis yang diikuti, dan bisa tertinggal

**Terkonfirmasi dari kode.** Profil sendiri menampilkan `getFollowedArtists()` sebagai “Artis yang baru diputar”. Profil pengguna lain menyebut data yang sama “Artis yang diikuti”. Ketika pengguna berhenti mengikuti artis terakhir, daftar lokal menjadi kosong tetapi sinkronisasi sengaja dilewati, sehingga daftar lama di server tidak dibersihkan.

Bukti: [sumber dan syarat sinkronisasi](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/home/ProfileScreen.kt:166), [label profil sendiri](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/home/ProfileScreen.kt:870), [label profil orang lain](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/social/UserProfileScreen.kt:359).

Usulan: pilih arti fitur yang benar dan samakan label serta pengaturan privasinya. Sinkronkan daftar kosong juga agar penghapusan artis terakhir sampai ke server.

### F07 · P2 · Playlist profil hanya menawarkan simpan; kegagalan tidak diberitahukan

**Terkonfirmasi dari kode.** Mengetuk playlist membuka sheet dengan pilihan “Tambahkan ke Pustaka”. Rute detail playlist sudah tersedia tetapi tidak digunakan oleh baris profil. Ketika penyimpanan gagal atau playlist sudah tersimpan, callback error hanya menutup sheet dan membuang pesan. Tidak ada keadaan sedang menyimpan di tombol tersebut.

Bukti: [aksi baris playlist](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/social/UserProfileScreen.kt:465), [pesan error dibuang](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/social/UserProfileScreen.kt:583).

Usulan: ketukan utama membuka detail/isi playlist; simpan menjadi aksi tambahan. Tampilkan hasil penyimpanan, error yang dapat dicoba lagi, dan cegah ketukan berulang selama proses.

### F08 · P2 · Tombol Ikuti tidak memberi arahan kepada pengguna belum login

**Direproduksi.** UI menampilkan tombol Ikuti, tetapi ViewModel langsung keluar jika tidak ada akun aktif. Tidak ada ajakan login atau perubahan yang menjelaskan hasil ketukan. Pencegahan permintaan follow ganda sudah ada, tetapi keadaan pending belum diekspos ke tombol.

Bukti: [tombol Ikuti](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/social/UserProfileScreen.kt:317), [keluar tanpa akun](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/UserProfileViewModel.kt:232).

Usulan: arahkan ke login sambil mempertahankan profil tujuan, lalu tampilkan keadaan mengikuti/memproses/gagal yang jelas.

### F09 · P2/P3 · Pencarian akun menyamarkan error dan belum mendukung identitas unik

**Terkonfirmasi dari kode.** Pencarian hanya mencocokkan `display_name`; awalan `@` dihapus lalu tetap mencari nama tampilan. Hasil hanya berisi avatar dan nama sehingga dua akun dengan nama sama sulit dibedakan. Error menjadi daftar kosong dan tidak diteruskan sebagai kegagalan pencarian. Tidak ada paginasi akun.

Bukti: [query dan penanganan error](/D:/Tan/Gratify/core/data/src/commonMain/kotlin/com/tan/data/repository/UserRepositoryImpl.kt:78), [awalan @](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/SearchViewModel.kt:265), [hasil akun](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/ui/screen/search/SearchScreen.kt:771).

Usulan: bedakan kosong/error dan sediakan paginasi. Username unik `@nama` dapat membantu menemukan akun, tetapi itu penambahan fitur dan skema yang memerlukan pilihan pengguna terlebih dahulu.

### F10 · P3 · Pembukaan profil membuat permintaan dan penulisan tambahan

**Pembacaan ganda direproduksi; penulisan terlihat di kode.** Profil dibaca dua kali, sekali untuk identitas dan sekali untuk aktivitas. Kedua hasil dapat datang pada waktu berbeda. Jumlah pengikut juga mengambil daftar profil lengkap hanya untuk menghitung ukurannya. Membuka profil sendiri melakukan upsert profil penuh yang baru dibaca, walaupun pengguna belum mengedit apa pun.

Bukti: [pembacaan pertama](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/UserProfileViewModel.kt:87), [pembacaan kedua](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/UserProfileViewModel.kt:176), [upsert saat membaca](/D:/Tan/Gratify/composeApp/src/commonMain/kotlin/com/tan/gratify/viewModel/UserProfileViewModel.kt:116).

Usulan: satu snapshot profil untuk seluruh bagian, hitungan server terkontrol, dan penulisan hanya saat edit/sinkronisasi yang memang perlu. Penulisan snapshot lama berpotensi bertabrakan dengan pembaruan pemutaran atau perangkat lain; kehilangan data akibat tabrakan ini belum direproduksi. Audit ini belum mengukur konsumsi baterai atau waktu render.

## Usulan UI/UX yang perlu persetujuan

- **Hubungkan profil dengan Friends:** tombol Ikuti dan Pesan, akses story aktif, serta menu berbagi profil. Saat ini profil orang lain hanya menyediakan Ikuti. Integrasi Pesan/Story adalah perluasan alur, bukan bukti backend pesan gagal.
- **Satukan tampilan profil sendiri, pratinjau publik, dan profil orang lain:** komponen profil yang sama dengan aksi berbeda sesuai pemilik/penonton. Pratinjau saat ini memakai layar profil sendiri dan tombol Ikuti yang masih TODO; bukan interaksi akun lain yang sesungguhnya.
- **Rapikan header untuk nama panjang dan huruf besar:** avatar 120 dp berada di samping kolom nama tanpa pembagian lebar yang fleksibel, dan statistik dipaksa dalam satu baris. Ada risiko melebar di layar kecil; perlu bukti render 320/360 dp dengan huruf 150–200% sebelum menetapkan ukuran baru.
- **Samakan istilah:** “yang dipajang di profil” berbeda dari “playlist publik”; “artis diikuti” berbeda dari “baru diputar”. Gunakan pesan privasi yang mempertahankan struktur bagian profil.
- **Perjelas pengaturan awal:** switch privasi memakai nilai UI awal TRUE walaupun nilai server awal false; tampilkan keadaan sedang memuat sampai nilai terkonfirmasi. Ini risiko tampilan keliru, bukan bukti data tersembunyi dibuka di server.
- **Perbaiki “Salin link”:** aksinya sekarang membuka alur berbagi. Gunakan clipboard untuk salin dan aksi terpisah untuk berbagi.
- **Blokir/laporkan akun:** dapat dipertimbangkan sebelum fitur sosial dipakai luas, tetapi belum termasuk alur/backend yang tersedia dan memerlukan keputusan fitur tersendiri.

## Bagian yang sudah sesuai

- Profil dasar dan pengaturan isi dipisahkan; bagian artis/playlist tersembunyi memiliki placeholder di profil orang lain.
- Server menyamarkan aktivitas dan artis untuk bukan pemilik ketika `show_recent_artists=false`.
- Tabel profil asli mengaktifkan RLS, baca pengguna terautentikasi dibatasi pemilik, dan tidak memberikan izin baca tabel kepada anon.
- Playlist publik dan pajangan profil sudah terpisah. RPC pajangan mewajibkan playlist publik, dipajang, dan pemilik atau izin `show_playlists=true`.
- Avatar dari Google tidak diterima sebagai foto unggahan Gratify oleh repository/komponen avatar.
- Pencarian memiliki pembatalan permintaan lama dan batas waktu di ViewModel.
- Pencegahan follow ganda dan kegagalan simpan privasi sudah ditangani, meskipun keadaan proses tombol perlu diperjelas.
- Ruang untuk mini player/navigasi sudah dicadangkan melalui `BottomSafeContent`; audit tidak menemukan dasar untuk menyatakan profil sekarang tertutup player.

## Urutan perbaikan yang disarankan

1. Putuskan penonton catatan, lalu benahi pembatasan dan kedaluwarsa di server.
2. Perbaiki loading/error/tidak ditemukan dan aktivitas lama; hapus aktivitas yang disimpulkan dari playlist.
3. Samakan tautan/QR dan perilaku membuka profil dari luar aplikasi.
4. Perbaiki jumlah/daftar pengikut, follow tanpa login, dan feedback simpan playlist.
5. Samakan arti artis, bersihkan sinkronisasi daftar kosong, dan kurangi permintaan/penulisan tambahan.
6. Setelah bug stabil, rapikan profil serta sambungkan Pesan/Story sesuai pilihan pengguna.

Bukti: [hasil pengujian](/D:/Tan/Gratify/artifacts/profile-audit/profile-audit-tests.log), [reproduksi audit](/D:/Tan/Gratify/artifacts/profile-audit/ProfileAuditReproductionTest.kt), [metadata server](/D:/Tan/Gratify/artifacts/profile-audit/live-metadata.json), dan XML di `artifacts/profile-audit/test-results/`.
