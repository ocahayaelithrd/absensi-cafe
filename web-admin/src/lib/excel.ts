import type ExcelJS from "exceljs";
import { fineFor } from "./rules";
import { buildRecap, totalRow, type RecapRow } from "./recap";
import { jamDesimal, tanggalPanjang } from "./format";
import type {
  AttendanceRecord,
  Employee,
  Punch,
  RosterDay,
  Settings,
  Shift,
} from "./types";

/*
 * Ekspor Excel tiga lembar untuk perhitungan gaji.
 *
 * Angka ditulis sebagai angka sungguhan dengan format sel — rupiah, bilangan
 * bulat, jam desimal, dan koordinat enam angka di belakang koma — sehingga
 * langsung bisa dijumlahkan atau dijadikan pivot tanpa dibersihkan dulu.
 */

const RUPIAH = '"Rp"#,##0';
const BULAT = "0";
const DESIMAL = "0.00";
const KOORDINAT = "0.000000";
const JAM = "hh:mm";

interface Kolom {
  header: string;
  key: string;
  width: number;
  format?: string;
}

export interface ExportInput {
  dates: string[];
  from: string;
  to: string;
  employees: Employee[];
  shifts: Shift[];
  records: AttendanceRecord[];
  roster: Record<string, RosterDay>;
  settings: Settings;
  periodLabel: string;
}

export async function exportWorkbook(input: ExportInput): Promise<void> {
  const { settings } = input;
  const denda = settings.fineEnabled;

  // ExcelJS besar dan hanya dipakai saat tombol ekspor ditekan, jadi dimuat
  // pada saat itu juga supaya halaman admin tetap ringan dibuka tiap hari.
  const { Workbook } = await import("exceljs");

  const wb = new Workbook();
  wb.creator = "Absensi Cafe";
  wb.created = new Date();

  tulisRekap(wb, input, denda);
  tulisDetail(wb, input, denda);
  tulisInfo(wb, input);

  const buffer = await wb.xlsx.writeBuffer();
  const blob = new Blob([buffer], {
    type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  });
  unduh(blob, `absensi-${input.from}-sd-${input.to}.xlsx`);
}

function tulisRekap(wb: ExcelJS.Workbook, input: ExportInput, denda: boolean) {
  const sheet = wb.addWorksheet("Rekap");
  const kolom: Kolom[] = [
    { header: "Karyawan", key: "name", width: 24 },
    { header: "Dijadwalkan", key: "scheduled", width: 12, format: BULAT },
    { header: "Hadir", key: "present", width: 9, format: BULAT },
    { header: "Alpa", key: "absent", width: 9, format: BULAT },
    { header: "Belum pulang", key: "incomplete", width: 13, format: BULAT },
    { header: "Di luar jadwal", key: "offSchedule", width: 14, format: BULAT },
    { header: "Kali telat", key: "lateCount", width: 11, format: BULAT },
    { header: "Total telat (menit)", key: "lateMinutes", width: 18, format: BULAT },
    ...(denda ? [{ header: "Denda", key: "fine", width: 14, format: RUPIAH }] : []),
    { header: "Pulang cepat (menit)", key: "earlyLeaveMinutes", width: 20, format: BULAT },
    { header: "Lembur (menit)", key: "overtimeMinutes", width: 15, format: BULAT },
    { header: "Jam kerja", key: "workHours", width: 12, format: DESIMAL },
  ];
  siapkan(sheet, kolom);

  const rows = buildRecap(
    input.dates,
    input.employees,
    input.records,
    input.roster,
    input.settings,
  );
  for (const r of rows) sheet.addRow(barisRekap(r));

  const total = sheet.addRow(barisRekap(totalRow(rows)));
  total.font = { bold: true };
  total.eachCell((cell) => {
    cell.border = { top: { style: "thin" } };
  });
}

function barisRekap(r: RecapRow) {
  return { ...r, workHours: jamDesimal(r.workMinutes) };
}

function tulisDetail(wb: ExcelJS.Workbook, input: ExportInput, denda: boolean) {
  const sheet = wb.addWorksheet("Detail");
  const kolom: Kolom[] = [
    { header: "Tanggal", key: "date", width: 12 },
    { header: "Karyawan", key: "name", width: 24 },
    { header: "Shift", key: "shift", width: 12 },
    { header: "Jadwal", key: "jadwal", width: 14 },
    { header: "Masuk", key: "masuk", width: 9, format: JAM },
    { header: "Pulang", key: "pulang", width: 9, format: JAM },
    { header: "Telat (menit)", key: "late", width: 13, format: BULAT },
    ...(denda ? [{ header: "Denda", key: "fine", width: 14, format: RUPIAH }] : []),
    { header: "Pulang cepat (menit)", key: "early", width: 20, format: BULAT },
    { header: "Lembur (menit)", key: "overtime", width: 15, format: BULAT },
    { header: "Jam kerja", key: "workHours", width: 12, format: DESIMAL },
    { header: "Di luar jadwal", key: "offSchedule", width: 14 },
    { header: "PIN masuk", key: "pinIn", width: 14 },
    { header: "PIN pulang", key: "pinOut", width: 14 },
    { header: "Lintang masuk", key: "latIn", width: 15, format: KOORDINAT },
    { header: "Bujur masuk", key: "lonIn", width: 15, format: KOORDINAT },
    { header: "Akurasi masuk (m)", key: "accIn", width: 17, format: BULAT },
    { header: "Jarak masuk (m)", key: "distIn", width: 16, format: BULAT },
    { header: "Lintang pulang", key: "latOut", width: 15, format: KOORDINAT },
    { header: "Bujur pulang", key: "lonOut", width: 15, format: KOORDINAT },
    { header: "Akurasi pulang (m)", key: "accOut", width: 18, format: BULAT },
    { header: "Jarak pulang (m)", key: "distOut", width: 17, format: BULAT },
    { header: "Foto masuk", key: "photoIn", width: 12 },
    { header: "Foto pulang", key: "photoOut", width: 12 },
    { header: "Dikoreksi", key: "edited", width: 11 },
    { header: "Catatan", key: "note", width: 30 },
  ];
  siapkan(sheet, kolom);

  /* Catatan absen hanya menyimpan pengenal karyawan dan shift, bukan namanya,
     sehingga rekap lama ikut berubah kalau nama diperbaiki. Namanya dicari di
     sini saat ekspor disusun. */
  const namaKaryawan = new Map(input.employees.map((e) => [e.id, e.name]));
  const shiftById = new Map(input.shifts.map((s) => [s.id, s]));
  const nama = (id: string) => namaKaryawan.get(id) ?? "(karyawan dihapus)";

  const urut = [...input.records].sort(
    (a, b) =>
      a.date.localeCompare(b.date) || nama(a.employeeId).localeCompare(nama(b.employeeId)),
  );

  for (const r of urut) {
    const shift = shiftById.get(r.shiftId);
    sheet.addRow({
      date: r.date,
      name: nama(r.employeeId),
      shift: shift?.name ?? "—",
      jadwal: shift ? `${shift.start}–${shift.end}` : "—",
      masuk: r.checkIn ? r.checkIn.at : null,
      pulang: r.checkOut ? r.checkOut.at : null,
      late: r.lateMinutes,
      fine: fineFor(r.lateMinutes, input.settings),
      early: r.earlyLeaveMinutes,
      overtime: r.overtimeMinutes,
      workHours: jamDesimal(r.workMinutes),
      offSchedule: r.offSchedule ? "ya" : "",
      pinIn: statusPin(r.checkIn),
      pinOut: statusPin(r.checkOut),
      latIn: r.checkIn?.lat ?? null,
      lonIn: r.checkIn?.lon ?? null,
      accIn: r.checkIn?.accuracyMeters ?? null,
      distIn: r.checkIn?.distanceMeters ?? null,
      latOut: r.checkOut?.lat ?? null,
      lonOut: r.checkOut?.lon ?? null,
      accOut: r.checkOut?.accuracyMeters ?? null,
      distOut: r.checkOut?.distanceMeters ?? null,
      photoIn: r.checkIn?.photo ? "ada" : "",
      photoOut: r.checkOut?.photo ? "ada" : "",
      edited: r.edited ? "ya" : "",
      note: r.note,
    });
  }
}

function statusPin(punch: Punch | null): string {
  if (!punch) return "";
  switch (punch.pinBy) {
    case "kosong":
      return "tanpa PIN";
    case "admin":
      return "izin penyelia";
    case "pin":
      return "ok";
    default:
      return "PIN dimatikan";
  }
}

function tulisInfo(wb: ExcelJS.Workbook, input: ExportInput) {
  const sheet = wb.addWorksheet("Info");
  sheet.columns = [
    { header: "Keterangan", key: "k", width: 34 },
    { header: "Nilai", key: "v", width: 46 },
  ];
  sheet.getRow(1).font = { bold: true };

  const s = input.settings;
  const isi: [string, string | number][] = [
    ["Cafe", s.cafeName],
    ["Periode", input.periodLabel],
    ["Dari tanggal", tanggalPanjang(input.from)],
    ["Sampai tanggal", tanggalPanjang(input.to)],
    ["Waktu ekspor", new Date().toLocaleString("id-ID")],
    ["Jumlah karyawan", input.employees.length],
    ["Jumlah catatan absen", input.records.length],
    ["Toleransi telat umum (menit)", s.toleranceMinutes],
    ["Lembur dihitung mulai (menit)", s.minOvertimeMinutes],
    ["Denda keterlambatan", s.fineEnabled ? "aktif" : "nonaktif"],
    ...(s.fineEnabled
      ? s.fineTiers.map(
          (t, i) =>
            [
              `  Tingkat ${i + 1}`,
              t.upToMinutes === null
                ? `lebih dari tingkat sebelumnya → Rp ${t.amount.toLocaleString("id-ID")}`
                : `sampai ${t.upToMinutes} menit → Rp ${t.amount.toLocaleString("id-ID")}`,
            ] as [string, string],
        )
      : []),
    ["Mode lokasi", modeLokasi(s.geoMode)],
    ["Titik cafe", s.geoLat !== null && s.geoLon !== null ? `${s.geoLat}, ${s.geoLon}` : "belum diatur"],
    ["Radius (meter)", s.geoRadiusMeters],
    ["PIN pribadi karyawan", s.pinRequired ? "wajib" : "dimatikan"],
  ];

  for (const [k, v] of isi) sheet.addRow({ k, v });

  sheet.addRow({});
  sheet.addRow({
    k: "Catatan",
    v: "Denda dihitung dari tarif yang berlaku saat ekspor dibuat, bukan disimpan per catatan.",
  });
}

function modeLokasi(mode: Settings["geoMode"]): string {
  if (mode === "strict") return "wajib di area";
  if (mode === "warn") return "peringatan saja";
  return "nonaktif";
}

/** Kepala tabel tebal, dibekukan, dengan filter otomatis dan format kolom. */
function siapkan(sheet: ExcelJS.Worksheet, kolom: Kolom[]) {
  sheet.columns = kolom.map((k) => ({ header: k.header, key: k.key, width: k.width }));

  const header = sheet.getRow(1);
  header.font = { bold: true };
  header.alignment = { vertical: "middle", wrapText: true };
  sheet.views = [{ state: "frozen", ySplit: 1 }];
  sheet.autoFilter = {
    from: { row: 1, column: 1 },
    to: { row: 1, column: kolom.length },
  };

  kolom.forEach((k, i) => {
    if (!k.format) return;
    sheet.getColumn(i + 1).numFmt = k.format;
  });
}

function unduh(blob: Blob, nama: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = nama;
  a.click();
  URL.revokeObjectURL(url);
}
