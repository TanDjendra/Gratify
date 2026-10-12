# Tampilan Pustaka — 8 Oktober 2026

Halaman Pustaka kini memisahkan akses cepat dari isi koleksi. Lagu disukai, musik diunduh, dan lagu baru ditambahkan tersedia di atas; tombol membuat playlist dan menambah artis berada di bagian koleksi. Koneksi YouTube ditampilkan lebih ringkas di bawah koleksi, sehingga tidak mendominasi layar pertama. Tema dan navigasi aplikasi dipertahankan.

## Perubahan

Label lama “Riwayat Putar” mengarah ke `RecentlySongsDestination`, yang mengambil lagu berdasarkan `inLibrary`, bukan waktu pemutaran. Label akses cepat kini “Baru ditambahkan” agar sesuai dengan fungsi sebenarnya.

- Daftar dan grid memakai jarak yang konsisten, gambar pengganti saat sampul gagal dimuat, ikon artis, teks yang dibatasi, serta tombol opsi playlist yang dapat ditemukan tanpa tekan lama.
- Filter, pilihan tampilan, dan pilihan urutan tetap memakai preferensi sebelumnya. Urutan baru ditambahkan memakai tanggal koleksi sebenarnya; identitas item memakai ID sehingga nama/sampul yang sama tidak menyebabkan benturan.
- Sematkan/lepas sematan kini benar-benar menyimpan pilihan dan mendahulukan playlist yang disematkan. Tidak ada perubahan skema basis data.
- Tampilan memuat, koleksi kosong, dan gagal memuat mempunyai pesan/tindakan yang sesuai. Pengambilan koleksi menangani kegagalan dan tidak membuat pengamat artis tambahan setiap kali halaman dibuka.
- Formulir membuat playlist menampilkan kesalahan di bawah input, mendukung tombol selesai pada keyboard, dan merapikan spasi nama. Pengambilan string yang memblokir UI dihapus.
- Teks baru tersedia dalam bahasa Inggris dan Indonesia. Isi koleksi dibatasi hingga 1060 dp pada layar lebar.

## Perbaikan kompatibilitas yang ditemukan saat pengujian

Pengujian formulir menemukan `AbstractMethodError` pada `OutlinedTextFieldDefaults`: Material3 sebelumnya tidak cocok dengan Foundation 1.12.1. Material3 multiplatform diselaraskan dari 1.12.0-alpha01 ke 1.12.0-alpha03, dan Android dari 1.5.0-alpha21 ke 1.5.0-alpha22. Pasangan ini tercantum dalam [dependensi resmi Compose Multiplatform 1.12.1](https://kotlinlang.org/docs/multiplatform/whats-new-compose-112.html#dependencies). Formulir kemudian lolos render/interaksi JVM dan pengujian pada emulator Android.

## Verifikasi

- Empat pengujian `LibraryCollectionRenderTest` lulus: interaksi filter/tampilan/opsi/akses cepat dengan nama duplikat, keadaan kosong/memuat/gagal pada lebar 320 dp, urutan sematan dan batas desktop 1500 px, serta validasi dan nama formulir.
- Tiga pengujian regresi `CollectionDetailRenderTest` lulus.
- `LibraryPreferencesPersistenceTest` lulus untuk filter, tampilan, urutan, dan sematan setelah DataStore dibuka ulang.
- Build debug Android dan kompilasi aplikasi desktop berhasil.
- Pada emulator Android 17 dengan halaman memori 16 KB, halaman produksi dibuka melalui host debug `DesignPreviewActivity --ez live true`. Daftar/grid dan navigasi ketiga akses cepat diperiksa, sematan bertahan setelah proses dimulai ulang, dan nama kosong tetap di formulir dengan pesan kesalahan. Sematan percobaan dilepas kembali.
- Audit ulang graf dependensi memeriksa 676 koordinat Maven unik: tidak ada kecocokan advisori OSV dan tidak ada batch pemeriksaan yang gagal. Ini tidak menjamin seluruh aplikasi bebas celah.

Log, XML interaksi, gambar, dan audit tersedia di `artifacts/library-screen-audit-2026-10-08/`. Perubahan masih lokal; belum diterbitkan. Pemeriksaan ini mencakup halaman Pustaka dan regresi terkait, bukan audit ulang visual seluruh aplikasi atau koneksi akun YouTube nyata.
