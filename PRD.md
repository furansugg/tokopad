# Tokopad — Product Requirements Document

Status: Draft MVP

Platform: Android

Repository: https://github.com/furansugg/tokopad.git

## 1. Ringkasan

Tokopad adalah aplikasi Android ringan untuk mencatat penjualan toko, melayani transaksi kasir, mengelola pelanggan, serta mencetak nota. Aplikasi mendukung produk fisik dan digital dalam satu transaksi.

Antarmuka minimalis, nyaman, informatif, dan memakai bahasa sehari-hari. Operasional utama berjalan tanpa internet. Bahasa awal adalah Inggris; bahasa Indonesia tersedia melalui pengaturan.

## 2. Tujuan

- Mempercepat pencatatan penjualan dengan langkah sesedikit mungkin.
- Mendukung toko dengan produk fisik dan beragam layanan digital.
- Menghasilkan nota yang mengikuti isi transaksi, tanpa informasi kosong.
- Menjaga data transaksi tetap konsisten saat aplikasi atau printer bermasalah.
- Mengutamakan ukuran aplikasi kecil, respons cepat, serta penggunaan memori dan baterai rendah.

## 3. Asumsi MVP

- Satu toko dan satu perangkat; tanpa akun, server, atau sinkronisasi.
- Mata uang IDR dengan presisi rupiah bulat.
- Penjualan produk digital dicatat manual, bukan diproses melalui distributor.
- Pembayaran tunai atau nontunai dicatat manual, tanpa verifikasi penyedia pembayaran.
- Printer awal menggunakan Bluetooth Classic dengan perintah ESC/POS.
- Usulan minimum Android 8.0/API 26; keputusan final mengikuti perangkat toko yang ditargetkan.

## 4. Fitur MVP

### 4.1. Dashboard

- Ringkasan omzet, jumlah transaksi, dan rata-rata nilai transaksi hari ini.
- Filter rentang tanggal dan daftar transaksi terbaru.
- Akses cepat ke kasir.
- Omzet adalah total setelah diskon dari transaksi selesai, tidak termasuk transaksi dibatalkan; bukan laba atau kas masuk bersih.
- Kondisi kosong memberi petunjuk tindakan singkat, tanpa grafik dekoratif.

### 4.2. Kasir

- Cari produk aktif, pilih item, ubah jumlah, dan hapus item dari keranjang.
- Gabungkan produk fisik dan digital dalam satu transaksi.
- Pilih pelanggan secara opsional; transaksi tanpa pelanggan tetap diperbolehkan.
- Tampilkan kolom digital hanya ketika jenis produk memerlukannya.
- Sediakan diskon nominal per transaksi.
- Pilih pembayaran tunai atau nontunai dan tampilkan total serta kembalian.
- Pembayaran tunai harus mencukupi total; pembayaran nontunai dicatat sebesar total.
- Tampilkan ringkasan sebelum transaksi diselesaikan.
- Simpan transaksi dan perubahan stok secara atomik.
- Cegah transaksi ganda akibat penekanan tombol berulang.
- Transaksi disimpan sebelum pencetakan; kegagalan cetak tidak membatalkan penjualan.
- MVP tidak menyediakan penyimpanan keranjang tertunda; konfirmasi sebelum membuang keranjang berisi item.

### 4.3. Riwayat transaksi

- Daftar, pencarian nomor transaksi, filter tanggal/status, dan detail transaksi.
- Cetak ulang tanpa membuat transaksi baru atau mengurangi stok lagi.
- Transaksi selesai tidak diedit langsung; koreksi melalui pembatalan lalu transaksi baru.
- Pembatalan memerlukan konfirmasi dan alasan, menyimpan waktu pembatalan, serta mengembalikan stok tepat satu kali.
- Riwayat pembatalan tetap tersimpan; transaksi tidak dihapus permanen melalui aplikasi.
- Pembatalan hanya mengoreksi catatan penjualan; pengembalian pembayaran dilakukan di luar aplikasi.

### 4.4. Produk

- Data umum: nama, kategori, harga jual, harga modal opsional, dan status aktif.
- Kelompok produk: fisik atau digital.
- Produk fisik: satuan, stok, dan pilihan pelacakan stok.
- Produk digital: jenis layanan dan kolom tambahan sesuai jenisnya.
- Stok dilacak dengan jumlah bulat pada MVP; penjualan berdasarkan berat atau pecahan belum didukung.
- Tolak penyelesaian transaksi jika stok produk yang dilacak tidak cukup; tampilkan alasan jelas.
- Produk yang telah digunakan dalam transaksi dinonaktifkan, bukan dihapus bersama riwayatnya.

### 4.5. Produk digital

| Jenis | Data transaksi tambahan |
| --- | --- |
| Pulsa | Nomor tujuan, operator, nominal |
| Token listrik | Nomor meter/ID pelanggan, nominal, token |
| Kuota internet | Nomor tujuan, operator, nama paket |
| Voucher internet | Provider, nama paket, kode voucher |
| Digital lainnya | Tujuan, keterangan, kode opsional |

- Nomor tujuan wajib untuk pulsa dan kuota; nomor meter/ID pelanggan wajib untuk token listrik.
- Token/kode dapat dimasukkan manual; jika produk memerlukannya, kode harus tersedia sebelum transaksi selesai.
- Data operator, provider, paket, atau nominal dapat berasal dari produk agar tidak diisi ulang setiap transaksi.
- Validasi format dan panjang input sesuai layanan; jangan mengasumsikan semua provider identik.
- Simpan nomor tujuan dan kode sebagai teks agar angka nol di depan tidak hilang.
- Nominal layanan dibedakan dari harga jual; token Rp20.000 dapat memiliki harga jual berbeda.
- Baris digital dengan tujuan atau kode berbeda tetap terpisah, meskipun produknya sama.
- Tidak ada pembelian token, pengiriman pulsa, atau pengecekan keberhasilan layanan otomatis pada MVP.

### 4.6. Pelanggan

- Tambah, ubah, cari, dan hapus pelanggan.
- Nama wajib; nomor telepon dan catatan opsional.
- Tampilkan transaksi terkait pelanggan.
- Penghapusan pelanggan melepaskan hubungan profil tanpa menghapus transaksi.
- Snapshot pelanggan pada transaksi lama tetap tersimpan; penghapusan profil bukan penghapusan seluruh data historis.

## 5. Nota

### 5.1. Pencetakan

- Dukungan awal: printer thermal Bluetooth Classic kompatibel ESC/POS, lebar kertas 58 mm dan 80 mm.
- Pilih printer, simpan pilihan, dan cetak percobaan.
- Sediakan pratinjau nota sebelum cetak.
- Cetak ulang menggunakan data transaksi yang sudah tersimpan.
- Tampilkan status dan kesalahan yang dapat ditindaklanjuti: izin ditolak, Bluetooth mati, printer tidak tersedia, atau koneksi terputus.
- Jika hasil cetak tidak dapat dipastikan, minta pengguna memeriksa printer sebelum mengulang; jangan otomatis mencetak berulang.
- Kompatibilitas harus diuji pada printer nyata; tidak mengklaim mendukung semua printer Bluetooth.

### 5.2. Kustomisasi

- Nama toko, alamat, dan kontak.
- Teks pembuka dan penutup.
- Pilihan menampilkan informasi opsional seperti pelanggan, label kasir statis, dan rincian diskon.
- Pilihan lebar kertas 58 mm atau 80 mm.
- Nomor transaksi, waktu, rincian item, dan total selalu tersedia.
- Tidak ada editor drag-and-drop pada MVP.

### 5.3. Layout dinamis

- Produk fisik: nama, jumlah, harga satuan, dan subtotal.
- Produk digital: rincian produk ditambah tujuan, paket, token/kode sesuai jenis dan ketersediaan data.
- Transaksi campuran: setiap baris memakai format jenis produknya, bukan satu template transaksi yang kaku.
- Informasi opsional kosong tidak menghasilkan label atau baris kosong.
- Teks panjang membungkus mengikuti lebar kertas; nilai total tidak terpotong.
- Nota menggunakan bahasa aplikasi saat dicetak; nama produk dan teks kustom tidak diterjemahkan otomatis.
- Snapshot item, harga, diskon, pembayaran, pelanggan, dan identitas toko disimpan saat transaksi selesai.
- Cetak ulang menggunakan snapshot tersebut; pengaturan presentasi saat ini seperti bahasa dan lebar kertas boleh berubah.
- Nota pembatalan/cetak ulang transaksi batal menampilkan status batal dengan jelas.

## 6. Bahasa dan pengalaman pengguna

- Bahasa Inggris menjadi default instalasi baru, terlepas dari bahasa perangkat.
- Bahasa Indonesia dapat dipilih melalui pengaturan; pilihan tersimpan setelah aplikasi dimulai ulang.
- Semua teks bawaan aplikasi dilokalkan tanpa campuran bahasa yang tidak disengaja.
- Bahasa pilihan mengatur format angka dan tanggal, bukan mata uang.
- Navigasi utama: Dashboard, Kasir, Riwayat. Produk, Pelanggan, dan Pengaturan tetap mudah dijangkau.
- Label singkat, hierarki visual jelas, teks bantuan hanya saat dibutuhkan.
- Hindari istilah formal berlebihan, dialog berulang, dan animasi dekoratif.
- Target sentuh minimal 48 dp, kontras memadai, dukungan pembesaran teks dan pembaca layar.
- Status penting tidak dibedakan hanya melalui warna.
- Validasi menjelaskan masalah dekat kolom terkait tanpa menghilangkan input pengguna.

## 7. Data, validasi, dan keamanan

- Database lokal adalah sumber data utama; internet tidak diperlukan untuk penjualan, dashboard, pelanggan, atau pencetakan Bluetooth.
- Nilai uang disimpan sebagai bilangan bulat rupiah, bukan floating point.
- Jumlah item harus positif; harga dan stok tidak negatif; diskon tidak melebihi subtotal.
- Tolak nilai di luar rentang yang didukung dan cegah overflow perhitungan total.
- Nomor transaksi unik; waktu transaksi menyimpan titik waktu dan konteks zona waktu untuk tampilan riwayat yang konsisten.
- Perubahan harga atau nama produk tidak mengubah transaksi lama.
- Nomor telepon, ID pelanggan, token, dan kode voucher tidak ditulis ke log.
- Data aplikasi disimpan di penyimpanan privat Android; tidak meminta akses penyimpanan luas.
- Izin Bluetooth mengikuti versi Android dan diminta ketika diperlukan, bukan saat pertama membuka aplikasi.
- Tidak ada kredensial atau signing key di source code maupun repository.

### 7.1. Cadangan dan pemulihan

- Ekspor dan impor cadangan melalui pemilih file sistem Android.
- Cadangan mencakup data toko, produk, pelanggan, transaksi, serta pengaturan yang portabel; koneksi printer dikonfigurasi kembali bila perlu.
- Format cadangan memiliki versi dan validasi integritas serta relasi data.
- Pemulihan MVP mengganti dataset, bukan menggabungkan dua database.
- Implementasi MVP membatasi cadangan 64 MiB UTF-8. Anggaran data tersimpan 28 MiB ditegakkan dalam transaksi database agar data tetap dapat diekspor; penulisan yang melampaui batas ditolak tanpa perubahan parsial.
- Jelaskan konsekuensi, minta konfirmasi, dan sediakan cadangan data saat ini sebelum penggantian.
- Validasi file sepenuhnya sebelum mengganti data; pemulihan atomik dan dapat dibatalkan jika gagal.
- Cadangan berisi data sensitif; pengguna diberi peringatan sebelum ekspor. Jangan menyebut file terenkripsi jika enkripsi belum diterapkan.

## 8. Arsitektur dan target performa

Stack implementasi: Kotlin, Jetpack Compose, SQLiteOpenHelper bawaan Android; aplikasi Android native tanpa WebView atau backend. SQLite platform dipilih untuk mengurangi dependensi; transaksi database dan snapshot tetap wajib.

- Dependensi dibatasi pada kebutuhan nyata; tidak memakai framework lintas platform untuk MVP ini.
- Operasi database, backup, dan printer tidak memblokir thread UI.
- Ringkasan dashboard memakai query agregasi, bukan memuat seluruh transaksi.
- Pencarian dan riwayat memakai indeks serta pemuatan bertahap sesuai kebutuhan.
- Tidak ada polling atau layanan latar belakang terus-menerus.
- Optimasi release menggunakan fasilitas bawaan Android seperti penyusutan kode dan resource.

Target awal pada build release, perangkat Android RAM 3 GB, dan dataset 10.000 transaksi:

| Metrik | Target |
| --- | --- |
| Cold start hingga layar dapat dipakai | ≤ 2 detik |
| Pencarian lokal hingga hasil tampil | ≤ 300 ms |
| Penyimpanan transaksi biasa | ≤ 500 ms, tidak termasuk cetak |
| Ukuran APK release | ≤ 25 MB |

Catat model perangkat, versi Android, dataset, dan hasil pengukuran. Target tidak dianggap tercapai sebelum diukur; penggunaan memori dan kelancaran scrolling ikut diperiksa untuk mendeteksi regresi.

## 9. Repository dan CI/CD

- Remote GitHub: https://github.com/furansugg/tokopad.git.
- Periksa isi dan riwayat remote sebelum setup; jangan menimpa riwayat atau melakukan force push.
- Build Android hanya dijalankan melalui GitHub Actions.
- Jangan memasang Android Studio, Android SDK, emulator, atau toolchain Android lain di komputer pengembangan ini.
- Jangan menjalankan build Android lokal.
- Setelah source aplikasi tersedia, workflow menjalankan lint, pengujian unit, dan build pada pull request, push branch utama, serta pemicu manual.
- Pengujian meliputi total/diskon/kembalian, perubahan stok dan pembatalan, snapshot transaksi, format nota, serta validasi/pemulihan cadangan.
- APK pengujian tersedia sebagai artifact untuk build branch utama atau pemicu manual.
- Release signing menggunakan GitHub Secrets; keystore dan password tidak masuk repository atau log.
- Pisahkan APK pengujian dari APK release bertanda tangan produksi.
- Workflow menggunakan izin minimum dan versi action yang dikunci; secret release tidak diberikan kepada pull request tidak tepercaya.
- Pengujian UI, performa, dan printer nyata menjadi pemeriksaan penerimaan sebelum rilis; CI tidak menggantikan uji hardware.

## 10. Di luar MVP

- Integrasi distributor pulsa/token atau verifikasi pembayaran otomatis.
- Sinkronisasi cloud, banyak perangkat, banyak cabang.
- Akun staf, autentikasi, serta hak akses berbasis peran.
- Akuntansi lengkap, utang/piutang, retur sebagian, dan pembayaran terpisah.
- Produk berbobot atau jumlah pecahan, barcode scanner khusus, dan impor katalog massal.
- Printer USB, jaringan, dan Bluetooth BLE.
- Editor nota drag-and-drop, dukungan logo/gambar, dan analitik lanjutan.

## 11. Kriteria penerimaan

1. Instalasi baru menggunakan bahasa Inggris; pilihan bahasa Indonesia tetap tersimpan setelah restart.
2. Produk fisik, digital, dan campuran dapat dijual tanpa internet.
3. Kolom digital mengikuti jenis produk; tujuan/kode berbeda tidak tergabung keliru.
4. Total, diskon, pembayaran, kembalian, serta stok akurat; input tidak valid ditolak dengan pesan jelas.
5. Penekanan tombol berulang tidak menggandakan transaksi atau perubahan stok.
6. Transaksi selesai tetap tersedia setelah aplikasi ditutup atau perangkat dimulai ulang.
7. Pembatalan mengecualikan transaksi dari omzet dan mengembalikan stok tepat sekali.
8. Nota menampilkan rincian relevan tanpa label kosong; transaksi campuran terbaca dengan benar.
9. Nota 58 mm dan 80 mm terbaca pada printer uji nyata, termasuk nama panjang dan token listrik.
10. Kegagalan printer tidak menghilangkan transaksi; cetak ulang tidak mengubah stok atau penjualan.
11. Riwayat dan isi transaksi lama tetap konsisten setelah produk, pelanggan, atau identitas toko diubah.
12. Dashboard sesuai transaksi selesai pada rentang tanggal pilihan dan menangani hari tanpa penjualan.
13. Cadangan valid dapat dipulihkan; file rusak atau versi tidak didukung ditolak tanpa kehilangan data.
14. Target performa diukur dan hasilnya dicatat pada perangkat referensi.
15. GitHub Actions menghasilkan APK tanpa build Android atau pemasangan toolchain Android di komputer ini.

## 12. Urutan implementasi

1. Fondasi aplikasi, lokalisasi, database, dan workflow GitHub Actions minimal.
2. Produk, pelanggan, kasir, riwayat, dan dashboard offline.
3. Nota dinamis, kustomisasi, dan printer Bluetooth.
4. Cadangan/pemulihan, pengujian kegagalan, aksesibilitas, dan pengukuran performa.
5. Uji penerimaan pada perangkat serta printer toko, lalu release melalui GitHub Actions.
