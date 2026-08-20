import { useEffect, useState } from "react";
import { useDevices, useSettingsState } from "../hooks/useData";
import { saveSettings } from "../lib/write";
import { sortTiers } from "../lib/rules";
import { rupiah } from "../lib/format";
import type { FineTier, GeoMode, Settings } from "../lib/types";

export default function SettingsPage() {
  const { settings: tersimpan, loaded } = useSettingsState();
  const devices = useDevices();
  const [draft, setDraft] = useState<Settings | null>(null);
  const [status, setStatus] = useState<string | null>(null);

  /* Menyalin sekali saat pengaturan sungguhan tiba — bukan sebelumnya.
     Menunggu `loaded` itu yang menentukan: tanpa itu formulir terisi nilai
     bawaan pada render pertama, menahannya karena sudah "terisi", lalu
     menimpa pengaturan cafe dengan bawaan begitu Simpan ditekan.

     Sesudah tersalin, formulir menjadi milik admin: pendengar snapshot tidak
     boleh menimpa ketikan yang belum disimpan. */
  useEffect(() => {
    if (!loaded) return;
    setDraft((prev) => prev ?? tersimpan);
  }, [loaded, tersimpan]);

  if (!loaded || !draft) return <p className="muted">Memuat pengaturan…</p>;

  const ubah = (patch: Partial<Settings>) => {
    setDraft({ ...draft, ...patch });
    setStatus(null);
  };

  async function simpan() {
    if (!draft) return;
    await saveSettings({ ...draft, fineTiers: sortTiers(draft.fineTiers) });
    setStatus("Pengaturan tersimpan. Tablet menerapkannya dalam beberapa detik.");
  }

  function ubahTier(index: number, patch: Partial<FineTier>) {
    if (!draft) return;
    const tiers = draft.fineTiers.map((t, i) => (i === index ? { ...t, ...patch } : t));
    ubah({ fineTiers: tiers });
  }

  function pakaiLokasiSekarang() {
    if (!navigator.geolocation) {
      setStatus("Peramban ini tidak mendukung geolokasi.");
      return;
    }
    setStatus("Mencari lokasi…");
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        ubah({
          geoLat: Number(pos.coords.latitude.toFixed(6)),
          geoLon: Number(pos.coords.longitude.toFixed(6)),
        });
        setStatus(`Titik diambil dengan akurasi ±${Math.round(pos.coords.accuracy)} m.`);
      },
      (err) => setStatus(`Gagal membaca lokasi: ${err.message}`),
      { enableHighAccuracy: true, timeout: 15000 },
    );
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Pengaturan</h1>
          <p>Berlaku untuk semua tablet kios begitu disimpan.</p>
        </div>
        <button className="primary" onClick={() => void simpan()}>
          Simpan pengaturan
        </button>
      </div>

      {status && <div className="notice">{status}</div>}

      <div className="card">
        <h2>Umum</h2>
        <div className="grid cols-2">
          <label className="field">
            <span>Nama cafe (judul di layar kios)</span>
            <input
              value={draft.cafeName}
              onChange={(e) => ubah({ cafeName: e.target.value })}
            />
          </label>
          <label className="field">
            <span>PIN penyelia di tablet</span>
            <input
              inputMode="numeric"
              maxLength={4}
              value={draft.kioskAdminPin}
              onChange={(e) =>
                ubah({ kioskAdminPin: e.target.value.replace(/\D/g, "").slice(0, 4) })
              }
            />
          </label>
          <label className="field">
            <span>Toleransi telat umum (menit)</span>
            <input
              inputMode="numeric"
              value={draft.toleranceMinutes}
              onChange={(e) =>
                ubah({ toleranceMinutes: Number(e.target.value.replace(/\D/g, "") || 0) })
              }
            />
          </label>
          <label className="field">
            <span>Lembur dihitung mulai dari (menit)</span>
            <input
              inputMode="numeric"
              value={draft.minOvertimeMinutes}
              onChange={(e) =>
                ubah({ minOvertimeMinutes: Number(e.target.value.replace(/\D/g, "") || 0) })
              }
            />
          </label>
        </div>
        <label className="check">
          <input
            type="checkbox"
            checked={draft.pinRequired}
            onChange={(e) => ubah({ pinRequired: e.target.checked })}
          />
          <span>
            Karyawan wajib memasukkan PIN pribadi sebelum berfoto — pengaman utama
            terhadap titip absen
          </span>
        </label>
      </div>

      <div className="card">
        <h2>Denda keterlambatan</h2>
        <label className="check">
          <input
            type="checkbox"
            checked={draft.fineEnabled}
            onChange={(e) => ubah({ fineEnabled: e.target.checked })}
          />
          <span>Hitung denda keterlambatan</span>
        </label>

        {draft.fineEnabled && (
          <>
            <p className="small muted">
              Denda dihitung saat ditampilkan, bukan disimpan di catatan absen —
              mengubah tarif langsung berlaku serempak di seluruh rekap dan
              ekspor, termasuk bulan-bulan sebelumnya.
            </p>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Telat sampai (menit)</th>
                    <th>Denda</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {draft.fineTiers.map((t, i) => (
                    <tr key={i}>
                      <td>
                        {t.upToMinutes === null ? (
                          <span className="muted">lebih dari tingkat sebelumnya</span>
                        ) : (
                          <input
                            inputMode="numeric"
                            value={t.upToMinutes}
                            onChange={(e) =>
                              ubahTier(i, {
                                upToMinutes: Number(e.target.value.replace(/\D/g, "") || 0),
                              })
                            }
                          />
                        )}
                      </td>
                      <td>
                        <input
                          inputMode="numeric"
                          value={t.amount}
                          onChange={(e) =>
                            ubahTier(i, {
                              amount: Number(e.target.value.replace(/\D/g, "") || 0),
                            })
                          }
                        />
                        <span className="small muted"> {rupiah(t.amount)}</span>
                      </td>
                      <td>
                        <button
                          className="small danger"
                          onClick={() =>
                            ubah({ fineTiers: draft.fineTiers.filter((_, j) => j !== i) })
                          }
                        >
                          Hapus
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="row" style={{ marginTop: 10 }}>
              <button
                onClick={() =>
                  ubah({ fineTiers: [...draft.fineTiers, { upToMinutes: 60, amount: 0 }] })
                }
              >
                Tambah tingkat
              </button>
              {!draft.fineTiers.some((t) => t.upToMinutes === null) && (
                <button
                  onClick={() =>
                    ubah({ fineTiers: [...draft.fineTiers, { upToMinutes: null, amount: 0 }] })
                  }
                >
                  Tambah tingkat tanpa batas
                </button>
              )}
            </div>
          </>
        )}
      </div>

      <div className="card">
        <h2>Lokasi absen</h2>
        <label className="field">
          <span>Mode</span>
          <select
            value={draft.geoMode}
            onChange={(e) => ubah({ geoMode: e.target.value as GeoMode })}
          >
            <option value="off">Nonaktif — koordinat tetap disimpan</option>
            <option value="warn">Peringatan saja — absen tetap tercatat, ditandai</option>
            <option value="strict">Wajib di area — ditolak, kecuali diloloskan penyelia</option>
          </select>
        </label>

        <div className="grid cols-3">
          <label className="field">
            <span>Lintang</span>
            <input
              value={draft.geoLat ?? ""}
              placeholder="-6.200000"
              onChange={(e) =>
                ubah({ geoLat: e.target.value === "" ? null : Number(e.target.value) })
              }
            />
          </label>
          <label className="field">
            <span>Bujur</span>
            <input
              value={draft.geoLon ?? ""}
              placeholder="106.816666"
              onChange={(e) =>
                ubah({ geoLon: e.target.value === "" ? null : Number(e.target.value) })
              }
            />
          </label>
          <label className="field">
            <span>Radius (meter)</span>
            <input
              inputMode="numeric"
              value={draft.geoRadiusMeters}
              onChange={(e) =>
                ubah({ geoRadiusMeters: Number(e.target.value.replace(/\D/g, "") || 0) })
              }
            />
          </label>
        </div>

        <button onClick={pakaiLokasiSekarang}>Pakai lokasi PC ini</button>

        <p className="small muted" style={{ marginTop: 10 }}>
          GPS di dalam ruangan biasanya meleset 20–50 meter. Radius di bawah 50
          meter cenderung menolak karyawan yang sebenarnya sudah berada di cafe.
          Titik yang paling tepat diambil dari tablet yang berdiri di kasir, bukan
          dari PC di ruang belakang.
        </p>
      </div>

      <div className="card">
        <h2>Tablet kios</h2>
        {devices.length === 0 ? (
          <p className="muted">Belum ada tablet yang pernah tersambung.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Nama</th>
                  <th>Versi</th>
                  <th>Terakhir terlihat</th>
                </tr>
              </thead>
              <tbody>
                {devices.map((d) => (
                  <tr key={d.id}>
                    <td>{d.label}</td>
                    <td>{d.appVersion}</td>
                    <td>{d.lastSeen ? d.lastSeen.toLocaleString("id-ID") : "—"}</td>
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
