const HARI = ["Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"];
const BULAN = [
  "Januari", "Februari", "Maret", "April", "Mei", "Juni",
  "Juli", "Agustus", "September", "Oktober", "November", "Desember",
];

export function rupiah(amount: number): string {
  return "Rp " + Math.round(amount).toLocaleString("id-ID");
}

export function jam(date: Date | null | undefined): string {
  if (!date) return "—";
  const p = (n: number) => String(n).padStart(2, "0");
  return `${p(date.getHours())}:${p(date.getMinutes())}`;
}

/** "Rab 13/08" untuk tabel yang sempit. */
export function tanggalPendek(iso: string): string {
  const [y, m, d] = iso.split("-").map((v) => Number.parseInt(v, 10));
  const dt = new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${HARI[dt.getDay()]!.slice(0, 3)} ${p(dt.getDate())}/${p(dt.getMonth() + 1)}`;
}

/** "13 Agustus 2026" untuk judul. */
export function tanggalPanjang(iso: string): string {
  const [y, m, d] = iso.split("-").map((v) => Number.parseInt(v, 10));
  return `${d} ${BULAN[(m ?? 1) - 1]} ${y}`;
}

export function namaBulan(bulan1sd12: number): string {
  return BULAN[bulan1sd12 - 1] ?? "";
}

/** "7j 30m"; menit negatif dianggap nol. */
export function durasi(minutes: number): string {
  const m = Math.max(0, Math.round(minutes));
  const j = Math.floor(m / 60);
  const sisa = m % 60;
  return j > 0 ? `${j}j ${sisa}m` : `${sisa}m`;
}

/** Jam desimal untuk kolom Excel yang perlu dijumlahkan. */
export function jamDesimal(minutes: number): number {
  return Math.round((Math.max(0, minutes) / 60) * 100) / 100;
}

export function meter(m: number | null | undefined): string {
  if (m === null || m === undefined) return "—";
  return m < 1000 ? `${Math.round(m)} m` : `${(m / 1000).toFixed(2)} km`;
}

export function hariDalamBulan(tahun: number, bulan1sd12: number): string[] {
  const jumlah = new Date(tahun, bulan1sd12, 0).getDate();
  const p = (n: number) => String(n).padStart(2, "0");
  return Array.from(
    { length: jumlah },
    (_, i) => `${tahun}-${p(bulan1sd12)}-${p(i + 1)}`,
  );
}

/** Senin sebagai awal minggu, sesuai roster mingguan di layar Jadwal. */
export function awalMinggu(iso: string): string {
  const [y, m, d] = iso.split("-").map((v) => Number.parseInt(v, 10));
  const dt = new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1);
  const geser = (dt.getDay() + 6) % 7;
  dt.setDate(dt.getDate() - geser);
  const p = (n: number) => String(n).padStart(2, "0");
  return `${dt.getFullYear()}-${p(dt.getMonth() + 1)}-${p(dt.getDate())}`;
}

export function labelHari(iso: string): string {
  const [y, m, d] = iso.split("-").map((v) => Number.parseInt(v, 10));
  const dt = new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1);
  return HARI[dt.getDay()] ?? "";
}
