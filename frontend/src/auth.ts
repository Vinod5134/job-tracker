const KEY = "jobtracker.token";

// localStorage can throw (private mode, blocked storage), so every access is guarded
export function getToken(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string): void {
  try {
    localStorage.setItem(KEY, token);
  } catch {
    // Without storage the user stays signed in only until the page reloads
    memoryToken = token;
  }
}

export function clearToken(): void {
  memoryToken = null;
  try {
    localStorage.removeItem(KEY);
  } catch {
    // ignore
  }
}

let memoryToken: string | null = null;

/** True when there's a token that hasn't expired yet (checks the JWT "exp" claim). */
export function isLoggedIn(): boolean {
  const token = getToken() ?? memoryToken;
  if (!token) {
    return false;
  }
  try {
    const payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
    return typeof payload.exp === "number" && payload.exp * 1000 > Date.now();
  } catch {
    return false;
  }
}

export function currentToken(): string | null {
  return getToken() ?? memoryToken;
}
