// Every user-facing string, in English and Spanish (CLAUDE.md). The browser's language picks one.

const en = {
  title: "Your training, on a bigger screen.",
  intro: "* Sign in with the Google account you use in the DinoGym app. You will see your workouts, progress and body weight. Logging still happens on your phone.",
  privacy: "DinoGym gets your name and email from Google to show them on this page. The server stores neither: only a random id.",
  loading: "Loading your workouts…",
  loaded: (workouts: number, weighIns: number) => `${workouts} workouts and ${weighIns} weigh-ins loaded. The full dashboard comes next.`,
  signInFailed: "Could not sign in. Try again in a moment.",
  loadFailed: "Could not load your data. Check your connection and try again.",
  sessionEnded: "You were signed out. Sign in again to see your data.",
  signOut: "SIGN OUT",
  tryAgain: "TRY AGAIN",
};

const es: typeof en = {
  title: "Tu entrenamiento, en una pantalla más grande.",
  intro: "* Inicia sesión con la cuenta de Google que usas en la app DinoGym. Verás tus entrenos, tu progreso y tu peso. Se siguen registrando en el teléfono.",
  privacy: "DinoGym recibe tu nombre y correo de Google para mostrarlos en esta página. El servidor no guarda ninguno: solo un id aleatorio.",
  loading: "Cargando tus entrenos…",
  loaded: (workouts, weighIns) => `${workouts} entrenos y ${weighIns} pesajes cargados. El panel completo viene después.`,
  signInFailed: "No se pudo iniciar sesión. Inténtalo de nuevo en un momento.",
  loadFailed: "No se pudieron cargar tus datos. Revisa tu conexión e inténtalo de nuevo.",
  sessionEnded: "Se cerró tu sesión. Vuelve a iniciarla para ver tus datos.",
  signOut: "CERRAR SESIÓN",
  tryAgain: "REINTENTAR",
};

export type Strings = typeof en;
export const language: "en" | "es" = navigator.language.toLowerCase().startsWith("es") ? "es" : "en";
export const strings: Strings = language === "es" ? es : en;
