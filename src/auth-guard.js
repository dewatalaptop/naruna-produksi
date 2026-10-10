// Every page except index.html (the login screen) imports this at the top.
//
// Naruna's Firestore data is gated by firestore.rules' isNarunaStaff() — the
// single source of truth for WHO may touch it. That gate was tightened on
// 2026-09-21 (this Firebase project is SHARED with every other app built here,
// so "any signed-in Google account" used to mean every unrelated customer
// could read Naruna's live production data). A signed-in Google account is
// therefore no longer automatically authorized: only the owner, a provisioned
// station tablet, or a Google account listed in narunaStaff/{email} is.
//
// This guard must mirror that boundary. Previously it resolved for ANY
// signed-in account and let the page fire its Firestore reads, so an
// unauthorized account (an unrelated customer who stumbled onto the URL, or a
// real staffer not yet added to the allow-list) crashed every page with an
// uncaught "Missing or insufficient permissions." — reported 13× as bug
// naruna-produksi_3c3627b8a1cc. We now probe one gated read here and, on a real
// permission denial, show a clear "belum terdaftar" screen instead of crashing.
// The rules stay the sole authority — this grants no one access, it only turns
// an uncaught crash into a handled, legible outcome.
import {
  auth,
  onAuthStateChanged,
  signOut,
  db,
  collection,
  query,
  limit,
  getDocs,
} from "./firebase.js";

export function requireAuth() {
  return new Promise((resolve) => {
    onAuthStateChanged(auth, async (user) => {
      if (!user) {
        window.location.href = "/index.html";
        return;
      }
      // Probe a single core collection every page reads anyway. A real
      // authorization failure reliably carries code "permission-denied"; a
      // transient network/offline failure carries "unavailable" and must NOT
      // lock out a legitimate staffer, so only "permission-denied" blocks.
      try {
        await getDocs(query(collection(db, "items"), limit(1)));
      } catch (err) {
        if (err && err.code === "permission-denied") {
          showUnauthorized(user);
          return; // never resolve — the page's own data loads never run
        }
        // Any other error: let the page proceed and surface its own error
        // handling rather than silently blocking on a transient glitch.
      }
      resolve(user);
    });
  });
}

function showUnauthorized(user) {
  const email = user && user.email ? user.email : "akun ini";
  document.body.innerHTML = `
    <div style="min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#f7f5f2;">
      <div style="max-width:440px;width:100%;background:#fff;border:1px solid #e7e2da;border-radius:16px;padding:32px;text-align:center;box-shadow:0 1px 3px rgba(0,0,0,.06);">
        <div style="font-size:40px;line-height:1;margin-bottom:12px;">&#128274;</div>
        <h1 style="font-size:20px;margin:0 0 10px;color:#2b2a27;">Akun belum terdaftar</h1>
        <p style="margin:0 0 6px;color:#5c574f;font-size:14px;line-height:1.55;">
          <strong>${escapeText(email)}</strong> belum punya akses ke aplikasi Naruna Produksi.
        </p>
        <p style="margin:0 0 22px;color:#5c574f;font-size:14px;line-height:1.55;">
          Jika Anda memang staf Naruna, hubungi admin agar akun Anda didaftarkan. Kalau salah masuk, silakan keluar dan gunakan akun yang benar.
        </p>
        <button id="ag-signout" style="appearance:none;border:none;border-radius:10px;padding:12px 20px;font-size:14px;font-weight:600;cursor:pointer;background:#9a3b2e;color:#fff;">
          Keluar &amp; ganti akun
        </button>
      </div>
    </div>
  `;
  document.getElementById("ag-signout")?.addEventListener("click", async () => {
    try {
      await signOut(auth);
    } finally {
      window.location.href = "/index.html";
    }
  });
}

function escapeText(s) {
  return String(s).replace(/[&<>"']/g, (c) => (
    { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]
  ));
}
