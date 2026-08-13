import { useMemo, useState } from "react";
import { Timestamp, deleteDoc, doc, updateDoc } from "firebase/firestore";
import { db } from "../firebase";
import { useAuth } from "../hooks/useAuth";
import { useEmployees, useRecords, useSettings, useShifts } from "../hooks/useData";
import { formatDate, recompute } from "../lib/rules";
import { durasi, jam, meter, rupiah, tanggalPendek } from "../lib/format";
import { fineFor } from "../lib/rules";
import Dialog from "../components/Dialog";
import PunchPhoto from "../components/PunchPhoto";
import type { AttendanceRecord } from "../lib/types";

export default function RecordsPage() {
  const { user } = useAuth();
  const settings = useSettings();
  const employees = useEmployees(true);
  const shifts = useShifts();

  const hariIni = formatDate(new Date());
  const [from, setFrom] = useState(hariIni);
  const [to, setTo] = useState(hariIni);
  const [filterKaryawan, setFilterKaryawan] = useState("");

  const records = useRecords(from, to);
  const [dibuka, setDibuka] = useState<AttendanceRecord | null>(null);

  const tampil = useMemo(() => {
    const list = filterKaryawan
      ? records.filter((r) => r.employeeId === filterKaryawan)
      : records;
    return [...list].sort(
      (a, b) =>
        b.date.localeCompare(a.date) ||
        (b.checkIn?.at.toMillis() ?? 0) - (a.checkIn?.at.toMillis() ?? 0),
    );
  }, [records, filterKaryawan]);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Absensi</h1>
          <p>
            Ketuk satu baris untuk melihat foto, lokasi, dan mengoreksi jamnya.
          </p>
        </div>
      </div>

      <div className="card">
        <div className="grid cols-3">
          <label className="field">
            <span>Dari tanggal</span>
            <input type="date" value={from} onChange={(e) => setFrom(e.target.value)} />
          </label>
          <label className="field">
            <span>Sampai tanggal</span>
            <input type="date" value={to} onChange={(e) => setTo(e.target.value)} />
          </label>
          <label className="field">
            <span>Karyawan</span>
            <select
              value={filterKaryawan}
              onChange={(e) => setFilterKaryawan(e.target.value)}
            >
              <option value="">Semua</option>
              {employees.map((e) => (
                <option key={e.id} value={e.id}>
                  {e.name}
                </option>
              ))}
            </select>
          </label>
        </div>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Tanggal</th>
              <th>Karyawan</th>
              <th>Shift</th>
              <th>Masuk</th>
              <th>Pulang</th>
              <th className="num">Telat</th>
              {settings.fineEnabled && <th className="num">Denda</th>}
              <th className="num">Kerja</th>
              <th className="num">Lembur</th>
              <th>Tanda</th>
            </tr>
          </thead>
          <tbody>
            {tampil.length === 0 && (
              <tr>
                <td colSpan={10} className="center muted">
                  Tidak ada absen pada rentang ini.
                </td>
              </tr>
            )}
            {tampil.map((r) => (
              <tr key={r.id} onClick={() => setDibuka(r)} style={{ cursor: "pointer" }}>
                <td>{tanggalPendek(r.date)}</td>
                <td>{r.employeeName}</td>
                <td>{r.shiftName || <span className="muted">—</span>}</td>
                <td>{jam(r.checkIn?.at.toDate())}</td>
                <td>
                  {r.checkOut ? (
                    jam(r.checkOut.at.toDate())
                  ) : (
                    <span className="tag warn">belum pulang</span>
                  )}
                </td>
                <td className="num">
                  {r.lateMinutes > 0 ? (
                    <span className="tag bad">{durasi(r.lateMinutes)}</span>
                  ) : (
                    "—"
                  )}
                </td>
                {settings.fineEnabled && (
                  <td className="num">
                    {r.lateMinutes > 0 ? rupiah(fineFor(r.lateMinutes, settings)) : "—"}
                  </td>
                )}
                <td className="num">{r.workMinutes > 0 ? durasi(r.workMinutes) : "—"}</td>
                <td className="num">
                  {r.overtimeMinutes > 0 ? durasi(r.overtimeMinutes) : "—"}
                </td>
                <td>
                  <Tanda record={r} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {dibuka && (
        <DetailDialog
          record={dibuka}
          adminEmail={user?.email ?? ""}
          onClose={() => setDibuka(null)}
          shifts={shifts}
          employees={employees}
          settings={settings}
        />
      )}
    </>
  );
}

function Tanda({ record }: { record: AttendanceRecord }) {
  const tanda: { teks: string; kelas: string }[] = [];
  if (record.offSchedule) tanda.push({ teks: "di luar jadwal", kelas: "warn" });
  if (record.checkIn?.noPin || record.checkOut?.noPin) {
    tanda.push({ teks: "tanpa PIN", kelas: "bad" });
  }
  if (record.checkIn?.adminOverride || record.checkOut?.adminOverride) {
    tanda.push({ teks: "izin penyelia", kelas: "warn" });
  }
  if (record.checkIn?.outsideGeofence || record.checkOut?.outsideGeofence) {
    tanda.push({ teks: "di luar area", kelas: "bad" });
  }
  if (record.earlyLeaveMinutes > 0) {
    tanda.push({ teks: `pulang cepat ${durasi(record.earlyLeaveMinutes)}`, kelas: "warn" });
  }
  if (record.correctedBy) tanda.push({ teks: "dikoreksi", kelas: "" });

  if (tanda.length === 0) return <span className="muted">—</span>;
  return (
    <>
      {tanda.map((t) => (
        <span key={t.teks} className={`tag ${t.kelas}`}>
          {t.teks}
        </span>
      ))}
    </>
  );
}

interface DetailProps {
  record: AttendanceRecord;
  adminEmail: string;
  shifts: ReturnType<typeof useShifts>;
  employees: ReturnType<typeof useEmployees>;
  settings: ReturnType<typeof useSettings>;
  onClose: () => void;
}

/**
 * Rincian dan koreksi satu catatan.
 *
 * Yang bisa diubah hanya jam, shift, dan catatan. Telat, lembur, dan jam kerja
 * selalu dihitung ulang dari jam yang tersimpan — tidak pernah diketik tangan —
 * supaya rekap tidak bisa berbeda dari bukti yang ada.
 */
function DetailDialog({
  record,
  adminEmail,
  shifts,
  employees,
  settings,
  onClose,
}: DetailProps) {
  const [masuk, setMasuk] = useState(toLocalInput(record.checkIn?.at.toDate()));
  const [pulang, setPulang] = useState(toLocalInput(record.checkOut?.at.toDate()));
  const [shiftId, setShiftId] = useState(record.shiftId);
  const [note, setNote] = useState(record.note);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function simpan() {
    setBusy(true);
    setError(null);
    try {
      if (!masuk) {
        setError("Jam masuk tidak boleh kosong.");
        setBusy(false);
        return;
      }
      const waktuMasuk = new Date(masuk);
      const waktuPulang = pulang ? new Date(pulang) : null;
      if (waktuPulang && waktuPulang < waktuMasuk) {
        setError("Jam pulang lebih awal dari jam masuk.");
        setBusy(false);
        return;
      }

      const shift = shifts.find((s) => s.id === shiftId) ?? null;
      const employee = employees.find((e) => e.id === record.employeeId);

      const diperbarui = recompute(
        {
          ...record,
          shiftId: shift?.id ?? "",
          shiftName: shift?.name ?? "",
          shiftStart: shift?.start ?? "",
          shiftEnd: shift?.end ?? "",
          offSchedule: shift === null,
          checkIn: record.checkIn
            ? { ...record.checkIn, at: Timestamp.fromDate(waktuMasuk) }
            : null,
          checkOut:
            waktuPulang && record.checkOut
              ? { ...record.checkOut, at: Timestamp.fromDate(waktuPulang) }
              : waktuPulang
                ? {
                    at: Timestamp.fromDate(waktuPulang),
                    lat: null,
                    lon: null,
                    accuracyMeters: null,
                    distanceMeters: null,
                    outsideGeofence: false,
                    photoPath: "",
                    pinOk: false,
                    adminOverride: true,
                    noPin: false,
                  }
                : null,
          note,
        },
        shift,
        employee,
        settings,
      );

      await updateDoc(doc(db, "records", record.id), {
        shiftId: diperbarui.shiftId,
        shiftName: diperbarui.shiftName,
        shiftStart: diperbarui.shiftStart,
        shiftEnd: diperbarui.shiftEnd,
        offSchedule: diperbarui.offSchedule,
        checkIn: diperbarui.checkIn,
        checkOut: diperbarui.checkOut,
        lateMinutes: diperbarui.lateMinutes,
        earlyLeaveMinutes: diperbarui.earlyLeaveMinutes,
        workMinutes: diperbarui.workMinutes,
        overtimeMinutes: diperbarui.overtimeMinutes,
        note: diperbarui.note,
        correctedBy: adminEmail,
      });
      onClose();
    } catch (e) {
      setError(`Gagal menyimpan: ${(e as Error).message}`);
    } finally {
      setBusy(false);
    }
  }

  async function hapus() {
    if (
      !confirm(
        `Hapus catatan absen ${record.employeeName} tanggal ${record.date}?\n\n` +
          "Foto buktinya tetap tersimpan di penyimpanan, tapi catatannya hilang " +
          "dari rekap dan ekspor.",
      )
    ) {
      return;
    }
    await deleteDoc(doc(db, "records", record.id));
    onClose();
  }

  return (
    <Dialog
      title={`${record.employeeName} — ${record.date}`}
      onClose={onClose}
      footer={
        <>
          <button className="danger" onClick={() => void hapus()}>
            Hapus catatan
          </button>
          <button onClick={onClose}>Tutup</button>
          <button className="primary" disabled={busy} onClick={() => void simpan()}>
            {busy ? "Menyimpan…" : "Simpan koreksi"}
          </button>
        </>
      }
    >
      <div className="grid cols-2">
        <label className="field">
          <span>Jam masuk</span>
          <input
            type="datetime-local"
            value={masuk}
            onChange={(e) => setMasuk(e.target.value)}
          />
        </label>
        <label className="field">
          <span>Jam pulang — kosongkan bila memang belum pulang</span>
          <input
            type="datetime-local"
            value={pulang}
            onChange={(e) => setPulang(e.target.value)}
          />
        </label>
      </div>

      <label className="field">
        <span>Shift</span>
        <select value={shiftId} onChange={(e) => setShiftId(e.target.value)}>
          <option value="">Tanpa shift (di luar jadwal)</option>
          {shifts.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name} ({s.start}–{s.end})
            </option>
          ))}
        </select>
      </label>

      <label className="field">
        <span>Catatan</span>
        <textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} />
      </label>

      {error && <div className="notice bad">{error}</div>}

      <div className="grid cols-2">
        <div>
          <h3>Masuk</h3>
          {record.checkIn ? (
            <>
              <PunchPhoto path={record.checkIn.photoPath} />
              <Lokasi
                lat={record.checkIn.lat}
                lon={record.checkIn.lon}
                akurasi={record.checkIn.accuracyMeters}
                jarak={record.checkIn.distanceMeters}
              />
            </>
          ) : (
            <p className="muted small">tidak ada</p>
          )}
        </div>
        <div>
          <h3>Pulang</h3>
          {record.checkOut ? (
            <>
              <PunchPhoto path={record.checkOut.photoPath} />
              <Lokasi
                lat={record.checkOut.lat}
                lon={record.checkOut.lon}
                akurasi={record.checkOut.accuracyMeters}
                jarak={record.checkOut.distanceMeters}
              />
            </>
          ) : (
            <p className="muted small">belum absen pulang</p>
          )}
        </div>
      </div>

      <p className="small muted">
        Dicatat perangkat {record.deviceId || "—"}
        {record.correctedBy && ` · terakhir dikoreksi ${record.correctedBy}`}
      </p>
    </Dialog>
  );
}

function Lokasi({
  lat,
  lon,
  akurasi,
  jarak,
}: {
  lat: number | null;
  lon: number | null;
  akurasi: number | null;
  jarak: number | null;
}) {
  if (lat === null || lon === null) {
    return <p className="small muted">Lokasi tidak tercatat.</p>;
  }
  return (
    <p className="small muted">
      <a
        href={`https://www.google.com/maps?q=${lat},${lon}`}
        target="_blank"
        rel="noreferrer"
      >
        {lat.toFixed(6)}, {lon.toFixed(6)}
      </a>
      {akurasi !== null && ` · ±${Math.round(akurasi)} m`}
      {jarak !== null && ` · ${meter(jarak)} dari cafe`}
    </p>
  );
}

/** Date -> nilai untuk <input type="datetime-local"> dalam zona lokal. */
function toLocalInput(date: Date | undefined | null): string {
  if (!date) return "";
  const p = (n: number) => String(n).padStart(2, "0");
  return (
    `${date.getFullYear()}-${p(date.getMonth() + 1)}-${p(date.getDate())}` +
    `T${p(date.getHours())}:${p(date.getMinutes())}`
  );
}
