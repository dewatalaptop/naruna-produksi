// Thin collection helpers over the shared ai-app-builder-7bf8e Firestore —
// see spec section 4 for the schema these mirror. Every collection here is
// scoped to this app only via naming (items/materials/colors/
// purchase_orders/work_groups/scan_events/stasiun_kerja/stok_biskuit/
// surat_jalan/notifikasi_log), gated by firestore.rules requiring sign-in.
import { db, collection, doc, getDoc, getDocs, addDoc, setDoc, updateDoc, deleteDoc, query, where, orderBy, onSnapshot, serverTimestamp } from "./firebase.js";

export const itemsCol = collection(db, "items");
export const materialsCol = collection(db, "materials");
export const colorsCol = collection(db, "colors");
export const purchaseOrdersCol = collection(db, "purchase_orders");
export const workGroupsCol = collection(db, "work_groups");
export const scanEventsCol = collection(db, "scan_events");
export const stasiunKerjaCol = collection(db, "stasiun_kerja");
export const stokBiskuitCol = collection(db, "stok_biskuit");
export const suratJalanCol = collection(db, "surat_jalan");
export const notifikasiLogCol = collection(db, "notifikasi_log");

export function watchCollection(col, cb, orderField) {
  const q = orderField ? query(col, orderBy(orderField)) : col;
  return onSnapshot(q, (snap) => cb(snap.docs.map((d) => ({ id: d.id, ...d.data() }))));
}

export async function getAllOnce(col) {
  const snap = await getDocs(col);
  return snap.docs.map((d) => ({ id: d.id, ...d.data() }));
}

export async function getById(col, id) {
  const snap = await getDoc(doc(col, id));
  return snap.exists() ? { id: snap.id, ...snap.data() } : null;
}

export function addDocTimestamped(col, data) {
  return addDoc(col, { ...data, createdAt: serverTimestamp(), updatedAt: serverTimestamp() });
}

export function updateDocTimestamped(col, id, data) {
  return updateDoc(doc(col, id), { ...data, updatedAt: serverTimestamp() });
}

export function removeDoc(col, id) {
  return deleteDoc(doc(col, id));
}

// Lookup maps keyed by id, built once and reused across a page render pass
// — every list/table needs to resolve item_id/material_id/color_id to
// human-readable names, and re-querying per row would be wasteful.
export function toLookupMap(rows) {
  return Object.fromEntries(rows.map((r) => [r.id, r]));
}
