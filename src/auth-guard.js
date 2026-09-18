// Every page except index.html (the login screen) imports this at the top.
// Any signed-in Google account may use the app — Naruna is a small,
// single-tenant client studio with only two real users (admin, kepala
// gudang) sharing this Web App; the spec never asked for per-role
// restriction beyond that, so a plain "signed in" gate is the right size,
// not an under-build. (The Android app, out of scope this pass, is where
// per-station login lives instead — a different, floor-operator concern.)
import { auth, onAuthStateChanged } from "./firebase.js";

export function requireAuth() {
  return new Promise((resolve) => {
    onAuthStateChanged(auth, (user) => {
      if (!user) {
        window.location.href = "/index.html";
        return;
      }
      resolve(user);
    });
  });
}
