// New projects built via this AI App Builder default to the SAME shared
// Firebase project (ai-app-builder-7bf8e) rather than provisioning a fresh
// one per project. The apiKey below is Firebase's public client key
// (identifies the project, not a secret — access control happens via
// security rules, never by hiding this key).
//
// CRITICAL — read before touching authDomain or signInWithRedirect:
// authDomain MUST stay "ai-app-builder-7bf8e.firebaseapp.com" (the SHARED
// project's own domain), never this app's own live domain — and web
// Google Sign-In MUST use signInWithPopup, never signInWithRedirect
// (redirect silently drops the session via storage partitioning; pointing
// authDomain at this app's own domain instead breaks louder with
// redirect_uri_mismatch). Confirmed pattern, see kasir-rakyat/src/firebase.ts.
import { initializeApp } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signOut,
  onAuthStateChanged,
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";
import {
  getFirestore,
  collection,
  doc,
  getDoc,
  getDocs,
  addDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  query,
  where,
  orderBy,
  limit,
  onSnapshot,
  serverTimestamp,
  Timestamp,
  writeBatch,
  runTransaction,
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js";
import {
  getFunctions,
  httpsCallable,
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-functions.js";

const firebaseApp = initializeApp({
  apiKey: "AIzaSyCiBe6t2_26DflR3W7TbDf4HwDI5Hh0TI4",
  authDomain: "ai-app-builder-7bf8e.firebaseapp.com",
  projectId: "ai-app-builder-7bf8e",
});

export const auth = getAuth(firebaseApp);
export const googleProvider = new GoogleAuthProvider();
export const db = getFirestore(firebaseApp);
export const functions = getFunctions(firebaseApp);

export {
  signInWithPopup,
  signOut,
  onAuthStateChanged,
  collection,
  doc,
  getDoc,
  getDocs,
  addDoc,
  setDoc,
  updateDoc,
  deleteDoc,
  query,
  where,
  orderBy,
  limit,
  onSnapshot,
  serverTimestamp,
  Timestamp,
  writeBatch,
  runTransaction,
  httpsCallable,
};
