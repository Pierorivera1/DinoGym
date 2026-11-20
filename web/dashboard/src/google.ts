// Google Identity Services (loaded by index.html). Only what the dashboard uses.

/** The Web OAuth client (decision 80). Not secret: it is in every ID token. */
export const GOOGLE_CLIENT_ID = "854486518090-7sc6gs4dmbuo5lgsm9ss9g0p0chedm9a.apps.googleusercontent.com";

interface CredentialResponse {
  credential: string;
}

interface GoogleId {
  initialize(config: { client_id: string; callback: (r: CredentialResponse) => void; auto_select?: boolean }): void;
  renderButton(parent: HTMLElement, options: { theme?: string; size?: string; text?: string; locale?: string }): void;
  prompt(): void;
  disableAutoSelect(): void;
}

declare global {
  interface Window {
    google?: { accounts: { id: GoogleId } };
  }
}

/** Waits for the GIS script, which index.html loads asynchronously. */
export function whenGoogleReady(): Promise<GoogleId> {
  return new Promise((resolve) => {
    const check = () => (window.google ? resolve(window.google.accounts.id) : setTimeout(check, 50));
    check();
  });
}
