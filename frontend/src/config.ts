/** Backend base URL, set with VITE_API_URL at build time (see .env.example). */
export const API_URL: string = (import.meta.env.VITE_API_URL || "http://localhost:8080").replace(/\/+$/, "");

export const LOGIN_URL = `${API_URL}/oauth2/authorization/google`;
export const GMAIL_CONNECT_URL = `${API_URL}/oauth2/authorization/google-gmail`;
