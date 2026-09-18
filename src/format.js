export function formatDate(d) {
  if (!d) return "-";
  const date = d.toDate ? d.toDate() : d instanceof Date ? d : new Date(d);
  return date.toLocaleDateString("id-ID", { day: "numeric", month: "short", year: "numeric" });
}

export function formatDateTime(d) {
  if (!d) return "-";
  const date = d.toDate ? d.toDate() : d instanceof Date ? d : new Date(d);
  return date.toLocaleString("id-ID", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

export function daysBetween(a, b) {
  const da = a.toDate ? a.toDate() : new Date(a);
  const db_ = b.toDate ? b.toDate() : new Date(b);
  return Math.round((db_.getTime() - da.getTime()) / (1000 * 60 * 60 * 24));
}

export function escapeHtml(s) {
  return String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}
