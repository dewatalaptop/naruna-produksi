import { auth, signOut } from "./firebase.js";

const PAGES = [
  { href: "/dashboard.html", label: "Dashboard" },
  { href: "/po.html", label: "Purchase Order" },
  { href: "/master-data.html", label: "Master Data" },
  { href: "/stok.html", label: "Stok Biskuit" },
  { href: "/surat-jalan.html", label: "Surat Jalan" },
  { href: "/laporan.html", label: "Laporan Kapasitas" },
  { href: "/pesanan-tertahan.html", label: "Pesanan Tertahan" },
  { href: "/panduan.html", label: "Panduan Penggunaan" },
  { href: "/unduh-aplikasi.html", label: "Aplikasi Android" },
];

export function renderNav(activeHref) {
  const current = window.location.pathname.endsWith(activeHref);
  const linksHtml = PAGES.map(
    (p) =>
      `<a href="${p.href}" class="${window.location.pathname.endsWith(p.href) ? "active" : ""}">${p.label}</a>`
  ).join("");

  document.body.insertAdjacentHTML(
    "afterbegin",
    `
    <div class="mobile-topbar">
      <span class="brand">Naruna Produksi</span>
      <button id="mobile-menu-btn">Menu</button>
    </div>
  `
  );

  const shell = document.querySelector(".app-shell");
  if (shell) {
    shell.insertAdjacentHTML(
      "afterbegin",
      `
      <nav class="sidebar" id="sidebar">
        <div class="brand">Naruna Produksi<span>Manajemen Produksi</span></div>
        ${linksHtml}
        <button class="signout" id="signout-btn">Keluar</button>
      </nav>
    `
    );
  }

  document.getElementById("signout-btn")?.addEventListener("click", async () => {
    await signOut(auth);
    window.location.href = "/index.html";
  });

  document.getElementById("mobile-menu-btn")?.addEventListener("click", () => {
    const sidebar = document.getElementById("sidebar");
    if (sidebar) sidebar.style.display = sidebar.style.display === "flex" ? "none" : "flex";
  });
}
