import { useMemo } from "react";
import { Link } from "react-router-dom";
import {
  useDevices,
  useEmployeeNames,
  useEmployees,
  useRecords,
  useRoster,
  useSettings,
  useShifts,
} from "../hooks/useData";
import { hasPin } from "../lib/types";
import { addDays, formatDate } from "../lib/rules";
import { durasi, jam, tanggalPanjang } from "../lib/format";
import { ROSTER_OFF } from "../lib/types";

export default function DashboardPage() {
  const hariIni = formatDate(new Date());
  const kemarin = addDays(hariIni, -1);

  const settings = useSettings();
  const employees = useEmployees();
  const names = useEmployeeNames(employees);
  const shifts = useShifts();
  const devices = useDevices();
  const roster = useRoster(useMemo(() => [hariIni, kemarin], [hariIni, kemarin]));
  const records = useRecords(kemarin, hariIni);

  const hariIniRecords = records.filter((r) => r.date === hariIni);
  const dijadwalkan = employees.filter((e) => {
    const tugas = roster[hariIni]?.assign?.[e.id];
    return tugas && tugas !== ROSTER_OFF;
  });
  const sudahAbsen = new Set(hariIniRecords.map((r) => r.employeeId));
  const belumDatang = dijadwalkan.filter((e) => !sudahAbsen.has(e.id));
  const masihBekerja = records.filter((r) => r.checkIn && !r.checkOut);
  const telatHariIni = hariIniRecords.filter((r) => r.lateMinutes > 0);

  const perluPerhatian: string[] = [];
  if (shifts.length === 0) perluPerhatian.push("Belum ada pola shift.");
  if (employees.length === 0) perluPerhatian.push("Belum ada karyawan aktif.");
  if (settings.pinRequired && employees.some((e) => !hasPin(e))) {
    perluPerhatian.push(
      `PIN belum diatur untuk ${employees
        .filter((e) => !hasPin(e))
        .map((e) => e.name)
        .join(", ")}.`,
    );
  }
  if (devices.length === 0) {
    perluPerhatian.push("Belum ada tablet kios yang pernah tersambung.");
  }
  if (settings.geoMode !== "off" && settings.geoLat === null) {
    perluPerhatian.push(
      "Pembatasan lokasi menyala tapi titik cafe belum diisi — semua absen akan dianggap di luar area.",
    );
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{settings.cafeName}</h1>
          <p>{tanggalPanjang(hariIni)}</p>
        </div>
      </div>

      {perluPerhatian.length > 0 && (
        <div className="notice warn">
          <strong>Perlu diatur</strong>
          <ul style={{ margin: "6px 0 0", paddingLeft: 20 }}>
            {perluPerhatian.map((p) => (
              <li key={p}>{p}</li>
            ))}
          </ul>
        </div>
      )}

      <div className="grid cols-4" style={{ marginBottom: 18 }}>
        <Kartu label="Dijadwalkan hari ini" nilai={String(dijadwalkan.length)} />
        <Kartu label="Sudah absen" nilai={String(sudahAbsen.size)} />
        <Kartu label="Belum datang" nilai={String(belumDatang.length)} />
        <Kartu label="Telat hari ini" nilai={String(telatHariIni.length)} />
      </div>

      <div className="grid cols-2">
        <div className="card">
          <h2>Sedang bekerja</h2>
          {masihBekerja.length === 0 ? (
            <p className="muted">Tidak ada yang sedang bekerja.</p>
          ) : (
            <table>
              <tbody>
                {masihBekerja.map((r) => (
                  <tr key={r.id}>
                    <td>{names.get(r.employeeId) ?? "(karyawan dihapus)"}</td>
                    <td className="muted">
                      {shifts.find((s) => s.id === r.shiftId)?.name ?? "di luar jadwal"}
                    </td>
                    <td className="num">masuk {jam(r.checkIn?.at)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <div className="card">
          <h2>Belum datang</h2>
          {dijadwalkan.length === 0 ? (
            <p className="muted">
              Belum ada yang dijadwalkan hari ini.{" "}
              <Link to="/jadwal">Atur jadwal</Link>.
            </p>
          ) : belumDatang.length === 0 ? (
            <p className="muted">Semua yang dijadwalkan sudah absen.</p>
          ) : (
            <ul style={{ margin: 0, paddingLeft: 20 }}>
              {belumDatang.map((e) => {
                const shift = shifts.find((s) => s.id === roster[hariIni]?.assign?.[e.id]);
                return (
                  <li key={e.id}>
                    {e.name}
                    {shift && <span className="muted"> — {shift.name} {shift.start}</span>}
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      </div>

      <div className="card">
        <h2>Absen hari ini</h2>
        {hariIniRecords.length === 0 ? (
          <p className="muted">
            Belum ada absen hari ini. <Link to="/absensi">Lihat hari lain</Link>.
          </p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Karyawan</th>
                  <th>Shift</th>
                  <th>Masuk</th>
                  <th>Pulang</th>
                  <th className="num">Telat</th>
                </tr>
              </thead>
              <tbody>
                {hariIniRecords.map((r) => (
                  <tr key={r.id}>
                    <td>{names.get(r.employeeId) ?? "(karyawan dihapus)"}</td>
                    <td>
                      {shifts.find((s) => s.id === r.shiftId)?.name ?? (
                        <span className="muted">di luar jadwal</span>
                      )}
                    </td>
                    <td>{jam(r.checkIn?.at)}</td>
                    <td>{r.checkOut ? jam(r.checkOut.at) : "—"}</td>
                    <td className="num">
                      {r.lateMinutes > 0 ? (
                        <span className="tag bad">{durasi(r.lateMinutes)}</span>
                      ) : (
                        "—"
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}

function Kartu({ label, nilai }: { label: string; nilai: string }) {
  return (
    <div className="stat">
      <div className="label">{label}</div>
      <div className="value">{nilai}</div>
    </div>
  );
}
